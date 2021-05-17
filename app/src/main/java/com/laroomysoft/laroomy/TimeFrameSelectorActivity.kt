package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.widget.AppCompatTextView

class TimeFrameSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, TimePicker.OnTimeChangedListener {

    var mustReconnect = false
    var relatedElementID = -1
    var relatedGlobalElementIndex = -1

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var toTimePicker: TimePicker
    private lateinit var fromTimePicker: TimePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_frame_selector)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.tfsHeaderTextView).apply {
            text = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                relatedGlobalElementIndex
            ).elementText
        }

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@TimeFrameSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the related complex-state object
        val timeFrameState =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // save reference to UI elements
        this.notificationTextView = findViewById(R.id.tfsNotificationTextView)
        this.toTimePicker = findViewById(R.id.toTimePicker)
        this.fromTimePicker = findViewById(R.id.fromTimePicker)

        // set the 24h format in the timePicker
        this.toTimePicker.setIs24HourView(true)
        this.fromTimePicker.setIs24HourView(true)

        // set the initial time of the pickers from the complex-state-data
        this.fromTimePicker.hour = timeFrameState.valueOne
        this.fromTimePicker.minute = timeFrameState.valueTwo
        this.toTimePicker.hour = timeFrameState.valueThree
        this.toTimePicker.minute = timeFrameState.commandValue

        // set the time-changed listeners
        this.fromTimePicker.setOnTimeChangedListener(this)
        this.toTimePicker.setOnTimeChangedListener(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateID = this.relatedElementID
        // close activity
        finish()
    }

    override fun onPause() {
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "M:TFSPage:onPause",
                    "Time-Frame Selector Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.close()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:TFSPage:onResume", "onResume executed in Time-Frame Selector Activity")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@TimeFrameSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:TFSPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    override fun onTimeChanged(view: TimePicker?, hourOfDay: Int, minute: Int) {
        if(verboseLog) {
            Log.d(
                "M:CB:TimeChanged", "Time changed in TimeFrameSelector Activity." +
                        "\nTime-Type: ${if (view?.id == R.id.fromTimePicker) "On-Time" else "Off-Time"}" +
                        "New Time is: $hourOfDay : $minute"
            )
        }
        ApplicationProperty.bluetoothConnectionManager.sendData(
            this.generateExecutionString(view?.id ?: 0, hourOfDay, minute)
        )
    }

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState){
        this.fromTimePicker.hour = complexPropertyState.valueOne
        this.fromTimePicker.minute = complexPropertyState.valueTwo
        this.toTimePicker.hour = complexPropertyState.valueThree
        this.toTimePicker.minute = complexPropertyState.commandValue
    }

    private fun generateExecutionString(ID: Int, hour: Int, minute: Int): String {
        if ((ID == R.id.fromTimePicker) || (ID == R.id.toTimePicker)) {
            val c =
                if (ID == R.id.fromTimePicker) '1' else '2' // time-setter-index 1 or 2 to identify the on or off time
            val reserved =
                "00"                             // reserved at the time, maybe use it later

            return "C${a8BitValueToString(this.relatedElementID)}$c${a8BitValueAsTwoCharString(hour)}${a8BitValueAsTwoCharString(
                minute
            )}$reserved$"
        }
        return "error$"
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "M:TFSPage:ConStateChge",
                "Connection state changed in Time-Frame Selector Activity. New Connection state is: $state"
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

        Log.e("M:TFSPage:onConnFailed", "Connection Attempt failed in Time-Frame Selector Activity")
        (applicationContext as ApplicationProperty).logControl("E: Connection failed in TimeFrameSelector")

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
                    "M:CB:TFSPage:ComplexPCg",
                    "Time-Frame Selector Activity - Complex Property changed - Update the UI"
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