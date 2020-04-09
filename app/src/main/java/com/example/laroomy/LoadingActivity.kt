package com.example.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

class LoadingActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var isLastConnectedDevice = false
    private var connectionAttemptCounter = 0
    //private var authenticationAttemtCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@LoadingActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        val index = this.intent.getIntExtra("BondedDeviceIndex", -1)
        if(index != -1){

            // TODO: make this in the property activity !!!!

            val adr =
                ApplicationProperty.bluetoothConnectionManger.bondedLaRoomyDevices.elementAt(index).address

            if(adr == ApplicationProperty.bluetoothConnectionManger.getLastConnectedDeviceAddress()){
                isLastConnectedDevice = true
            }

            ApplicationProperty.bluetoothConnectionManger.connectToBondedDeviceWithMacAddress(adr)
            setProgressText(getString(R.string.CA_Connecting))
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        ApplicationProperty.bluetoothConnectionManger.clear()
        finish()
    }

    override fun onPause() {
        super.onPause()
        //ApplicationProperty.bluetoothConnectionManger.clear() this is fucking wrong!!
        //finish()
    }

    private fun setProgressText(text: String){
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
/*

        this.runOnUiThread {
            this.setMessageText(R.color.InfoColor, getString(R.string.CA_AuthSuccess))
        }
*/

        // navigate to the next activity and retrieve the properties there:
        val intent =
            Intent(this@LoadingActivity, DeviceMainActivity::class.java)
        startActivity(intent)
        // finish this activity
        finish()
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)

        if(connectionAttemptCounter <= 4) {
            if (!state) {
                // the device was disconnected, this should not happen in this activity, so try to reconnect 5 times
                connectionAttemptCounter++
                // clear the bluetoothManager
                ApplicationProperty.bluetoothConnectionManger.clear()
                // try to connect again with 1 sec delay
                Handler().postDelayed({
                    ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
                }, 1000)
            }
        } else {
            // all attempts to connect failed -> go back to start (main activity)
            ApplicationProperty.bluetoothConnectionManger.clear()
            finish()
        }
    }

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
        // device ready -> send authentication
        runOnUiThread {
            setProgressText(getString(R.string.CA_Connected))
            // send authentication request
            Handler().postDelayed({
                ApplicationProperty.bluetoothConnectionManger.sendData(
                    ApplicationProperty.bluetoothConnectionManger.authenticationString)

                // check the authentication with delay an try again if it is false
                Handler(Looper.getMainLooper()).postDelayed({
                    if(!ApplicationProperty.bluetoothConnectionManger.authenticationSuccess){
                        //authenticationAttemptCounter++
                        ApplicationProperty.bluetoothConnectionManger.sendData(
                            ApplicationProperty.bluetoothConnectionManger.authenticationString)
                    }
                },1000)
                
                // notify User:
                setProgressText(getString(R.string.CA_Authenticate))
            }, 1500)
        }
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        setErrorText(message)
    }
}
