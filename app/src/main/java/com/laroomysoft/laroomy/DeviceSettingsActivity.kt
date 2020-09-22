package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat

class DeviceSettingsActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    lateinit var userNotificationTextView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_settings)

        // align context and event objects
        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceSettingsActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // get the necessary views
        userNotificationTextView = findViewById(R.id.deviceSettingsActivityUserNotificationTextView)

        // set the connection-state info
        when(ApplicationProperty.bluetoothConnectionManger.isConnected){
            true -> notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Connected), R.color.connectedTextColor)
            else -> notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Disconnected), R.color.disconnectedTextColor)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // handle the back-navigation like a property-sub-page, because the device should remain connected during this procedure
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        // close
        finish()
    }

    override fun onPause() {
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            Log.d("M:DSPPage:onPause", "Device Settings Activity: The user closes the app -> suspend connection")
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManger.close()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:DSPPage:onResume", "onResume executed in Device Settings Activity")

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceSettingsActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            Log.d("M:DSPPage:onResume", "The connection was suspended -> try to reconnect")
            ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        userNotificationTextView.setTextColor(getColor(colorID))
        userNotificationTextView.text = message
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:DSPPage:ConStateChge", "Connection state changed in Device Settings Activity. New Connection state is: $state")
        if(state){
            notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Connected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Disconnected), R.color.disconnectedTextColor)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        Log.e("M:DSPPage:onConnFailed", "Connection Attempt failed in Device Settings Activity")
        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }
}