package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import com.ramotion.fluidslider.FluidSlider

class ExtendedLevelSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var currentLevel = 0
    private var onOffState = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    private lateinit var onOffSwitch: SwitchCompat
    private lateinit var fluidLevelSlider: FluidSlider
    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView

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

        // get the related complex state object
        val exLevelState = ExtendedLevelSelectorState()
        exLevelState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        // save the ex level data
        this.currentLevel = exLevelState.levelValue
        this.onOffState = exLevelState.onOffState

        // get the uiElements and set the initial values
        this.onOffSwitch = findViewById(R.id.elsSwitch)
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

        this.notificationTextView = findViewById(R.id.elsUserNotificationTextView)

        this.fluidLevelSlider =
            findViewById(R.id.exLevelSlider)

        this.fluidLevelSlider.apply {
            position = get8BitValueAsPartOfOne(exLevelState.levelValue)
            positionListener = {
                onSliderPositionChanged(
                    percentTo8Bit(
                        (100 * it).toInt()
                    )
                )
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
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:ELSPage:onResume", "onResume executed in Extended Level Selector Control")
        }

        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:ELSPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        } else {
            // notify the remote device of the invocation of this property-page
            ApplicationProperty.bluetoothConnectionManager.notifyComplexPropertyPageInvoked(this.relatedElementID)
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(extendedLevelSelectorState: ExtendedLevelSelectorState){
        // set slider position
        this.fluidLevelSlider.position = get8BitValueAsPartOfOne(extendedLevelSelectorState.levelValue)
        // set switch state
        this.onOffSwitch.isChecked = extendedLevelSelectorState.onOffState

        // TODO: check if the onChecked event is triggered by the setting process
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun onOffSwitchClicked(){
        this.onOffState = this.onOffSwitch.isChecked
        this.collectDataSendCommandAndUpdateState()
    }

    private fun onSliderPositionChanged(value: Int){
        // the value must be in 0..255 (8bit) format
        if(verboseLog) {
            Log.d(
                "M:CB:ELS:SliderPosC",
                "Slider Position changed in ExtendedLevelSelectorActivity. New Level: $value"
            )
        }
        // save the new value
        this.currentLevel = value
        this.collectDataSendCommandAndUpdateState()
    }

    private fun collectDataSendCommandAndUpdateState(){
        val exLevelState = ExtendedLevelSelectorState()
        exLevelState.levelValue = this.currentLevel
        exLevelState.onOffState = this.onOffState

        ApplicationProperty.bluetoothConnectionManager.sendData(
            exLevelState.toExecutionString(this.relatedElementID)
        )

        ApplicationProperty.bluetoothConnectionManager.updatePropertyStateDataNoEvent(
            exLevelState.toComplexPropertyState(),
            this.relatedElementID
        )
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
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
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
