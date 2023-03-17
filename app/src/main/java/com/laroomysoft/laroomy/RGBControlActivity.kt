package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.flask.colorpicker.slider.LightnessSlider
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


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
    private lateinit var backButton: AppCompatImageButton
    private lateinit var deviceSettingsButton: AppCompatImageButton

    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
    private var currentColor = Color.WHITE
    private var currentColorTransitionProgram = -1
    private var currentMode = RGB_MODE_SINGLE_COLOR
    private var currentHardTransitionFlagValue = false
    private var onOffState = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r_g_b_control)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackPressed()
            }
        })

        // get the element ID
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedUIAdapterIndex = intent.getIntExtra("globalElementIndex", -1)

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

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.rgbBackButton).apply {
            setOnClickListener {
                handleBackPressed()
            }
        }
        
        // add device settings button functionality (if applicable)
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.rgbHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to device settings page
                    // navigate to the device settings activity..
                    val intent = Intent(this@RGBControlActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // get the state for this RGB control
        val rgbState = RGBSelectorState()
        rgbState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState
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
        this.headerTextView.text = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).elementText

        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
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
    
    private fun handleBackPressed(){
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
        if(verboseLog) {
            Log.d("M:RGBPage:onPause", "onPause executed in RGBControlActivity")
        }
        // if the user closed the application -> suspend connection
        // set information parameter for onResume()
        if(!this.isStandAlonePropertyMode) {
            // NOT stand-alone mode:
            // if the following is true, onBackPressed was executed before and the connection must remain active
            // because this is a back navigation to the device main activity
            if (!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {
                if (verboseLog) {
                    Log.d(
                        "M:RGBPage:onPause",
                        "RGB Control Activity: The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                this.expectedConnectionLoss = true
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
                        "M:RGBPage:onPause",
                        "RGB Control Activity: The user left the app -> suspend connection"
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
            Log.d("M:RGBPage:onResume", "onResume executed in RGBControlActivity")
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

                // TODO: test if the dualContainer will be set visible on reConnection (otherwise make the visibility secure here)

                // reconnect to the device if necessary (if the user has left the application)
                if (this.mustReconnect) {
                    if (verboseLog) {
                        Log.d(
                            "M:RGBPage:onResume",
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
                    singleButton.setBackgroundResource(R.drawable.rgb_selector_mode_button_left_selected)
                    singleButton.setTextColor(getColor(R.color.selectedTextColor))
                    transButton.setBackgroundResource(R.drawable.rgb_selector_mode_button_right_normal)
                    transButton.setTextColor(getColor(R.color.rgb_selector_unselected_button_text_color))
                    colorPickerContainer.visibility = View.VISIBLE
                    transitionSelectorContainer.visibility = View.GONE

                    this.currentMode = RGB_MODE_SINGLE_COLOR
                    // this.currentColorTransitionProgram = 0 -> don't do this ! It resets the current program selection !
                }
                RGB_MODE_TRANSITION -> {
                    transButton.setBackgroundResource(R.drawable.rgb_selector_mode_button_right_selected)
                    transButton.setTextColor(getColor(R.color.selectedTextColor))
                    singleButton.setBackgroundResource(R.drawable.rgb_selector_mode_button_left_normal)
                    singleButton.setTextColor(getColor(R.color.rgb_selector_unselected_button_text_color))
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
                        // do not call clear() on the bleManager in normal mode, this corrupts the list on the device main page
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
                this.currentColorTransitionProgram = rgbSelectorState.colorTransitionValue

                if (this.currentMode != RGB_MODE_TRANSITION) {
                    setPageSelectorModeState(RGB_MODE_TRANSITION)
                }
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

            if(this.propertyStateUpdateRequired){
                this.propertyStateUpdateRequired = false
                Executors.newSingleThreadScheduledExecutor().schedule({
                    ApplicationProperty.bluetoothConnectionManager.updatePropertyStates()
                }, TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT, TimeUnit.MILLISECONDS)
            }
        } else {
            // set UI-State
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
            showDualContainer(false)

            if(!expectedConnectionLoss){
                // unexpected loss of connection
                if(verboseLog){
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in RGBControlActivity.")
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
