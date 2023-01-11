package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TimePicker
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TimeFrameSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, TimePicker.OnTimeChangedListener {

    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false
    
    private var updateDelayStarted = false

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var toTimePicker: TimePicker
    private lateinit var fromTimePicker: TimePicker
    private lateinit var backButton: AppCompatImageButton
    private lateinit var deviceSettingsButton: AppCompatImageButton
    
    private val successiveComplexUpdateStorage = SuccessiveComplexUpdateStorage()
    private lateinit var setupTimer: Timer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_frame_selector)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // register back event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // get the element ID + UI-Adapter Index
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedUIAdapterIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.tfsBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // add device settings button functionality
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.tfsHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to the device settings activity..
                    val intent = Intent(this@TimeFrameSelectorActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.tfsHeaderTextView).apply {
            text = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                relatedUIAdapterIndex
            ).elementText
        }

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the related complex-state object
        val timeFrameState = TimeFrameSelectorState()
        timeFrameState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState
        )

        // save reference to UI elements
        this.notificationTextView = findViewById(R.id.tfsNotificationTextView)
        this.toTimePicker = findViewById(R.id.toTimePicker)
        this.fromTimePicker = findViewById(R.id.fromTimePicker)

        // set the 24h format in the timePicker
        this.toTimePicker.setIs24HourView(true)
        this.fromTimePicker.setIs24HourView(true)

        // set the initial time of the pickers from the complex-state-data
        this.fromTimePicker.hour = timeFrameState.onTimeHour
        this.fromTimePicker.minute = timeFrameState.onTimeMinute
        this.toTimePicker.hour = timeFrameState.offTimeHour
        this.toTimePicker.minute = timeFrameState.offTimeMinute

        // set the time-changed listeners
        this.fromTimePicker.setOnTimeChangedListener(this)
        this.toTimePicker.setOnTimeChangedListener(this)
    }
    
    private fun handleBackEvent(){
        // check the mode and act in relation to it
        if(!isStandAlonePropertyMode) {
            // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
            (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementIndex
            // close activity
            finish()
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        } else {
            // this is stand-alone mode, so when back navigation occurs, the connection must be cleared
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if(!isStandAlonePropertyMode) {
            // NOT stand-alone mode:
            // if the following is true, onBackPressed was executed before and the connection must remain active
            // because this is a back navigation to the device main activity
            if (!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {
                if (verboseLog) {
                    Log.d(
                        "TimeFrameSelector:onPause",
                        "Time-Frame Selector Activity: The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        } else {
            // this is stand-alone property mode
            if(this.preventOnPauseExecutionInStandAloneMode){
                this.preventOnPauseExecutionInStandAloneMode = false
            } else {
                // normal onPause execution:
                if (verboseLog) {
                    Log.d(
                        "TimeFrameSelector:onPause",
                        "Time-Frame Selector Activity: The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                this.expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:TFSPage:onResume", "onResume executed in Time-Frame Selector Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false
    
        // recover button if applicable
        if (isStandAlonePropertyMode && buttonNormalizationRequired) {
            buttonNormalizationRequired = false
            this.deviceSettingsButton.setImageResource(R.drawable.ic_settings_48dp)
        }
    
        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
            }
            else -> {
                ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
                ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                // reconnect to the device if necessary (if the user has left the application)
                if (this.mustReconnect) {
                    if (verboseLog) {
                        Log.d(
                            "M:TFSPage:onResume",
                            "The connection was suspended -> try to reconnect"
                        )
                    }
                    ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                    this.mustReconnect = false
                } else {
                    // notify the remote device of the invocation of this property-page
                    ApplicationProperty.bluetoothConnectionManager.notifyComplexPropertyPageInvoked(
                        this.relatedElementIndex
                    )
                }
            }
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
        
        // update with a little delay to avoid too much transmissions
        if(!updateDelayStarted){
            updateDelayStarted = true
            Executors.newSingleThreadScheduledExecutor().schedule({
                updateDelayStarted = false
                collectDataSendCommandAndUpdateState()
            }, 800, TimeUnit.MILLISECONDS)
        }
    }

    private fun collectDataSendCommandAndUpdateState(){
        val timeFrameSelectorState = TimeFrameSelectorState()
        timeFrameSelectorState.onTimeHour = this.fromTimePicker.hour
        timeFrameSelectorState.onTimeMinute = this.fromTimePicker.minute
        timeFrameSelectorState.offTimeHour = this.toTimePicker.hour
        timeFrameSelectorState.offTimeMinute = this.toTimePicker.minute
        
//          old update - no transmission control!
//        ApplicationProperty.bluetoothConnectionManager.sendData(
//            timeFrameSelectorState.toExecutionString(this.relatedElementID)
//        )

        ApplicationProperty.bluetoothConnectionManager.updatePropertyStateDataNoEvent(
            timeFrameSelectorState.toComplexPropertyState(),
            this.relatedElementIndex
        )
    
        // control the output transmissions in a sensible manner
        if(!this.successiveComplexUpdateStorage.isStarted){
            // set initial update data
            this.successiveComplexUpdateStorage.keep = true
            this.successiveComplexUpdateStorage.data = timeFrameSelectorState.toExecutionString(this.relatedElementIndex)
            this.successiveComplexUpdateStorage.isStarted = true
        
            // start timer
            this.setupTimer = Timer()
            this.setupTimer.scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        if(successiveComplexUpdateStorage.keep){
                            successiveComplexUpdateStorage.keep = false
                        } else {
                            // this is the second run while no action was executed by the user, send update transmission (delay = 120*2 = 240 millis)
                            ApplicationProperty.bluetoothConnectionManager.sendData(
                                successiveComplexUpdateStorage.data
                            )
                            // stop timer
                            this.cancel()
                            successiveComplexUpdateStorage.isStarted = false
                        }
                    }
                }, (120).toLong(), (120).toLong())
        } else {
            // timer is running, set keep to true to mark the time-setup-process as pending and save the data, not more!
            this.successiveComplexUpdateStorage.keep = true
            this.successiveComplexUpdateStorage.data = timeFrameSelectorState.toExecutionString(this.relatedElementIndex)
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(timeFrameSelectorState: TimeFrameSelectorState){
        runOnUiThread {
            this.fromTimePicker.hour = timeFrameSelectorState.onTimeHour
            this.fromTimePicker.minute = timeFrameSelectorState.onTimeMinute
            this.toTimePicker.hour = timeFrameSelectorState.offTimeHour
            this.toTimePicker.minute = timeFrameSelectorState.offTimeMinute
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
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
                    if(!isStandAlonePropertyMode) {
                        // do not call clear() on the bleManager in normal mode, this corrupts the list on the device main page!
                        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                            true
                    } else {
                        // stand-alone-mode: here 'clear()' must be called - finish goes back to main activity directly
                        ApplicationProperty.bluetoothConnectionManager.clear()
                    }
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
                "M:TFSPage:ConStateChge",
                "Connection state changed in Time-Frame Selector Activity. New Connection state is: $state"
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
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in TimeFrameSelectorActivity.")
                }
                (applicationContext as ApplicationProperty).logControl("W: Unexpected loss of connection. Remote device not reachable.")
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
                this.connectionLossAlertDialog()
            }
        }
    }
    
    override fun onConnectionEvent(eventID: Int) {
        if (eventID == BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED) {
            notifyUser(
                getString(R.string.GeneralMessage_resumingConnection),
                R.color.connectingTextColor
            )
        }
    }
    
    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
        return this.relatedElementIndex
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementIndex){
            if(verboseLog) {
                Log.d(
                    "M:CB:TFSPage:ComplexPCg",
                    "Time-Frame Selector Activity - Complex Property changed - Update the UI"
                )
            }
            val timeFrameSelectorState = TimeFrameSelectorState()
            timeFrameSelectorState.fromComplexPropertyState(newState)
            this.setCurrentViewStateFromComplexPropertyState(timeFrameSelectorState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList[UIAdapterElementIndex].hasChanged = true
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
        // else: do nothing: property reload is not supported in stand-alone mode
    }
    
    override fun onRemoteBackNavigationRequested() {
        if (!isStandAlonePropertyMode) {
            Executors.newSingleThreadScheduledExecutor().schedule({
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                
                finish()
                
                overridePendingTransition(
                    R.anim.finish_activity_slide_animation_in,
                    R.anim.finish_activity_slide_animation_out
                )
            }, 500, TimeUnit.MILLISECONDS)
        }
        // else: do nothing: back navigation to device main is not possible in stand-alone-mode
    }
    
    override fun onCloseDeviceRequested() {
        if(isStandAlonePropertyMode){
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        } else {
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).closeDeviceRequested = true
            
            finish()
            
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }
}