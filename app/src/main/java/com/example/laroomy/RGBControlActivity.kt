package com.example.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Switch
import android.widget.TextView
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorSelectedListener

class RGBControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, OnColorSelectedListener {

    lateinit var colorPickerView: ColorPickerView
    var mustReconnect = false
    var relatedElementID = -1
    var relatedGlobalElementIndex = -1

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

//        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@RGBControlActivity, this)
//        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        findViewById<Switch>(R.id.rgbSwitch).setOnClickListener{
            onSwitchClick(it)
            Log.d("ONSWITCHCLICK", "On switch click executed")
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

    fun onBackButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        //finish()
    }

    fun onSwitchClick(view: View){


        // only for testing switch!
        // TODO: check if the new state confirms with the state in the callback

        runOnUiThread {

            val state = (view as Switch).isChecked

            if (state) {
                notifyUser("Checked", R.color.InfoColor)
            } else {
                notifyUser("Unchecked", R.color.InfoColor)
            }
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
        // temporary hex color display
        runOnUiThread {
            notifyUserWithColorAsInt(
                "${getString(R.string.RGBPageColorSelectionInformation)} ${Integer.toHexString(
                    selectedColor
                )}", selectedColor
            )
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
