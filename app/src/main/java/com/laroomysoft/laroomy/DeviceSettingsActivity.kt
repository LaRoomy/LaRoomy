package com.laroomysoft.laroomy

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat

class DeviceSettingsActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    lateinit var userNotificationTextView: AppCompatTextView
    lateinit var bindingSwitch: SwitchCompat
    lateinit var factoryResetButton: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_settings)

        // align context and event objects
        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@DeviceSettingsActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the necessary views
        userNotificationTextView = findViewById(R.id.deviceSettingsActivityUserNotificationTextView)
        bindingSwitch = findViewById(R.id.deviceSettingsActivityBindingSwitch)
        factoryResetButton = findViewById(R.id.deviceSettingsActivityFactoryResetButton)

        // set the initial settings
        bindingSwitch.isChecked = ApplicationProperty.bluetoothConnectionManager.isBindingRequired

        // set the connection-state info
        when(ApplicationProperty.bluetoothConnectionManager.isConnected){
            true -> notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Connected), R.color.connectedTextColor)
            else -> notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Disconnected), R.color.disconnectedTextColor)
        }

        // set up event-handler for controls
        bindingSwitch.setOnCheckedChangeListener { _, isChecked ->

            // create/release device binding

            when(isChecked){
                true -> {
                    // enable the device binding
                    val passkey =
                        (applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_DefaultRandomBindingPasskey)

                    //ApplicationProperty.bluetoothConnectionManger.sendData("SeB§$passkey$")
                    ApplicationProperty.bluetoothConnectionManager.enableDeviceBinding(passkey)
                }
                else -> {
                    // release the device binding
                    //ApplicationProperty.bluetoothConnectionManger.sendData("SrB>$")
                    ApplicationProperty.bluetoothConnectionManager.releaseDeviceBinding()
                }
            }

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
            ApplicationProperty.bluetoothConnectionManager.close()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:DSPPage:onResume", "onResume executed in Device Settings Activity")

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@DeviceSettingsActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            Log.d("M:DSPPage:onResume", "The connection was suspended -> try to reconnect")
            ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            userNotificationTextView.setTextColor(getColor(colorID))
            userNotificationTextView.text = message
        }
    }

    private fun setUIElementsEnabledState(state: Boolean){
        runOnUiThread{
            if(state){
                bindingSwitch.isEnabled = true
                factoryResetButton.isEnabled = true
            } else{
                bindingSwitch.isEnabled = false
                factoryResetButton.isEnabled = false
            }
        }
    }

    fun onFactoryResetButtonClick(@Suppress("UNUSED_PARAMETER") view: View){
        factoryResetAlertDialog()
    }

    private fun factoryResetAlertDialog(){
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(R.string.DeviceSettingsActivity_FactoryResetConfirmationMessage)
        dialog.setTitle(R.string.DeviceSettingsActivity_FactoryResetButtonDescriptorText)
        dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
            // send factory reset command
            ApplicationProperty.bluetoothConnectionManager.sendData("SfR=$")
            dialogInterface.dismiss()
        }
        dialog.setNegativeButton(R.string.GeneralString_Cancel) { dialogInterface: DialogInterface, _: Int ->
            // cancel action
            dialogInterface.dismiss()
        }
        dialog.create()
        dialog.show()
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:DSPPage:ConStateChge", "Connection state changed in Device Settings Activity. New Connection state is: $state")
        if(state){
            notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Connected), R.color.connectedTextColor)
            setUIElementsEnabledState(state)
        } else {
            notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Disconnected), R.color.disconnectedTextColor)
            setUIElementsEnabledState(state)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        Log.e("M:DSPPage:onConnFailed", "Connection Attempt failed in Device Settings Activity")
        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
    }

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
        notifyUser(deviceHeaderData.message, R.color.InfoColor)
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