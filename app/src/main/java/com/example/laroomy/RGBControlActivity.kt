package com.example.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorSelectedListener

class RGBControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, OnColorSelectedListener {

    lateinit var colorPickerView: ColorPickerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r_g_b_control)

        colorPickerView = findViewById(R.id.color_picker_view)
        colorPickerView.addOnColorSelectedListener(this)
        // TODO: set the current selected color to the view!
        // TODO: set the name of the property to the headerView

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@RGBControlActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

    }

    override fun onBackPressed() {
        super.onBackPressed()

        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
    }

    override fun onPause() {
        super.onPause()

        // the user closed the application -> suspend connection
        // set information parameter for onResume()
    }

    override fun onResume() {
        super.onResume()

        // reconnect to the device
    }

    fun onBackButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        finish()
    }

    fun onSwitchClick(view: View){

    }

    override fun onColorSelected(selectedColor: Int) {
        TODO("Not yet implemented")
    }

}
