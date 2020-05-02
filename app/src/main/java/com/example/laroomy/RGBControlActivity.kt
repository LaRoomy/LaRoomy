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
import com.flask.colorpicker.OnColorSelectedListener

const val RGB_MODE_OFF = 0
const val RGB_MODE_SINGLE_COLOR = 1
const val RGB_MODE_TRANSITION = 2

class RGBControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, OnColorSelectedListener, SeekBar.OnSeekBarChangeListener {

    lateinit var colorPickerView: ColorPickerView
    var mustReconnect = false
    var relatedElementID = -1
    var relatedGlobalElementIndex = -1
    var currentColor = Color.WHITE
    var currentProgram = 12
    var currentMode = RGB_MODE_SINGLE_COLOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r_g_b_control)

        colorPickerView = findViewById(R.id.color_picker_view)
        colorPickerView.addOnColorSelectedListener(this)
        // TODO: set the current selected color to the view!
        // TODO: set the name of the property to the headerView??

        // TODO: get the element ID extra and set the state to the property-state
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        findViewById<TextView>(R.id.rgbHeaderTextView).text =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

//        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@RGBControlActivity, this)
//        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // set program-speed seekBar changeListener
        findViewById<SeekBar>(R.id.rgbProgramSpeedSeekBar).setOnSeekBarChangeListener(this)

        // set on/off Switch-state changeListener
        findViewById<Switch>(R.id.rgbSwitch).setOnClickListener{
            Log.d("M:RGB:Switch:onClick", "On / Off Switch was clicked. New state is: ${(it as Switch).isChecked}")
            onMainOnOffSwitchClick(it)
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

    private fun setCurrentSingleColor(){
        this.sendInstruction(8, Color.red(this.currentColor), Color.green(this.currentColor), Color.blue(this.currentColor))
    }

    private fun setCurrentProgram(){
        this.sendInstruction(this.currentProgram, 0, 0,0)
    }

    private fun setAllOff(){
        this.sendInstruction(0,0,0,0)
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

    override fun onColorSelected(selectedColor: Int) {
        Log.d("M:RGBPage:onColorSelect","New color selected in RGBControlActivity. New Color: ${Integer.toHexString(selectedColor)}")

        this.currentColor = selectedColor

        // temporary hex color display
        runOnUiThread {
            notifyUserWithColorAsInt(
                "${getString(R.string.RGBPageColorSelectionInformation)} ${Integer.toHexString(
                    selectedColor
                )}", selectedColor
            )
        }

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

        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
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


    }
}
