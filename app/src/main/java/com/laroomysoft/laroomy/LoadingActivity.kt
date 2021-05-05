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
    private var blockAllFurtherProcessing = false
    private var connectionAttemptCounter = 0
    //private var authenticationAttemptCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@LoadingActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        when(val index = this.intent.getIntExtra("BondedDeviceIndex", -1)){
            -1 -> {
                // error state
                Log.e("M:LoadingAct::onCreate", "The given DeviceListIndex is -ErrorState- (-1)")

                // TODO: Notify user? Navigate back? With delay?

            }
            -2 -> {
                // connect to last device
                isLastConnectedDevice = true
                ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
                setProgressText(getString(R.string.CA_Connecting))
            }
            else -> {
                // connect to device from list at index
                val adr =
                    ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.elementAt(index).address

                if(adr == ApplicationProperty.bluetoothConnectionManager.getLastConnectedDeviceAddress()){
                    isLastConnectedDevice = true
                }

                ApplicationProperty.bluetoothConnectionManager.connectToBondedDeviceWithMacAddress(adr)
                setProgressText(getString(R.string.CA_Connecting))
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        ApplicationProperty.bluetoothConnectionManager.clear()
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
        runOnUiThread {
            val notificationTextView = findViewById<TextView>(R.id.LA_progressTextView)
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = text
        }
    }

    // Interface methods:
    override fun onAuthenticationSuccessful() {
        super.onAuthenticationSuccessful()
        // navigate to the next activity and retrieve the properties there:
        val intent =
            Intent(this@LoadingActivity, DeviceMainActivity::class.java)
        startActivity(intent)
        // finish this activity
        finish()
    }

    override fun onBindingPasskeyRejected() {
        super.onBindingPasskeyRejected()
        // binding was rejected from the remote device due to an invalid passkey
        setErrorText(getString(R.string.CA_BindingPasskeyRejected))
        // stop connection process
        ApplicationProperty.bluetoothConnectionManager.clear()
        // navigate back with delay !?
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 4000)
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:CB:ConStateChange", "Connection State changed in Loading Activity: new State: $state")
        if(!blockAllFurtherProcessing) {
            if (connectionAttemptCounter <= 4) {
                if (!state) {
                    Log.e(
                        "M:CB:ConStateChange",
                        "Disconnected in Loading Activity - try to reconnect - Attempt-Counter is: $connectionAttemptCounter"
                    )
                    // the device was disconnected, this should not happen in this activity, so try to reconnect 5 times
                    connectionAttemptCounter++
                    // clear the bluetoothManager
                    ApplicationProperty.bluetoothConnectionManager.clear()
                    // try to connect again with 1 sec delay
                    Handler(Looper.getMainLooper()).postDelayed({

                        // TODO: this is wrong, if the device is not reachable and wasn't the last device, another device will be connected


                        ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
                    }, 1000)
                }
            } else {
                // all attempts to connect failed -> go back to start (main activity)
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        Log.e("M:CB:ConAttemptFail", "Error - Connection Failed in Loading Activity")
        setErrorText(message)
        // navigate back with delay
        Handler(Looper.getMainLooper()).postDelayed({

            // TODO: check if this works!?

            blockAllFurtherProcessing = true
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()

        },3000)
    }

    override fun onDeviceReadyForCommunication() {
        super.onDeviceReadyForCommunication()
        Log.e("M:CB:onDRFC", "Loading Activity - Device ready for communication reported")
        // device ready -> send authentication
        runOnUiThread {
            setProgressText(getString(R.string.CA_Connected))
            // send authentication request
            Handler(Looper.getMainLooper()).postDelayed({
                ApplicationProperty.bluetoothConnectionManager.authenticate()

                // check the authentication with delay an try again if it is false
                Handler(Looper.getMainLooper()).postDelayed({

                    // TODO: maybe check here if the device is disconnected and try to reconnect

                    if(!ApplicationProperty.bluetoothConnectionManager.authenticationSuccess){
                        ApplicationProperty.bluetoothConnectionManager.authenticate()
                    }
                },1500)
                
                // notify User:
                setProgressText(getString(R.string.CA_Authenticate))
            }, 1200)
        }
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        setErrorText(message)
    }
}
