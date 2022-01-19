package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SimpleTimeSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, TimePicker.OnTimeChangedListener {

    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var mustReconnect = false
    private var currentHour = 0
    private var currentMinute = 0
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false



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
        val timeSelectorState = TimeSelectorState()
        timeSelectorState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        // set the displayed time to the current device setting
        this.currentHour = timeSelectorState.hour
        this.currentMinute = timeSelectorState.minute

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // config timeSelector
        this.simpleTimePicker = findViewById(R.id.simpleTimePicker)
        this.simpleTimePicker.setIs24HourView(true)
        this.simpleTimePicker.hour = timeSelectorState.hour
        this.simpleTimePicker.minute = timeSelectorState.minute
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
            this.expectedConnectionLoss = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, do a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementID
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

        // reset parameter anyway
        this.expectedConnectionLoss = false

        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:STSPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        } else {
            // notify the remote device of the invocation of this property-page
            ApplicationProperty.bluetoothConnectionManager.notifyComplexPropertyPageInvoked(this.relatedElementID)
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
        this.collectDataSendCommandAndUpdateState()
    }

    private fun setCurrentViewStateFromComplexPropertyState(timeSelectorState: TimeSelectorState){
        // NOTE: do not use this method before the view is retrieved in onCreate

        // TODO: check if this works (send property-changed notification from device)

        this.currentHour = timeSelectorState.hour
        this.currentMinute = timeSelectorState.minute

        this.simpleTimePicker.hour = timeSelectorState.hour
        this.simpleTimePicker.minute = timeSelectorState.minute
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun collectDataSendCommandAndUpdateState(){
        val timeSelectorState = TimeSelectorState()
        timeSelectorState.hour = this.currentHour
        timeSelectorState.minute = this.currentMinute

        ApplicationProperty.bluetoothConnectionManager.sendData(
            timeSelectorState.toExecutionString(this.relatedElementID)
        )

        ApplicationProperty.bluetoothConnectionManager.updatePropertyStateDataNoEvent(
            timeSelectorState.toComplexPropertyState(),
            this.relatedElementID
        )
    }

    private fun connectionLossAlertDialog(){
        runOnUiThread {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage(R.string.GeneralString_UnexpectedConnectionLossMessage)
            dialog.setTitle(R.string.GeneralString_ConnectionLossDialogTitle)
            dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
                // try to reconnect
                this.propertyStateUpdateRequired = true
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                dialogInterface.dismiss()
            }
            dialog.setNegativeButton(R.string.GeneralString_Cancel) { dialogInterface: DialogInterface, _: Int ->
                // cancel action
                Executors.newSingleThreadScheduledExecutor().schedule({
                    // NOTE: do not call clear() on the bleManager, this corrupts the list on the device main page!
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                    finish()
                    if(!isStandAlonePropertyMode) {
                        // only set slide transition if the activity was invoked from the deviceMainActivity
                        overridePendingTransition(
                            R.anim.finish_activity_slide_animation_in,
                            R.anim.finish_activity_slide_animation_out
                        )
                    }
                }, 300, TimeUnit.MILLISECONDS)
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()
        }
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

            if(this.propertyStateUpdateRequired){
                this.propertyStateUpdateRequired = false
                Executors.newSingleThreadScheduledExecutor().schedule({
                    ApplicationProperty.bluetoothConnectionManager.updatePropertyStates()
                }, TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT, TimeUnit.MILLISECONDS)
            }
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)

            if(!expectedConnectionLoss){
                // unexpected loss of connection
                if(verboseLog){
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in SimpleTimeSelectorActivity.")
                }
                (applicationContext as ApplicationProperty).logControl("W: Unexpected loss of connection. Remote device not reachable.")
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
                this.connectionLossAlertDialog()
            }
        }
    }

    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
        }
    }

    override fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData) {
        super.onRemoteUserMessage(deviceHeaderData)
        notifyUser(deviceHeaderData.message, R.color.InfoColor)
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        return this.relatedElementID
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

        if(element.internalElementIndex == this.relatedElementID){
            if(verboseLog) {
                Log.d(
                    "M:CB:STSPage:ComplexPCg",
                    "Simple Time Selector Activity - Complex Property changed - Update the UI"
                )
            }
            val timeSelectorState = TimeSelectorState()
            timeSelectorState.fromComplexPropertyState(newState)
            this.setCurrentViewStateFromComplexPropertyState(timeSelectorState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }

    override fun onPropertyInvalidated() {
        if(!isStandAlonePropertyMode) {
            (this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage = true
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true

            finish()

            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }
}
