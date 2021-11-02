package com.laroomysoft.laroomy

import android.app.ApplicationErrorReport
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.flask.colorpicker.slider.LightnessSlider


class RGBControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, OnColorSelectedListener, OnColorChangedListener, SeekBar.OnSeekBarChangeListener {

    private lateinit var colorPickerView: ColorPickerView
    private lateinit var lightnessSliderView: LightnessSlider
    private lateinit var onOffSwitch: SwitchCompat
    private lateinit var transitionSwitch: SwitchCompat
    private lateinit var programSpeedSeekBar: SeekBar
    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView

    private lateinit var onOffSwitchContainer: ConstraintLayout
    private lateinit var modeSelectorContainer: ConstraintLayout
    private lateinit var transitionTypeSetupContainer: ConstraintLayout

    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedGlobalElementIndex = -1
    private var currentColor = Color.WHITE
    private var currentColorTransitionProgram = -1
    private var currentMode = RGB_MODE_SINGLE_COLOR
    private var currentHardTransitionFlagValue = false
    private var onOffState = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r_g_b_control)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // get container elements
        onOffSwitchContainer = findViewById(R.id.rgbSwitchContainer)
        modeSelectorContainer = findViewById(R.id.modeSelectorContainer)
        transitionTypeSetupContainer = findViewById(R.id.transitionTypeSetupContainer)

        // get color picker
        colorPickerView = findViewById(R.id.color_picker_view)
        // get lightnessSlider
        lightnessSliderView = findViewById(R.id.v_lightness_slider)
        // get notification textView
        notificationTextView = findViewById(R.id.rgbUserNotificationTextView)

        // add listeners
        colorPickerView.addOnColorSelectedListener(this)
        colorPickerView.addOnColorChangedListener(this)

        // get the state for this RGB control
        val rgbState = RGBSelectorState()
        rgbState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        // set the visibility state of elements
        if(!rgbState.onOffButtonVisibility){
            this.onOffSwitchContainer.visibility = View.GONE
        }
        if(!rgbState.singleOrTransitionButtonVisibility){
            this.modeSelectorContainer.visibility = View.GONE
        }
        if(!rgbState.intensitySliderVisibility){
            this.lightnessSliderView.visibility = View.GONE
        }
        if(!rgbState.softOrHardTransitionSwitchVisibility){
            this.transitionTypeSetupContainer.visibility = View.GONE
        }

        // get the current color
        val actualColor = rgbState.getColor()

        if(actualColor != currentColor)
            currentColor = actualColor

        // set the current device-color to the view (picker + slider)
        colorPickerView.setColor(actualColor, false)
        lightnessSliderView.postDelayed({
            lightnessSliderView.setColor(actualColor)
        }, 500)

        // set the header-text to the property-name
        this.headerTextView = findViewById(R.id.rgbHeaderTextView)
        this.headerTextView.text = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this, this@RGBControlActivity)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get program speed seekBar
        programSpeedSeekBar = findViewById(R.id.rgbProgramSpeedSeekBar)

        // if a program is active -> set the view to transition-view and set the right value in the slider
        val programStatus = rgbState.colorTransitionValue - 1

        // save the program
        this.currentColorTransitionProgram = rgbState.colorTransitionValue

        if(programStatus != -1){
            // program must be active

            //this.currentColorTransitionProgram = rgbState.colorTransitionValue

            programSpeedSeekBar.progress = programStatus
            setPageSelectorModeState(RGB_MODE_TRANSITION)
        }
        // set program-speed seekBar changeListener
        programSpeedSeekBar.setOnSeekBarChangeListener(this)

        // set on/off Switch-state changeListener and state params
        onOffSwitch = findViewById(R.id.rgbSwitch)
        onOffSwitch.isChecked = rgbState.onOffState

        this.currentHardTransitionFlagValue = rgbState.hardTransitionFlag

        this.onOffState = rgbState.onOffState

        onOffSwitch.apply {
            isChecked = rgbState.onOffState
            setOnClickListener {
                if (verboseLog) {
                    Log.d(
                        "M:RGB:OnOffSwitchClick",
                        "On / Off Switch was clicked. New state is: ${(it as SwitchCompat).isChecked}"
                    )
                }
                onMainOnOffSwitchClick(it)
            }
        }

        // set transition switch condition and onClick-Listener
        transitionSwitch = findViewById(R.id.transitionTypeSwitch)
        transitionSwitch.isChecked = !rgbState.hardTransitionFlag
        transitionSwitch.setOnClickListener{
            if(verboseLog) {
                Log.d(
                    "M:RGB:transSwitchClick",
                    "Transition Switch was clicked. New state is: ${(it as SwitchCompat).isChecked}"
                )
            }
            onTransitionTypeSwitchClicked(it)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()

        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateID = this.relatedElementIndex
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
        if(verboseLog) {
            Log.d("M:RGBPage:onPause", "onPause executed in RGBControlActivity")
        }

        // TODO: check if onPause will be executed after onBackPressed!!!!!!!!!!!!!!!

        // if the user closed the application -> suspend connection
        // set information parameter for onResume()

        // if this is true, onBackPressed was executed before
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "M:RGBPage:onPause",
                    "RGB Control Activity: The user closes the app -> suspend connection"
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
            Log.d("M:RGBPage:onResume", "onResume executed in RGBControlActivity")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@RGBControlActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // TODO: test if the dualContainer will be set visible on reConnection (otherwise make the visibility secure here)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:RGBPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        this.currentColorTransitionProgram = progress + 1
        this.collectDataSendCommandAndUpdateState()
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    fun onSingleColorModeButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        this.setPageSelectorModeState(RGB_MODE_SINGLE_COLOR)
        this.collectDataSendCommandAndUpdateState()
    }

    fun onTransitionModeButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        this.setPageSelectorModeState(RGB_MODE_TRANSITION)
        this.collectDataSendCommandAndUpdateState()
    }

    private fun collectDataSendCommandAndUpdateState(){
        val rgbSelectorState = RGBSelectorState()

        rgbSelectorState.onOffState = this.onOffState

        if(this.onOffSwitchContainer.visibility == View.GONE){
            rgbSelectorState.onOffButtonVisibility = false
        }
        if(this.modeSelectorContainer.visibility == View.GONE){
            rgbSelectorState.singleOrTransitionButtonVisibility = false
        }
        if(this.lightnessSliderView.visibility == View.GONE){
            rgbSelectorState.intensitySliderVisibility = false
        }
        if(this.transitionTypeSetupContainer.visibility == View.GONE){
            rgbSelectorState.softOrHardTransitionSwitchVisibility = false
        }
        if(currentMode == RGB_MODE_SINGLE_COLOR){
            rgbSelectorState.colorTransitionValue = 0
        } else {
            rgbSelectorState.colorTransitionValue = this.currentColorTransitionProgram
        }
        rgbSelectorState.redValue = Color.red(this.currentColor)
        rgbSelectorState.greenValue = Color.green(this.currentColor)
        rgbSelectorState.blueValue = Color.blue(this.currentColor)
        rgbSelectorState.hardTransitionFlag = this.currentHardTransitionFlagValue

        ApplicationProperty.bluetoothConnectionManager.sendData(
            rgbSelectorState.toExecutionString(this.relatedElementIndex)
        )

        ApplicationProperty.bluetoothConnectionManager.updatePropertyStateDataNoEvent(
            rgbSelectorState.toComplexPropertyState(),
            this.relatedElementIndex
        )
    }

    private fun showDualContainer(show: Boolean){
        runOnUiThread {
            findViewById<ConstraintLayout>(R.id.dualContainer).visibility =
                if (show) View.VISIBLE else View.GONE
        }
    }

    private fun setPageSelectorModeState(state: Int){

        // TODO: handle rgb-mode off!!

        runOnUiThread {

            val singleButton = findViewById<Button>(R.id.singleColorSelectionButton)
            val transButton = findViewById<Button>(R.id.transitionSelectionButton)
            val colorPickerContainer =
                findViewById<ConstraintLayout>(R.id.rgbSelectorWidgetContainer)
            val transitionSelectorContainer =
                findViewById<ConstraintLayout>(R.id.rgbTransitionControlContainer)

            when (state) {
                RGB_MODE_SINGLE_COLOR -> {
                    singleButton.setBackgroundColor(getColor(R.color.RGB_SelectedButtonColor))
                    singleButton.setTextColor(getColor(R.color.selectedTextColor))
                    transButton.setBackgroundColor(getColor(R.color.transparentViewColor))
                    transButton.setTextColor(getColor(R.color.normalTextColor))
                    colorPickerContainer.visibility = View.VISIBLE
                    transitionSelectorContainer.visibility = View.GONE

                    this.currentMode = RGB_MODE_SINGLE_COLOR
                    this.currentColorTransitionProgram = 0
                }
                RGB_MODE_TRANSITION -> {
                    transButton.setBackgroundColor(getColor(R.color.RGB_SelectedButtonColor))
                    transButton.setTextColor(getColor(R.color.selectedTextColor))
                    singleButton.setBackgroundColor(getColor(R.color.transparentViewColor))
                    singleButton.setTextColor(getColor(R.color.normalTextColor))
                    colorPickerContainer.visibility = View.GONE
                    transitionSelectorContainer.visibility = View.VISIBLE

                    this.currentMode = RGB_MODE_TRANSITION
                    if(this.currentColorTransitionProgram < 1) {
                        this.currentColorTransitionProgram = 1
                    }
                }
                else -> {
                    // set to rgb-mode "off" ?

                    // do nothing!
                    // do nothing!

                    //singleButton.setBackgroundColor(getColor(R.color.transparentViewColor))
                    //singleButton.setTextColor(getColor(R.color.normalTextColor))
                    //transButton.setBackgroundColor(getColor(R.color.transparentViewColor))
                    //transButton.setTextColor(getColor(R.color.normalTextColor))
                    //colorPickerContainer.visibility = View.GONE
                    //transitionSelectorContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun onMainOnOffSwitchClick(view: View){

        this.onOffState = (view as SwitchCompat).isChecked

        runOnUiThread {
            if (this.onOffState) {
                setPageSelectorModeState(this.currentMode)
            } else {
                setPageSelectorModeState(RGB_MODE_OFF)
            }
        }
        this.collectDataSendCommandAndUpdateState()
    }

    private fun onTransitionTypeSwitchClicked(view: View){
        this.currentHardTransitionFlagValue = !((view as SwitchCompat).isChecked)
        this.collectDataSendCommandAndUpdateState()
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.text = message
            notificationTextView.setTextColor(getColor(colorID))
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(rgbSelectorState: RGBSelectorState){

        runOnUiThread {
            // set the visibility of the elements accordingly to the v-flag definition
            this.onOffSwitchContainer.visibility =
                if(rgbSelectorState.onOffButtonVisibility){
                    View.VISIBLE
                } else {
                    View.GONE
                }
            this.modeSelectorContainer.visibility =
                if(rgbSelectorState.singleOrTransitionButtonVisibility){
                    View.VISIBLE
                } else {
                    View.GONE
                }
            this.lightnessSliderView.visibility =
                if(rgbSelectorState.intensitySliderVisibility){
                    View.VISIBLE
                } else {
                    View.GONE
                }
            this.transitionTypeSetupContainer.visibility =
                if(rgbSelectorState.softOrHardTransitionSwitchVisibility){
                    View.VISIBLE
                } else {
                    View.GONE
                }

            // 1. On/Off-State
            this.onOffState = rgbSelectorState.onOffState
            if (this.onOffState != this.onOffSwitch.isChecked)
                this.onOffSwitch.isChecked = this.onOffState

            // 2. Transition-Type
            if (!rgbSelectorState.hardTransitionFlag != this.transitionSwitch.isChecked)
                this.transitionSwitch.isChecked = !rgbSelectorState.hardTransitionFlag

            // 3. ColorPicker view-color
            val actualColor =
                rgbSelectorState.getColor()

            if (actualColor != this.currentColor) {
                this.currentColor = actualColor
                colorPickerView.setColor(actualColor, false)
                if(rgbSelectorState.intensitySliderVisibility) {
                    lightnessSliderView.setColor(actualColor)
                }
            }

            // 4. Current Mode and Program-Slider Position combined
            val programStatus =
                rgbSelectorState.colorTransitionValue - 1
            if (programStatus != -1) {
                // program must be active
                programSpeedSeekBar.progress = programStatus
                this.currentColorTransitionProgram = rgbSelectorState.colorTransitionValue // TODO: check this!!!

                if (this.currentMode != RGB_MODE_TRANSITION)
                    setPageSelectorModeState(RGB_MODE_TRANSITION)
            } else {
                // must be single color mode
                if (this.currentMode != RGB_MODE_SINGLE_COLOR)
                    setPageSelectorModeState(RGB_MODE_SINGLE_COLOR)
            }
        }
    }

    override fun onColorSelected(selectedColor: Int) {

        if(verboseLog) {
            Log.d(
                "M:RGBPage:onColorSelect",
                "New color selected in RGBControlActivity. New Color: ${
                    Integer.toHexString(selectedColor)
                }"
            )
        }
        this.currentColor = selectedColor
        this.collectDataSendCommandAndUpdateState()
    }

    override fun onColorChanged(selectedColor: Int) {
        if(verboseLog) {
            Log.d(
                "M:RGBPage:onColorChange",
                "Color changed in RGBControlActivity. New Color: ${Integer.toHexString(selectedColor)}"
            )
        }

        this.currentColor = selectedColor
        this.collectDataSendCommandAndUpdateState()
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "M:RGBPage:ConStateChge",
                "Connection state changed in RGB Activity. New Connection state is: $state"
            )
        }
        if(state){
            // set UI-State
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
            showDualContainer(true)
        } else {
            // set UI-State
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
            showDualContainer(false)
        }
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        // if there is a connection failure -> navigate back
        when(message){
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

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
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
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementIndex){


            if(verboseLog) {
                Log.d(
                    "M:CB:RGBPage:ComplexPCg",
                    "RGB Activity - Complex Property changed - Updating the UI"
                )
            }

            val rgbSelectorState = RGBSelectorState().apply {
                fromComplexPropertyState(newState)
            }
            this.setCurrentViewStateFromComplexPropertyState(rgbSelectorState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }
}
