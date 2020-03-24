package com.example.laroomy

import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.TextView

class LoadingActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var isLastConnectedDevice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        (applicationContext as ApplicationProperty).bluetoothConnectionManger.reAlignContextObjects(this, this@LoadingActivity, this)
        (applicationContext as ApplicationProperty).bluetoothConnectionManger.setPropertyEventHandler(this)

        val index = this.intent.getIntExtra("BondedDeviceIndex", -1)
        if(index != -1){

            val adr =
                (applicationContext as ApplicationProperty).bluetoothConnectionManger.bondedLaRoomyDevices.elementAt(index).Address

            if(adr == (applicationContext as ApplicationProperty).bluetoothConnectionManger.getLastConnectedDeviceAddress()){
                isLastConnectedDevice = true
            }

            (applicationContext as ApplicationProperty).bluetoothConnectionManger.connectToBondedDeviceWithMacAddress(adr)
            setProgressText(getString(R.string.CA_Connecting))
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.clear()
        finish()
    }

    override fun onPause() {
        super.onPause()
        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.clear()
        finish()
    }

    private fun setProgressText(text: String){
        // maybe include a drawable..? and a text-color ?

//        val notificationTextView = findViewById<TextView>(R.id.LA_progressTextView)
//        notificationTextView.setTextColor(getColor(R.color.InfoColor))
//        notificationTextView.text = text

        this.setMessageText(R.color.InfoColor, text)
    }

    private fun setErrorText(text: String){
        setMessageText(R.color.ErrorColor, text)
    }

    private fun setMessageText(colorID: Int, text: String){
        val notificationTextView = findViewById<TextView>(R.id.LA_progressTextView)
        notificationTextView.setTextColor(getColor(colorID))
        notificationTextView.text = text
    }

    // Interface methods:
    override fun onAuthenticationSuccessful() {
        super.onAuthenticationSuccessful()

        this.setMessageText(R.color.InfoColor, getString(R.string.CA_AuthSuccess))

        // confirm or retrieve the device-properties...

        if(this.isLastConnectedDevice && ((applicationContext as ApplicationProperty).bluetoothConnectionManger.laRoomyDevicePropertyList.size > 0)){
            (applicationContext as ApplicationProperty).bluetoothConnectionManger.startDevicePropertyListing()
        }
        else{
            (applicationContext as ApplicationProperty).bluetoothConnectionManger.startPropertyConfirmationProcess()
        }
    }

/*
    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)

        // if the device is connected -> check if this is the last connected device
        // if so -> load ui configuration and start confirmation process (if the authentication is successful!)
        // otherwise -> start property retrieving process (if the authentication is successful!)
    }
*/

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        setErrorText(message)

        // navigate back with delay
        Handler().postDelayed({
            // TODO: check if this works
            finish()
        },2000)
    }

    override fun onDeviceReadyForCommunication() {
        super.onDeviceReadyForCommunication()

        runOnUiThread {
            setProgressText(getString(R.string.CA_Connected))

            Handler().postDelayed({
                (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.sendData(
                    (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.authenticationString
                )
                setProgressText(getString(R.string.CA_Authenticate))
            }, 1500)
        }
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        setErrorText(message)
    }

    override fun onPropertyDataRetrievalCompleted(properties: ArrayList<LaRoomyDeviceProperty>) {
        super.onPropertyDataRetrievalCompleted(properties)

        val intent =
            Intent(this@LoadingActivity, DeviceMainActivity::class.java)
        startActivity(intent)
    }

}
