package com.laroomysoft.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import java.util.*

class LoadingActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var isLastConnectedDevice = false
    private var blockAllFurtherProcessing = false
    private var previouslyConnected = false
    private var timeOut = false
    private var timeOutHandlerStarted = false
    private var connectionAttemptCounter = 0
    private var macAddressToConnect = ""
    private var curDeviceListIndex = -5
    //private var authenticationAttemptCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@LoadingActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // if logging is enabled set the appropriate data
        if((applicationContext as ApplicationProperty).eventLogEnabled){

            // clear the log array
            (applicationContext as ApplicationProperty).connectionLog.clear()

            // get date and time
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)// 24 hour format!
            val min = calendar.get(Calendar.MINUTE)
            val sec = calendar.get(Calendar.SECOND)
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

            // set the time-stamp
            (applicationContext as ApplicationProperty).logRecordingTime = "Rec:Time: $hour:$min:$sec - $dayOfMonth/$month/$year"
        }

        this.curDeviceListIndex = this.intent.getIntExtra("BondedDeviceIndex", -1)

        when(this.curDeviceListIndex){
            -1 -> {
                // error state
                Log.e("M:LoadingAct::onCreate", "The given DeviceListIndex is -ErrorState- (-1)")
                (applicationContext as ApplicationProperty).logControl("E: Device-List-Index invalid.")

                // TODO: Notify user? Navigate back? With delay?

            }
            -2 -> {
                // cache the address to connect again if an unexpected disconnect event occurs
                this.macAddressToConnect = (applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)

                // connect to last device
                isLastConnectedDevice = true
                ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
                setProgressText(getString(R.string.CA_Connecting))
            }
            else -> {
                // connect to device from list at index
                val adr =
                    ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.elementAt(this.curDeviceListIndex).address

                // cache the address to connect again if an unexpected disconnect event occurs
                this.macAddressToConnect = adr

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

        // in auto-connect-mode the parameter "curDeviceListIndex" is -2, this is an invalid index and not suitable to get the image of the device
        // so get the image for the device
        var image = -1

        if(curDeviceListIndex < 0){
            // the list index is invalid, must be an auto-connect procedure
                // generate lookup for the address in the bonded device list:
            ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.forEach {
                if(it.address == ApplicationProperty.bluetoothConnectionManager.currentDevice?.address){
                    image = it.image
                    return@forEach
                }
            }
            if(image == -1){
                // image was not found, set a placeholder image
                image = if(isLaroomyDevice(ApplicationProperty.bluetoothConnectionManager.currentDevice?.name ?: "Name")){
                    // laroomy device
                    R.drawable.laroomy_icon_sq64
                } else {
                    // any device
                    R.drawable.bluetooth_green_glow_sq64
                }
            }
        } else {
            image = ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.elementAt(this.curDeviceListIndex).image
        }

        intent.putExtra("BondedDeviceImageResourceId", image)
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
        // navigate back with delay
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 4000)
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "M:CB:ConStateChange",
                "Connection State changed in Loading Activity: new State: $state"
            )
        }
        (applicationContext as ApplicationProperty).logControl("I: Connection-State changed to: $state")

        if(!blockAllFurtherProcessing) {
            if (connectionAttemptCounter <= 4) {
                if (!state) {
                    if (!previouslyConnected) {
                        // unexpected disconnected event, the system says disconnected, but the device was not previously connected
                        // set up a timeout-handler and try to connect again
                        if (!timeOut) {
                            ApplicationProperty.bluetoothConnectionManager.clear()
                            ApplicationProperty.bluetoothConnectionManager.connectToBondedDeviceWithMacAddress(
                                this.macAddressToConnect
                            )
                            if (!timeOutHandlerStarted) {
                                // make sure this will only be executed once:
                                timeOutHandlerStarted = true
                                Handler(Looper.getMainLooper()).postDelayed({
                                    timeOut = true
                                }, 5000) // timeout 5 seconds!
                            }
                        } else {
                            // must be a timeout, the system says disconnected, but the device was not previously connected
                            Log.e(
                                "M:CB:ConStateChange",
                                "Timeout for the connection-attempt. Device may be not reachable. Stop connection-process."
                            )
                            (applicationContext as ApplicationProperty).logControl("E: Timeout for connection. Device may be not reachable. Stop process.")
                            ApplicationProperty.bluetoothConnectionManager.clear()
                            // notify user
                            setMessageText(
                                R.color.WarningColor,
                                getString(R.string.CA_TimeoutForConnection)
                            )
                            // navigate back with delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                finish()
                            }, 1500)
                        }
                    } else {
                        Log.e(
                            "M:CB:ConStateChange",
                            "Disconnected in Loading Activity - try to reconnect - Attempt-Counter is: $connectionAttemptCounter"
                        )
                        (applicationContext as ApplicationProperty).logControl("W: Disconnected in LoadingActivity - try to reconnect")
                        // the device was disconnected, this should not happen in this activity, so try to reconnect 5 times
                        connectionAttemptCounter++
                        // clear the bluetoothManager
                        ApplicationProperty.bluetoothConnectionManager.clear()
                        // try to connect again with 1 sec delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            ApplicationProperty.bluetoothConnectionManager.connectToBondedDeviceWithMacAddress(this.macAddressToConnect)
                            // TODO: check this!
                            //ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
                        }, 1000)
                    }
                } else {
                    previouslyConnected = true
                }
            } else {
                // all attempts to connect failed -> go back to start (main activity)
                (applicationContext as ApplicationProperty).logControl("E: Connection failed. Navigate back to main.")
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)

        Log.e("M:CB:ConAttemptFail", "Error - Connection Failed in Loading Activity")
        (applicationContext as ApplicationProperty).logControl("E: Failed to connect in LoadingActivity")

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

        if(verboseLog) {
            Log.d("M:CB:onDRFC", "Loading Activity - Device ready for communication reported")
        }
        (applicationContext as ApplicationProperty).logControl("I: Device ready for communication - sending authentication string")

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
