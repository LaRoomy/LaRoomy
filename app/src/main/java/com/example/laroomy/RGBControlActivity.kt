package com.example.laroomy

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorChangedListener
import com.flask.colorpicker.OnColorSelectedListener
import com.flask.colorpicker.slider.LightnessSlider

const val RGB_MODE_OFF = 0
const val RGB_MODE_SINGLE_COLOR = 1
const val RGB_MODE_TRANSITION = 2

class RGBControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, OnColorSelectedListener, OnColorChangedListener, SeekBar.OnSeekBarChangeListener {

    lateinit var colorPickerView: ColorPickerView
    lateinit var lightnessSliderView: LightnessSlider
    lateinit var onOffSwitch: Switch
    lateinit var transitionSwitch: Switch
    lateinit var programSpeedSeekBar: SeekBar
    private var mustReconnect = false
    var relatedElementID = -1
    var relatedGlobalElementIndex = -1
    var currentColor = Color.WHITE
    var currentProgram = 12
    var currentMode = RGB_MODE_SINGLE_COLOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r_g_b_control)

        // get the element ID
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // get color picker
        colorPickerView = findViewById(R.id.color_picker_view)
        // get lightnessSlider
        lightnessSliderView = findViewById(R.id.v_lightness_slider)

        // add listeners
        colorPickerView.addOnColorSelectedListener(this)
        colorPickerView.addOnColorChangedListener(this)

        // get the state for this RGB control
        val colorState = ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // set the current device-color to the view (picker + slider)
        val actualColor = Color.rgb(colorState.valueOne, colorState.valueTwo, colorState.valueThree)
        colorPickerView.setInitialColor(actualColor, false)
        lightnessSliderView.setColor(actualColor)

        // set the header-text to the property-name
        findViewById<TextView>(R.id.rgbHeaderTextView).text =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@RGBControlActivity)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // get program speed seekBar
        programSpeedSeekBar = findViewById(R.id.rgbProgramSpeedSeekBar)

        // if a program is active -> set the view to transition-view and set the right value in the slider
        val programStatus =
            this.transitionSpeedSliderPositionFromCommandValue(colorState.commandValue)
        if(programStatus != -1){
            // program must be active
            programSpeedSeekBar.progress = programStatus
            setPageSelectorModeState(RGB_MODE_TRANSITION)
        }
        // set program-speed seekBar changeListener
        programSpeedSeekBar.setOnSeekBarChangeListener(this)

        // set on/off Switch-state changeListener and state
        onOffSwitch = findViewById(R.id.rgbSwitch)
        onOffSwitch.isChecked = when(colorState.commandValue){
            0 -> false
            else -> true
        }
        onOffSwitch.setOnClickListener{
            Log.d("M:RGB:OnOffSwitchClick", "On / Off Switch was clicked. New state is: ${(it as Switch).isChecked}")
            onMainOnOffSwitchClick(it)
        }

        // set transition switch condition and onClick-Listener
        transitionSwitch = findViewById(R.id.transitionTypeSwitch)
        transitionSwitch.isChecked = !colorState.hardTransitionFlag
        transitionSwitch.setOnClickListener{
            Log.d("M:RGB:transSwitchClick", "Transition Switch was clicked. New state is: ${(it as Switch).isChecked}")
            onTransitionTypeSwitchClicked(it)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()

        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        //finish()
    }

    override fun onPause() {
        super.onPause()
        Log.d("M:RGBPage:onPause", "onPause executed in RGBControlActivity")

        // TODO: check if onPause will be executed after onBackPressed!!!!!!!!!!!!!!!

        // if the user closed the application -> suspend connection
        // set information parameter for onResume()

        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            Log.d("M:RGBPage:onPause", "The user closes the app -> suspend connection")
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManger.close()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:RGBPage:onResume", "onResume executed in RGBControlActivity")

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@RGBControlActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // TODO: test if the dualContainer will be set visible on reConnection (otherwise make the visibility secure here)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            Log.d("M:RGBPage:onResume", "The connection was suspended -> try to reconnect")
            ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        this.sendInstruction(11 + progress, 0, 0,0)
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    fun onSingleColorModeButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        setPageSelectorModeState(RGB_MODE_SINGLE_COLOR)

        if(findViewById<Switch>(R.id.rgbSwitch).isChecked){
            this.setCurrentSingleColor()
        }
    }

    fun onTransitionModeButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        setPageSelectorModeState(RGB_MODE_TRANSITION)

        if(findViewById<Switch>(R.id.rgbSwitch).isChecked){
            this.setCurrentProgram()
        }
    }

    private fun sendInstruction(command: Int, redValue: Int, greenValue: Int, blueValue: Int){

        val commandAsString = a8BitValueToString(command)
        val redValueAsString = a8BitValueToString(redValue)
        val greenValueAsString = a8BitValueToString(greenValue)
        val blueValueAsString = a8BitValueToString(blueValue)

        val instruction = "C${a8BitValueToString(ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(this.relatedGlobalElementIndex).elementID)}${commandAsString}${redValueAsString}${greenValueAsString}${blueValueAsString}$"

        ApplicationProperty.bluetoothConnectionManger.sendData(instruction)
    }

    private fun transitionSpeedSliderPositionFromCommandValue(commandValue: Int): Int {
        return when(commandValue){
            11 -> 0
            12 -> 1
            13 -> 2
            14 -> 3
            15 -> 4
            else -> -1
        }
    }

    private fun setCurrentSingleColor(){
        this.sendInstruction(8, Color.red(this.currentColor), Color.green(this.currentColor), Color.blue(this.currentColor))
    }

    private fun setCurrentProgram(){
        this.sendInstruction(this.currentProgram, 0, 0,0)
    }

    private fun setAllOff(){
        this.sendInstruction(0,0,0,0)
    }

    private fun showDualContainer(show: Boolean){
        runOnUiThread {
            findViewById<ConstraintLayout>(R.id.dualContainer).visibility =
                if (show) View.VISIBLE else View.GONE
        }
    }

    private fun setPageSelectorModeState(state: Int){

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
                }
                RGB_MODE_TRANSITION -> {
                    transButton.setBackgroundColor(getColor(R.color.RGB_SelectedButtonColor))
                    transButton.setTextColor(getColor(R.color.selectedTextColor))
                    singleButton.setBackgroundColor(getColor(R.color.transparentViewColor))
                    singleButton.setTextColor(getColor(R.color.normalTextColor))
                    colorPickerContainer.visibility = View.GONE
                    transitionSelectorContainer.visibility = View.VISIBLE

                    this.currentMode = RGB_MODE_TRANSITION
                }
                else -> {
                    // set to rgb-mode "off" ?

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

        val state = (view as Switch).isChecked

        runOnUiThread {

            if (state) {
                setPageSelectorModeState(this.currentMode)
                //notifyUser("Checked", R.color.InfoColor)
            } else {
                setPageSelectorModeState(RGB_MODE_OFF)
                //notifyUser("Unchecked", R.color.InfoColor)
            }
        }

        // send the on / off command to the device
        if(state){

            if(this.currentMode == RGB_MODE_SINGLE_COLOR){

                this.setCurrentSingleColor()

            } else if(this.currentMode == RGB_MODE_TRANSITION){

                this.setCurrentProgram()
            }
        } else {
            this.setAllOff()
        }
    }

    private fun onTransitionTypeSwitchClicked(view: View){
        val type = when((view as Switch).isChecked){
            true -> 201 // soft transition
            else -> 200 // hard transition
        }
        this.sendInstruction(type, 0,0,0)
    }

    private fun notifyUser(message: String, colorID: Int){
        val textView = findViewById<TextView>(R.id.rgbUserNotificationTextView)
        textView.text = message
        textView.setTextColor(getColor(colorID))
    }

    private fun notifyUserWithColorAsInt(message: String, color: Int){
        val textView = findViewById<TextView>(R.id.rgbUserNotificationTextView)
        textView.text = message
        textView.setTextColor(color)
    }

    private fun setCurrentViewStateFromComplexPropertyState(colorState: ComplexPropertyState){

        // 1. On/Off-State
        val onOffState =
            (colorState.commandValue != 0)
        if(onOffState != this.onOffSwitch.isChecked)
            this.onOffSwitch.isChecked = onOffState

        // 2. Transition-Type
        if(!colorState.hardTransitionFlag != this.transitionSwitch.isChecked)
            this.transitionSwitch.isChecked = !colorState.hardTransitionFlag

        // 3. ColorPicker view-color
        val actualColor = Color.rgb(colorState.valueOne, colorState.valueTwo, colorState.valueThree)
        if(actualColor != this.currentColor) {
            colorPickerView.setColor(actualColor, false)
            lightnessSliderView.setColor(actualColor)
        }

        // 4. Current Mode and Program-Slider Position combined
        val programStatus =
            this.transitionSpeedSliderPositionFromCommandValue(colorState.commandValue)
        if(programStatus != -1){
            // program must be active
            programSpeedSeekBar.progress = programStatus

            if(this.currentMode != RGB_MODE_TRANSITION)
                setPageSelectorModeState(RGB_MODE_TRANSITION)
        } else {
            // must be single color mode
            if(this.currentMode != RGB_MODE_SINGLE_COLOR)
                setPageSelectorModeState(RGB_MODE_SINGLE_COLOR)
        }
    }

    override fun onColorSelected(selectedColor: Int) {
        Log.d("M:RGBPage:onColorSelect","New color selected in RGBControlActivity. New Color: ${Integer.toHexString(selectedColor)}")

        this.currentColor = selectedColor

        // temporary hex color display
//        runOnUiThread {
//            notifyUserWithColorAsInt(
//                "${getString(R.string.RGBPageColorSelectionInformation)} ${Integer.toHexString(
//                    selectedColor
//                )}", selectedColor
//            )
//        }

        if(findViewById<Switch>(R.id.rgbSwitch).isChecked) {

            val r = Color.red(selectedColor)
            val g = Color.green(selectedColor)
            val b = Color.blue(selectedColor)
            val elID = a8BitValueToString(
                ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(this.relatedGlobalElementIndex).elementID
            )
            val instruction =
                "C${elID}008${a8BitValueToString(r)}${a8BitValueToString(g)}${a8BitValueToString(b)}$"

            ApplicationProperty.bluetoothConnectionManger.sendData(instruction)
        }
    }

    override fun onColorChanged(selectedColor: Int) {
        Log.d("M:RGBPage:onColorChange","Color changed in RGBControlActivity. New Color: ${Integer.toHexString(selectedColor)}")

        this.currentColor = selectedColor

        // temporary hex color display
//        runOnUiThread {
//            notifyUserWithColorAsInt(
//                "${getString(R.string.RGBPageColorSelectionInformation)} ${Integer.toHexString(
//                    selectedColor
//                )}", selectedColor
//            )
//        }

        if(findViewById<Switch>(R.id.rgbSwitch).isChecked) {

            val r = Color.red(selectedColor)
            val g = Color.green(selectedColor)
            val b = Color.blue(selectedColor)
            val elID = a8BitValueToString(
                ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(this.relatedGlobalElementIndex).elementID
            )
            val instruction =
                "C${elID}008${a8BitValueToString(r)}${a8BitValueToString(g)}${a8BitValueToString(b)}$"

            ApplicationProperty.bluetoothConnectionManger.sendData(instruction)
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:RGBPage:ConStateChge", "Connection state changed in RGB Activity. New Connection state is: $state")
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
            showDualContainer(true)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
            showDualContainer(false)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        Log.e("M:RGBPage:onConnFailed", "Connection Attempt failed in RGBControlActivity")
        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true

        val element =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.elementID == this.relatedElementID){
            Log.d("M:CB:RGBPage:ComplexPCg", "RGB Activity - Complex Property changed - Updating the UI")
            this.setCurrentViewStateFromComplexPropertyState(element.complexPropertyState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }
}
