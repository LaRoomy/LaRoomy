package com.example.laroomy

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.TextView

class LoadingActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        (applicationContext as ApplicationProperty).bluetoothConnectionManger.reAlignContextObjects(this, this@LoadingActivity, this)

        val index = this.intent.getIntExtra("BondedDeviceIndex", -1)
        if(index != -1){
            (applicationContext as ApplicationProperty).bluetoothConnectionManger.connectToBondedDeviceWithMacAddress(
                (applicationContext as ApplicationProperty).bluetoothConnectionManger.bondedLaRoomyDevices.elementAt(index).Address
            )
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

        // confirm or retrieve the device-properties...

    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)

        // if the device is connected -> check if this is the last connected device
        // if so -> load ui configuration and start confirmation process (if the authentication is successful!)
        // otherwise -> start property retrieving process (if the authentication is successful!)
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        setErrorText(message)

        // navigate back with delay??
    }

    override fun onDeviceReadyForCommunication() {
        super.onDeviceReadyForCommunication()

        runOnUiThread {
            setProgressText(getString(R.string.CA_Connected))


            Handler().postDelayed({
                (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.sendData(
                    (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.authenticationString
                )

                // to check: run in ui thread ??
                setProgressText(getString(R.string.CA_Authenticate))

            }, 1500)
        }
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        setErrorText(message)
    }
}
