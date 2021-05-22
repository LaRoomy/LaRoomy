package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.ramotion.fluidslider.FluidSlider

class ExtendedLevelSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var currentLevel = 0
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    lateinit var onOffSwitch: SwitchCompat
    lateinit var fluidLevelSlider: FluidSlider
    lateinit var notificationTextView: TextView

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

        // set the header-text to the property Name
        findViewById<TextView>(R.id.elsHeaderTextView).text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@ExtendedLevelSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the related complex state object
        val exLevelState =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // save the 8bit level value
        this.currentLevel = exLevelState.valueOne

        // get the uiElements and set the initial values
        this.onOffSwitch = findViewById(R.id.elsSwitch)
        this.onOffSwitch.setOnClickListener{
            if(verboseLog) {
                Log.d(
                    "M:ELS:onOffSwitchClick",
                    "On/Off Switch was clicked. New state is: ${(it as SwitchCompat).isChecked}"
                )
            }
            onOffSwitchClicked()
        }
        this.onOffSwitch.isChecked = exLevelState.onOffState

        this.notificationTextView = findViewById(R.id.elsUserNotificationTextView)

        this.fluidLevelSlider = findViewById(R.id.exLevelSlider)
        this.fluidLevelSlider.position = get8BitValueAsPartOfOne(exLevelState.valueOne)
        this.fluidLevelSlider.positionListener = {
            this.onSliderPositionChanged(
                percentTo8Bit(
                (100*it).toInt())
            )
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
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
            ApplicationProperty.bluetoothConnectionManager.close()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:ELSPage:onResume", "onResume executed in Extended Level Selector Control")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@ExtendedLevelSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:ELSPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState){
        // set slider position
        this.fluidLevelSlider.position = get8BitValueAsPartOfOne(complexPropertyState.valueOne)
        // set switch state
        this.onOffSwitch.isChecked = complexPropertyState.onOffState
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun onOffSwitchClicked(){
        // send the instruction to the device
        ApplicationProperty.bluetoothConnectionManager.sendData(
            generateExecutionString(this.onOffSwitch.isChecked, this.currentLevel)
        )
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
        // send the instruction to the device
        ApplicationProperty.bluetoothConnectionManager.sendData(
            generateExecutionString(this.onOffSwitch.isChecked, this.currentLevel)
        )
    }

    private fun generateExecutionString(onOffState: Boolean, levelValue: Int) : String {
        val c = when(onOffState){
            true -> '1'
            else -> '0'
        }
        return "C${a8BitValueToString(this.relatedElementID)}$c${a8BitValueToString(levelValue)}$"
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

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)

        Log.e("M:ELSPage:onConnFailed", "Connection Attempt failed in Extended Level Selector Activity")
        (applicationContext as ApplicationProperty).logControl("Failed to connect in ExtendedLevelSelectorActivity")

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
                    "M:CB:ELSPage:ComplexPCg",
                    "Extended Level Selector Activity - Complex Property changed - Update the UI"
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
