package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.ramotion.fluidslider.FluidSlider
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExtendedLevelSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {
    
    private val tmNONE = 0
    private val tmSTART = 1
    private val tmEND = 2
    
    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
    private var currentLevel = 0
    private var minValue = 0
    private var maxValue = 100
    private var onOffState = false
    private var showOnOffSwitch = true
    private var transmitOnlyStartEndIndication = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false
    
    private val total: Int
    get() {
        return when {
            (this.maxValue < this.minValue) -> 0
            ((this.minValue == 0)&&(this.maxValue > 0)) -> this.maxValue
            ((this.maxValue == 0)&&(this.minValue < 0)) -> -(this.minValue)
            ((this.minValue < 0)&&(this.maxValue > 0)) -> (this.maxValue + (-(this.minValue)))
            ((this.minValue < 0)&&(this.maxValue < 0)) -> ((-(this.minValue)) - (-(this.maxValue)))
            ((this.minValue > 0)&&(this.maxValue > 0)) -> (this.maxValue - this.minValue)
            else -> 0
        }
    }

    private lateinit var onOffSwitch: SwitchCompat
    private lateinit var fluidLevelSlider: FluidSlider
    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var switchContainer: ConstraintLayout
    private lateinit var backButton: AppCompatImageButton
    private lateinit var deviceSettingsButton: AppCompatImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extended_level_selector)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // register onBackPressed event
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

        // get the header-text and set it to the property Name
        this.headerTextView = findViewById(R.id.elsHeaderTextView)
        this.headerTextView.apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedUIAdapterIndex
                ).elementText
        }

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the UI Elements
        this.switchContainer = findViewById(R.id.elsSwitchContainer)
        this.notificationTextView = findViewById(R.id.elsUserNotificationTextView)
        this.onOffSwitch = findViewById(R.id.elsSwitch)
        this.fluidLevelSlider = findViewById(R.id.exLevelSlider)
        
        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.elsBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // add device settings button functionality (if applicable)
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.elsHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to device settings page
                    // navigate to the device settings activity..
                    val intent = Intent(this@ExtendedLevelSelectorActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // get the related complex state object
        val exLevelState = ExtendedLevelSelectorState()
        exLevelState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState
        )

        // save the ex level data
        this.currentLevel = exLevelState.levelValue
        this.onOffState = exLevelState.onOffState
        this.minValue = exLevelState.minValue
        this.maxValue = exLevelState.maxValue
        this.showOnOffSwitch = exLevelState.showOnOffSwitch
        this.transmitOnlyStartEndIndication = exLevelState.transmitOnlyStartEndOfTracking

        // hide the switch if requested
        if(!this.showOnOffSwitch){
            // the slider must be shifted up if the switch is not visible!
            this.switchContainer.visibility = View.GONE
            this.setSliderUpDown(true)
        }


        // configure UI Elements
        this.onOffSwitch.apply {

            setOnClickListener {
                if (verboseLog) {
                    Log.d(
                        "M:ELS:onOffSwitchClick",
                        "On/Off Switch was clicked. New state is: ${(it as SwitchCompat).isChecked}"
                    )
                }
                onOffSwitchClicked()
            }
            isChecked = exLevelState.onOffState
        }


        this.fluidLevelSlider.apply {
            // apply values
            if(total != 0){
                this.startText = "$minValue"
                this.endText = "$maxValue"
                position = levelToSliderPosition(exLevelState.levelValue)
                this.bubbleText = "${exLevelState.levelValue}"
                invalidate()
            } else {
                // invalid data
                position = 0f
                this.bubbleText = "E!"
            }
            // set listener
            positionListener = {
                val realPos =
                    sliderPositionToLevelValue(it)
                bubbleText = "$realPos"

                if(!transmitOnlyStartEndIndication) {
                    if (realPos != currentLevel) {
                        onSliderPositionChanged(
                            realPos
                        )
                        //currentLevel = realPos // done in onSliderPositionChanged(..)
                    }
                }
            }
            // set end tracking listener
            this.endTrackingListener = {
                Executors.newSingleThreadScheduledExecutor().schedule({
                    val realPos = sliderPositionToLevelValue(fluidLevelSlider.position)
                    currentLevel = realPos
                    collectDataSendCommandAndUpdateState(tmEND, true)
                }, 200, TimeUnit.MILLISECONDS)
            }
            // set begin tracking listener
            this.beginTrackingListener = {
                collectDataSendCommandAndUpdateState(tmSTART, false)
            }
        }
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
                        "M:ELSPage:onPause",
                        "Extended Level Selector Activity: The user left the app -> suspend connection"
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
                        "M:ELSPage:onPause",
                        "Extended Level Selector Activity: The user left the app -> suspend connection"
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
            Log.d("M:ELSPage:onResume", "onResume executed in Extended Level Selector Control")
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
                            "M:ELSPage:onResume",
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

    private fun setCurrentViewStateFromComplexPropertyState(extendedLevelSelectorState: ExtendedLevelSelectorState){

        // save the data to local
        this.onOffState = extendedLevelSelectorState.onOffState
        this.currentLevel = extendedLevelSelectorState.levelValue
        this.minValue = extendedLevelSelectorState.minValue
        this.maxValue = extendedLevelSelectorState.maxValue
        this.showOnOffSwitch = extendedLevelSelectorState.showOnOffSwitch
        this.transmitOnlyStartEndIndication = extendedLevelSelectorState.transmitOnlyStartEndOfTracking
        
        runOnUiThread {
            // set the switch visibility
            this.switchContainer.visibility = if (this.showOnOffSwitch) {
                // set switch state
                this.onOffSwitch.isChecked = extendedLevelSelectorState.onOffState
                // set slider constraint
                this.setSliderUpDown(false)
                // set visible
                View.VISIBLE
            } else {
                // the slider must be shifted up if the switch is not visible!
                // set slider constraint
                this.setSliderUpDown(true)
                // set visibility to gone
                View.GONE
            }

            this.fluidLevelSlider.apply {
                this.startText = "${extendedLevelSelectorState.minValue}"
                this.endText = "${extendedLevelSelectorState.maxValue}"
                position = levelToSliderPosition(extendedLevelSelectorState.levelValue)
                this.bubbleText = "${extendedLevelSelectorState.levelValue}"
                this.invalidate()
            }
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

    private fun setSliderUpDown(setUp: Boolean) {
        if (setUp) {
            val constraintLayout =
                findViewById<ConstraintLayout>(R.id.extendedLevelSelectorActivityParentContainer)
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.connect(
                R.id.extendedLevelSelectorActivityControllerContainer,
                ConstraintSet.TOP,
                R.id.extendedLevelSelectorActivityParentContainer,
                ConstraintSet.TOP
            )
            constraintSet.applyTo(constraintLayout)
        } else {
            val constraintLayout =
                findViewById<ConstraintLayout>(R.id.extendedLevelSelectorActivityParentContainer)
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.connect(
                R.id.extendedLevelSelectorActivityControllerContainer,
                ConstraintSet.TOP,
                R.id.elsHeaderContainer,
                ConstraintSet.BOTTOM
            )
            constraintSet.applyTo(constraintLayout)
        }
    }

    private fun sliderPositionToLevelValue(pos: Float) : Int {
        return (this.minValue + (this.total * pos)).toInt()
    }

    private fun levelToSliderPosition(level: Int) : Float {
        return when {
            (level == this.minValue) -> 0f
            (level == this.maxValue) -> 1f
            else -> {
                val realLevel = when {
                    ((this.minValue < 0)&&(this.maxValue == 0)) -> {
                        (-(level)) - (-(this.minValue))
                    }
                    ((this.minValue < 0)&&(this.maxValue < 0)) -> {
                        (-(this.minValue)) - (-(level))
                    }
                    ((this.minValue == 0)&&(this.maxValue > 0)) -> {
                        level
                    }
                    ((this.minValue > 0)&&(this.maxValue > 0)) -> {
                        level - this.minValue
                    }
                    ((this.minValue < 0)&&(this.maxValue > 0)) -> {
                        when {
                            (level < 0) -> {
                                (-(this.minValue)) - (-(level))
                            }
                            (level == 0) -> {
                                -(this.minValue)
                            }
                            else -> {
                                (-(this.minValue)) + level
                            }
                        }
                    }
                    else -> 0
                }
                val totalValue = this.total.toFloat()
                if(totalValue != 0f) {
                    (realLevel.toFloat() / totalValue)
                } else {
                    0f
                }
            }
        }
    }

    private fun onOffSwitchClicked(){
        this.onOffState = this.onOffSwitch.isChecked
        this.collectDataSendCommandAndUpdateState(tmNONE, true)
    }

    private fun onSliderPositionChanged(value: Int){
        if(verboseLog) {
            Log.d(
                "M:CB:ELS:SliderPosC",
                "Slider Position changed in ExtendedLevelSelectorActivity. New Level: $value"
            )
        }
        // save the new value
        this.currentLevel = value
        this.collectDataSendCommandAndUpdateState(tmNONE, true)
    }

    private fun collectDataSendCommandAndUpdateState(transmissionType: Int, updateState: Boolean){
        val exLevelState = ExtendedLevelSelectorState()
        exLevelState.levelValue = this.currentLevel
        exLevelState.onOffState = this.onOffState
        exLevelState.maxValue = this.maxValue
        exLevelState.minValue = this.minValue
        exLevelState.showOnOffSwitch = this.showOnOffSwitch

        when {
            (transmissionType == tmSTART) -> exLevelState.isStart = true
            (transmissionType == tmEND) -> exLevelState.isEnd = true
        }

        ApplicationProperty.bluetoothConnectionManager.sendData(
            exLevelState.toExecutionString(this.relatedElementIndex)
        )

        if(updateState) {
            ApplicationProperty.bluetoothConnectionManager.updatePropertyStateDataNoEvent(
                exLevelState.toComplexPropertyState(),
                this.relatedElementIndex
            )
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)

        if(verboseLog) {
            Log.d(
                "M:ELSPage:ConStateChge",
                "Connection state changed in ExtendedLevelSelector Activity. New Connection state is: $state"
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
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in ExtendedLevelSelectorActivity.")
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
                    "M:CB:ELSPage:ComplexPCg",
                    "Extended Level Selector Activity - Complex Property changed - Update the UI"
                )
            }
            val exLevelState = ExtendedLevelSelectorState()
            exLevelState.fromComplexPropertyState(newState)
            this.setCurrentViewStateFromComplexPropertyState(exLevelState)
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
