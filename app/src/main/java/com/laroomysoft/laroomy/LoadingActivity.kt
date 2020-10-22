package com.laroomysoft.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView

class LoadingActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var isLastConnectedDevice = false
    private var connectionAttemptCounter = 0
    //private var authenticationAttemptCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@LoadingActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        when(val index = this.intent.getIntExtra("BondedDeviceIndex", -1)){
            -1 -> {
                // error state
                Log.e("M:LoadingAct::onCreate", "The given DeviceListIndex is -ErrorState- (-1)")

                // TODO: Notify user? Navigate back? With delay?

            }
            -2 -> {
                // connect to last device
                isLastConnectedDevice = true
                ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
                setProgressText(getString(R.string.CA_Connecting))
            }
            else -> {
                // connect to device from list at index
                val adr =
                    ApplicationProperty.bluetoothConnectionManger.bondedLaRoomyDevices.elementAt(index).address

                if(adr == ApplicationProperty.bluetoothConnectionManger.getLastConnectedDeviceAddress()){
                    isLastConnectedDevice = true
                }

                ApplicationProperty.bluetoothConnectionManger.connectToBondedDeviceWithMacAddress(adr)
                setProgressText(getString(R.string.CA_Connecting))
            }
        }


//        if(index != -1){
//
//            // TODO: make this in the property activity !!!!
//
//            val adr =
//                ApplicationProperty.bluetoothConnectionManger.bondedLaRoomyDevices.elementAt(index).address
//
//            if(adr == ApplicationProperty.bluetoothConnectionManger.getLastConnectedDeviceAddress()){
//                isLastConnectedDevice = true
//            }
//
//            ApplicationProperty.bluetoothConnectionManger.connectToBondedDeviceWithMacAddress(adr)
//            setProgressText(getString(R.string.CA_Connecting))
//        }
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

        //intent.flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        // finish this activity
        finish()
        // and finish first activity ??
        //MainActivity.main.finish()
    }

    override fun onBindingPasskeyRejected() {
        super.onBindingPasskeyRejected()
        // binding was rejected from the remote device due to an invalid passkey
        setErrorText(getString(R.string.CA_BindingPasskeyRejected))
        // stop connection process
        ApplicationProperty.bluetoothConnectionManger.clear()
        // navigate back with delay !?
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 4000)
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:CB:ConStateChange", "Connection State changed in Loading Activity: new State: $state")
        if(connectionAttemptCounter <= 4) {
            if (!state) {
                Log.e("M:CB:ConStateChange", "Disconnected in Loading Activity - try to reconnect - Attempt-Counter is: $connectionAttemptCounter")
                // the device was disconnected, this should not happen in this activity, so try to reconnect 5 times
                connectionAttemptCounter++
                // clear the bluetoothManager
                ApplicationProperty.bluetoothConnectionManger.clear()
                // try to connect again with 1 sec delay
                Handler(Looper.getMainLooper()).postDelayed({
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
        Log.e("M:CB:ConAttemptFail", "Error - Connection Failed in Loading Activity")
        setErrorText(message)
        // navigate back with delay
        Handler(Looper.getMainLooper()).postDelayed({
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
                ApplicationProperty.bluetoothConnectionManger.authenticate()
                //sendData(ApplicationProperty.bluetoothConnectionManger.authenticationString)

                // check the authentication with delay an try again if it is false
                Handler(Looper.getMainLooper()).postDelayed({

                    // TODO: maybe check here if the device is disconnected and try to reconnect

                    if(!ApplicationProperty.bluetoothConnectionManger.authenticationSuccess){
                        //authenticationAttemptCounter++
                        ApplicationProperty.bluetoothConnectionManger.authenticate()
                        //sendData(ApplicationProperty.bluetoothConnectionManger.authenticationString)
                    }
                },1500)
                
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
