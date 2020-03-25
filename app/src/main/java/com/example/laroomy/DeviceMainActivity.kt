package com.example.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView

class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback {

    private var isUpToDate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@DeviceMainActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)
        //(this.applicationContext as ApplicationProperty).bluetoothConnectionManger.reAlignContextObjects(this, this@DeviceMainActivity, this)
        //(this.applicationContext as ApplicationProperty).bluetoothConnectionManger.setPropertyEventHandler(this)

        // init recycler view!!




    }

    override fun onPause() {
        super.onPause()
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        setUIConnectionStatus(ApplicationProperty.bluetoothConnectionManger.isConnected) //TODO: parameter is reset in new activity -> why???
        this.isUpToDate =
            ApplicationProperty.bluetoothConnectionManger.isPropertyUpToDate
        if(this.isUpToDate){
            this.adaptUIToPropertyListing()
        }
    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
    }

    private fun adaptUIToPropertyListing(){

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

    override fun onGroupDataRetrievalCompleted(groups: ArrayList<LaRoomyDevicePropertyGroup>) {
        super.onGroupDataRetrievalCompleted(groups)

        // this could be a race condition
        // what happens when the device has no groups or the groups are retrieved before the activity is loaded

        if(!this.isUpToDate){
            this.adaptUIToPropertyListing()
        }
    }
}
