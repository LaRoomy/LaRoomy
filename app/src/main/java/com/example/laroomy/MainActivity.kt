package com.example.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class MainActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //(this.applicationContext as ApplicationProperty).bluetoothConnectionManger.reAlignContextObjects(this, )
    }

    override fun onStart() {
        super.onStart()
        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.checkBluetoothEnabled()
    }

/*
    fun onClick(view: View){

        val intent = Intent(this@MainActivity, LoadingActivity::class.java)
        // intent.putExtra(...)
        startActivity(intent)
    }
*/

    override fun onAuthenticationSuccessful() {
        TODO("Not yet implemented")
    }

    override fun onComponentError(message: String) {
        TODO("Not yet implemented")
    }

/*
    override fun onConnectionAttemptFailed(message: String) {
        TODO("Not yet implemented")
    }

    override fun onConnectionStateChanged(state: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onDataReceived(data: String?) {
        TODO("Not yet implemented")
    }

    override fun onDataSent(data: String?) {
        TODO("Not yet implemented")
    }

    override fun onDeviceReadyForCommunication() {
        TODO("Not yet implemented")
    }
*/
}
