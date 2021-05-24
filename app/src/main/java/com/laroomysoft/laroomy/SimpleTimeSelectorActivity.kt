package com.laroomysoft.laroomy

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView

class SimpleTimeSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, TimePicker.OnTimeChangedListener {

    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var mustReconnect = false
    private var currentHour = 0
    private var currentMinute = 0
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE


    private lateinit var simpleTimePicker: TimePicker
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var notificationTextView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_time_selector)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.stsHeaderTextView).apply {
            text = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                relatedGlobalElementIndex
            ).elementText
        }
        this.notificationTextView = findViewById(R.id.stsUserNotificationTextView)

        // get the complex state data for the time selector
        val timeSelectorState =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // set the displayed time to the current device setting
        this.currentHour = timeSelectorState.valueOne
        this.currentMinute = timeSelectorState.valueTwo


        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@SimpleTimeSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // config timeSelector
        this.simpleTimePicker = findViewById(R.id.simpleTimePicker)
        this.simpleTimePicker.setIs24HourView(true)
        this.simpleTimePicker.hour = timeSelectorState.valueOne
        this.simpleTimePicker.minute = timeSelectorState.valueTwo
        this.simpleTimePicker.setOnTimeChangedListener(this)
    }

    override fun onPause() {
        super.onPause()
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "M:STSPage:onPause",
                    "Simple Time Selector Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, do a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateID = this.relatedElementID
        // close activity
        finish()
        if(!isStandAlonePropertyMode) {
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if(verboseLog) {
            Log.d("M:STSPage:onResume", "onResume executed in Simple Time Selector Control")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@SimpleTimeSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:STSPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        }
    }

    override fun onTimeChanged(view: TimePicker?, hourOfDay: Int, minute: Int) {
        if(verboseLog) {
            Log.d(
                "M:CB:TimeChanged", "Time changed in SimpleTimeSelector Activity." +
                        "Selected Time is: $hourOfDay : $minute"
            )
        }

        this.currentHour = hourOfDay
        this.currentMinute = minute

        ApplicationProperty.bluetoothConnectionManager.sendData(
            this.generateExecutionString()
        )
    }

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState){
        // NOTE: do not use this method before the view is retrieved in onCreate

        // TODO: check if this works (send property-changed notification from device)

        this.currentHour = complexPropertyState.valueOne
        this.currentMinute = complexPropertyState.valueTwo

        this.simpleTimePicker.hour = complexPropertyState.valueOne
        this.simpleTimePicker.minute = complexPropertyState.valueTwo
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun generateExecutionString() : String {
        return "C${a8BitValueToString(relatedElementID)}" +
                "${ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedGlobalElementIndex
                ).complexPropertyState.timeSetterIndex}" +
                a8BitValueAsTwoCharString(currentHour) +
                a8BitValueAsTwoCharString(currentMinute) +
                "00$"
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "M:STSPage:ConStateChge",
                "Connection state changed in SimpleTimeSelector Activity. New Connection state is: $state"
            )
        }
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)

        Log.e("M:STSPage:onConnFailed", "Connection Attempt failed in Simple Time Selector Activity")
        (applicationContext as ApplicationProperty).logControl("E: Connection failed in SimpleTimeSelector")

        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
    }

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
        notifyUser(deviceHeaderData.message, R.color.InfoColor)
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.elementID == this.relatedElementID){
            if(verboseLog) {
                Log.d(
                    "M:CB:STSPage:ComplexPCg",
                    "Simple Time Selector Activity - Complex Property changed - Update the UI"
                )
            }
            this.setCurrentViewStateFromComplexPropertyState(element.complexPropertyState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }
}
