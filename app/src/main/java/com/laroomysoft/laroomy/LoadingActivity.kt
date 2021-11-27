package com.laroomysoft.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import java.lang.IndexOutOfBoundsException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class LoadingActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var isLastConnectedDevice = false
    private var blockAllFurtherProcessing = false
    private var previouslyConnected = false
    private var timeOut = false
    private var timeOutHandlerStarted = false
    private var connectionAttemptCounter = 0
    private var macAddressToConnect = ""
    private var curDeviceListIndex = -5
    private var propertyLoadingStarted = false
    private var connectionMustResetDueToOnPauseExecution = false
    //private var authenticationAttemptCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
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
            val month = calendar.get(Calendar.MONTH) + 1 // (January is 0, so this is a index, so add one to fit)
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

            // set the time-stamp
            (applicationContext as ApplicationProperty).logRecordingTime = "Rec:Time: $hour:$min:$sec - $dayOfMonth/$month/$year"
        }

        this.curDeviceListIndex = this.intent.getIntExtra("DeviceListIndex", -1)

        when(this.curDeviceListIndex){
            -1 -> {
                // error state
                Log.e("M:LoadingAct::onCreate", "The given DeviceListIndex is -ErrorState- (-1)")
                (applicationContext as ApplicationProperty).logControl("E: Device-List-Index invalid.")
                // notify user
                this.setMessageText(
                    R.color.errorLightColor,
                    getString(R.string.CA_ErrorDeviceListIndexInvalid)
                )
                // navigate back with delay
                Executors.newSingleThreadScheduledExecutor().schedule({
                    ApplicationProperty.bluetoothConnectionManager.clear()
                    finish()
                }, 5000, TimeUnit.MILLISECONDS)
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
                try {
                    // connect to device from list at index
                    val adr =
                        (applicationContext as ApplicationProperty).addedDevices.devices.elementAt(
                            this.curDeviceListIndex
                        ).macAddress

                    // cache the address to connect again if an unexpected disconnect event occurs
                    this.macAddressToConnect = adr

                    if (adr == ApplicationProperty.bluetoothConnectionManager.getLastConnectedDeviceAddress()) {
                        isLastConnectedDevice = true
                    }

                    ApplicationProperty.bluetoothConnectionManager.connectToBondedDeviceWithMacAddress(
                        adr
                    )
                    setProgressText(
                        getString(R.string.CA_Connecting)
                    )
                } catch (e: IndexOutOfBoundsException){
                    Log.e("M:E:onCreate", "IndexOutOfBoundsException in LoadingActivity in onCreate")
                    Log.e("M:E:onCreate", "Index was: ${this.curDeviceListIndex} / bonded-list-size: ${ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.size}")
                    (applicationContext as ApplicationProperty).logControl("E: Critical Error, Exception occurred while accessing the device-address: $e")
                    setProgressText(
                        getString(R.string.CA_ErrorDeviceAddressAccessException)
                    )
                    Executors.newSingleThreadScheduledExecutor().schedule({
                        ApplicationProperty.bluetoothConnectionManager.clear()
                        finish()
                    }, 3000, TimeUnit.MILLISECONDS)
                }
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
        if(verboseLog){
            Log.w("LA:onPause", "OnPause executed in Loading Activity -> stop connection process")
        }
        // clear the connection process and start over on resume
        ApplicationProperty.bluetoothConnectionManager.disconnect()
        this.connectionMustResetDueToOnPauseExecution = true
    }

    override fun onResume() {
        super.onResume()
        if(verboseLog){
            Log.w("LA:onResume", "OnResume executed in Loading Activity -> restart connection process")
        }
        if(this.connectionMustResetDueToOnPauseExecution) {
            // start over with the connection process
            ApplicationProperty.bluetoothConnectionManager.clear()
            ApplicationProperty.bluetoothConnectionManager.connectToBondedDeviceWithMacAddress(
                this.macAddressToConnect
            )
            this.connectionMustResetDueToOnPauseExecution = false
        }
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
    override fun onInitializationSuccessful() {
        super.onInitializationSuccessful()
        if(verboseLog){
            Log.d("LA:onInitSuccess", "Initialization successful in Loading Activity, start property listing")
        }
        this.setProgressText(
            getString(R.string.CA_LoadingProperties)
        )
        this.propertyLoadingStarted = true
        ApplicationProperty.bluetoothConnectionManager.startPropertyListing()



        // navigate to the next activity and retrieve the properties there:


//        val intent =
//            Intent(this@LoadingActivity, DeviceMainActivity::class.java)

        // in auto-connect-mode the parameter "curDeviceListIndex" is -2, this is an invalid index and not suitable to get the image of the device
        // so get the image for the device
        //var image = -1

/*
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
            image = try {
                ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.elementAt(
                    this.curDeviceListIndex
                ).image
            } catch (e: IndexOutOfBoundsException){
                Log.e("M:E:Auth:Success", "IndexOutOfBoundsException in LoadingActivity in callback-method: onAuthenticationSuccessful")
                Log.e("M:E:Auth:Success", "ExceptionMessage: ${e.message}")
                Log.e("M:E:Auth:Success", "Current index was ${this.curDeviceListIndex} / size of bonded-list: ${ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices.size}")
                R.drawable.bluetooth_green_glow_sq64
            }
        }
*/

        //intent.putExtra("BondedDeviceImageResourceId", image)
        //startActivity(intent)
        // finish this activity
        //finish()
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

    override fun onDeviceReadyForCommunication() {
        super.onDeviceReadyForCommunication()

        if(verboseLog) {
            Log.d("M:CB:onDRFC", "Loading Activity - Device ready for communication reported")
        }
        (applicationContext as ApplicationProperty).logControl("I: Device ready for communication - sending authentication string")

        // device ready -> send authentication
        runOnUiThread {
            setProgressText(getString(R.string.CA_Connected))
            // send init request
            Handler(Looper.getMainLooper()).postDelayed({
                ApplicationProperty.bluetoothConnectionManager.initDeviceTransmission()

                // check the init with delay an try again if it is false
                Handler(Looper.getMainLooper()).postDelayed({

                    // TODO: maybe check here if the device is disconnected and try to reconnect

                    if(!ApplicationProperty.bluetoothConnectionManager.initializationSuccess){
                        ApplicationProperty.bluetoothConnectionManager.initDeviceTransmission()
                    }
                },2000)// FIXME: what is the right time interval??
                
                // notify User:
                setProgressText(getString(R.string.CA_Initialize))
            }, 1500) // FIXME: what is the right time interval??
        }
    }

    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)

        val errorMessage = when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                getString(R.string.Error_ResumeFailedDeviceNotReachable)
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                getString(R.string.Error_ResumeFailedNoDevice)
            }
            BLE_UNEXPECTED_CRITICAL_BINDING_KEY_MISSING -> {
                getString(R.string.Error_UnexpectedBindingKeyMissing)
            }
            else -> {
                getString(R.string.Error_UnknownError)
            }
        }
        this.setErrorText(errorMessage)
        Log.e("LA:onConError", "Error occurred in Loading Activity. User message: $errorMessage")
        (applicationContext as ApplicationProperty).logControl("E: $errorMessage")

        Executors.newSingleThreadScheduledExecutor().schedule({
            blockAllFurtherProcessing = true
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        }, 3000, TimeUnit.MILLISECONDS)
    }

    override fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>) {
        super.onUIAdaptableArrayListGenerationComplete(UIArray)

        val intent =
            Intent(this@LoadingActivity, DeviceMainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
