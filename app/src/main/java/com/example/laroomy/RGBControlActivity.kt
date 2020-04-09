package com.example.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class RGBControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r_g_b_control)

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
}
