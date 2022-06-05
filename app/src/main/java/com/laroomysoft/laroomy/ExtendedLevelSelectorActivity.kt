package com.laroomysoft.laroomy

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.ramotion.fluidslider.FluidSlider
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val TM_NONE = 0
const val TM_START = 1
const val TM_END = 2

class ExtendedLevelSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var currentLevel = 0
    private var minValue = 0
    private var maxValue = 100
    private var onOffState = false
    private var showOnOffSwitch = true
    private var transmitOnlyStartEndIndication = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extended_level_selector)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // get the header-text and set it to the property Name
        this.headerTextView = findViewById(R.id.elsHeaderTextView)
        this.headerTextView.apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedGlobalElementIndex
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
        this.backButton = findViewById(R.id.elsBackButton)

        // get the related complex state object
        val exLevelState = ExtendedLevelSelectorState()
        exLevelState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
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
            // TODO: the slider must be shifted up if the switch is not visible!
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

        this.backButton.setOnClickListener {
            this.onBackPressed()
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
                    collectDataSendCommandAndUpdateState(TM_END, true)
                }, 200, TimeUnit.MILLISECONDS)
            }
            // set begin tracking listener
            this.beginTrackingListener = {
                collectDataSendCommandAndUpdateState(TM_START, false)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
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

    override fun onPause() {
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "M:ELSPage:onPause",
                    "Extended Level Selector Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            expectedConnectionLoss = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:ELSPage:onResume", "onResume executed in Extended Level Selector Control")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false

        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
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
                        this.relatedElementID
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

        // set the switch visibility
        runOnUiThread {
            this.switchContainer.visibility = if (this.showOnOffSwitch) {
                // set switch state
                this.onOffSwitch.isChecked = extendedLevelSelectorState.onOffState
                // set slider constraint
                this.setSliderUpDown(false)
                // set visible
                View.VISIBLE
            } else {
                // TODO: the slider must be shifted up if the switch is not visible!
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
        // TODO: check if the onChecked event is triggered by the setting process
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
        this.collectDataSendCommandAndUpdateState(TM_NONE, true)
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
        this.collectDataSendCommandAndUpdateState(TM_NONE, true)
    }

    private fun collectDataSendCommandAndUpdateState(transmissionType: Int, updateState: Boolean){
        val exLevelState = ExtendedLevelSelectorState()
        exLevelState.levelValue = this.currentLevel
        exLevelState.onOffState = this.onOffState
        exLevelState.maxValue = this.maxValue
        exLevelState.minValue = this.minValue
        exLevelState.showOnOffSwitch = this.showOnOffSwitch

        when {
            (transmissionType == TM_START) -> exLevelState.isStart = true
            (transmissionType == TM_END) -> exLevelState.isEnd = true
        }

        ApplicationProperty.bluetoothConnectionManager.sendData(
            exLevelState.toExecutionString(this.relatedElementID)
        )

        if(updateState) {
            ApplicationProperty.bluetoothConnectionManager.updatePropertyStateDataNoEvent(
                exLevelState.toComplexPropertyState(),
                this.relatedElementID
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

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementID){
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
    }
}
