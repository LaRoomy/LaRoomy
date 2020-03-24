package com.example.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView

class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.reAlignContextObjects(this, this@DeviceMainActivity, this)
        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.setPropertyEventHandler(this)


    }

    override fun onPause() {
        super.onPause()
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        setUIConnectionStatus((this.applicationContext as ApplicationProperty).bluetoothConnectionManger.connectionStatus)
    }

    fun onDiscardDeviceButtonClick(view: View){

    }

    private fun setUIConnectionStatus(status :Boolean){

        val statusText =
            findViewById<TextView>(R.id.deviceConnectionStatusTextView)

        if(status){
            statusText.setTextColor(getColor(R.color.connectedTextColor))
            statusText.text = getString(R.string.DMA_ConnectionStatus_connected)
        }
        else{
            statusText.setTextColor(getColor(R.color.disconnectedTextColor))
            statusText.text = getString(R.string.DMA_ConnectionStatus_disconnected)
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        this.setUIConnectionStatus(state)
    }
}
