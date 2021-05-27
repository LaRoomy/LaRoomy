package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout

class DeviceSettingsActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private lateinit var userNotificationTextView: AppCompatTextView
    private lateinit var bindingSwitch: SwitchCompat
    private lateinit var factoryResetButton: AppCompatButton
    private lateinit var bindingHintTextView: AppCompatTextView
    private lateinit var shareBindingContainer: ConstraintLayout
    private lateinit var deviceNameTextView: AppCompatTextView
    private lateinit var deviceAddressTextView: AppCompatTextView

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
        bindingHintTextView = findViewById(R.id.deviceSettingsActivityBindingHintTextView)
        shareBindingContainer = findViewById(R.id.deviceSettingsActivityShareBindingContainer)
        deviceNameTextView = findViewById(R.id.deviceSettingsActivityDeviceInfoNameTextView)
        deviceAddressTextView = findViewById(R.id.deviceSettingsActivityDeviceMACAddressNameTextView)

        // set device info
        deviceNameTextView.text = ApplicationProperty.bluetoothConnectionManager.currentDevice?.name
        deviceAddressTextView.text = ApplicationProperty.bluetoothConnectionManager.currentDevice?.address

        // set the initial settings
        bindingSwitch.isChecked = ApplicationProperty.bluetoothConnectionManager.isBindingRequired

        // change the UI if the binding-switch is on
        if(ApplicationProperty.bluetoothConnectionManager.isBindingRequired){
            bindingHintTextView.text = getString(R.string.DeviceSettingsActivity_BindingPurposeHintForDisable)
            shareBindingContainer.visibility = View.VISIBLE

            if(ApplicationProperty.bluetoothConnectionManager.isCurrentConnectionDoneWithSharedBindingKey){
                // the current connection is established with a shared binding key
                // -> no sharing is possible, only the origin can share the binding, so disable the button and notify the user
                findViewById<AppCompatImageButton>(R.id.deviceSettingsActivityShareButton).isEnabled = false
                findViewById<AppCompatTextView>(R.id.deviceSettingsActivityShareBindingHint).apply {
                    setTextColor(getColor(R.color.errorLightColor))
                    text = getString(R.string.DeviceSettingsActivity_OnlyTheOriginCanShareTheBindingNotification)
                }
                bindingSwitch.isEnabled = false
                bindingHintTextView.setTextColor(getColor(R.color.errorLightColor))
                bindingHintTextView.text = getString(R.string.DeviceSettingsActivity_OnlyTheOriginCanReleaseTheBinding)
            }
        }

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
                        // select default or custom key in relation to the appropriate setting
                        when((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_UseCustomBindingKey)) {
                            true -> {
                                (applicationContext as ApplicationProperty).loadSavedStringData(
                                    R.string.FileKey_AppSettings,
                                    R.string.DataKey_CustomBindingPasskey
                                )
                            }
                            false -> {
                                (applicationContext as ApplicationProperty).loadSavedStringData(
                                    R.string.FileKey_AppSettings,
                                    R.string.DataKey_DefaultRandomBindingPasskey
                                )
                            }
                        }
                    //ApplicationProperty.bluetoothConnectionManger.sendData("SeB§$passkey$")
                    ApplicationProperty.bluetoothConnectionManager.enableDeviceBinding(passkey)

                    // update the hint for the user
                    bindingHintTextView.text = getString(R.string.DeviceSettingsActivity_BindingPurposeHintForDisable)

                    // show the share-button
                    shareBindingContainer.visibility = View.VISIBLE
                }
                else -> {
                    // release the device binding
                    //ApplicationProperty.bluetoothConnectionManger.sendData("SrB>$")
                    ApplicationProperty.bluetoothConnectionManager.releaseDeviceBinding()

                    // update the hint for the user
                    bindingHintTextView.text = getString(R.string.DeviceSettingsActivity_BindingPurposeHintForEnable)

                    // hide the share-button
                    shareBindingContainer.visibility = View.GONE
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
            if(verboseLog) {
                Log.d(
                    "M:DSPPage:onPause",
                    "Device Settings Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()
        if(verboseLog) {
            Log.d("M:DSPPage:onResume", "onResume executed in Device Settings Activity")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@DeviceSettingsActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:DSPPage:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            userNotificationTextView.setTextColor(getColor(colorID))
            userNotificationTextView.text = message
        }
    }

    private fun notifyUserWithDelayedReset(message: String, colorID: Int){
        notifyUser(message, colorID)
        Handler(Looper.getMainLooper()).postDelayed({
            if(ApplicationProperty.bluetoothConnectionManager.isConnected){
                notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Connected), R.color.connectedTextColor)
            } else {
                notifyUser(getString(R.string.DeviceSettingsActivity_UserInfo_Disconnected), R.color.disconnectedTextColor)
            }
        }, 4000)
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
        if(verboseLog) {
            Log.d(
                "M:DSPPage:ConStateChge",
                "Connection state changed in Device Settings Activity. New Connection state is: $state"
            )
        }
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

        Log.e("M:DSPPage:onConnFailed", "Connection Attempt failed in Device Settings Activity. Message: $message")
        (applicationContext as ApplicationProperty).logControl("E: Failed to connect in DeviceSettingsActivity. Reason: $message")

        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        // if there is a connection failure -> navigate back
        when(message){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
            }
        }
    }

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
        notifyUserWithDelayedReset(deviceHeaderData.message, R.color.InfoColor)
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

    override fun onDeviceNotification(notificationID: Int) {
        super.onDeviceNotification(notificationID)
        // look for a rejected/error/success setting notification
        when(notificationID){
            DEVICE_NOTIFICATION_BINDING_NOT_SUPPORTED -> {
                // update the hint for the user
                bindingHintTextView.text = getString(R.string.DeviceSettingsActivity_BindingPurposeHintForEnable)

                // hide the share-button
                shareBindingContainer.visibility = View.GONE

                // reset the switch
                bindingSwitch.isChecked = false

                // notify user
                notifyUserWithDelayedReset(getString(R.string.DeviceSettingsActivity_BindingNotSupportedNotification), R.color.WarningColor)
            }
            DEVICE_NOTIFICATION_BINDING_SUCCESS -> {
                //notify user
                notifyUserWithDelayedReset(getString(R.string.DeviceSettingsActivity_BindingSuccessNotification), R.color.successLightColor)
            }
            DEVICE_NOTIFICATION_BINDING_ERROR -> {
                // update the hint for the user
                bindingHintTextView.text = getString(R.string.DeviceSettingsActivity_BindingPurposeHintForEnable)
                // reset the switch
                bindingSwitch.isChecked = false
                //notify user
                notifyUserWithDelayedReset(getString(R.string.DeviceSettingsActivity_BindingErrorNotification), R.color.errorLightColor)
            }
        }
    }

    fun deviceSettingsActivityShareBindingButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        // make sure there is a connected device
        if(ApplicationProperty.bluetoothConnectionManager.isConnected){

            // get passKey
            val passKey =
                when((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_UseCustomBindingKey)){
                    true -> {
                        (applicationContext as ApplicationProperty).loadSavedStringData(
                            R.string.FileKey_AppSettings,
                            R.string.DataKey_CustomBindingPasskey
                        )
                    }
                    else -> {
                        (applicationContext as ApplicationProperty).loadSavedStringData(
                            R.string.FileKey_AppSettings,
                            R.string.DataKey_DefaultRandomBindingPasskey
                        )
                    }
                }

            // get mac-address
            val mac = ApplicationProperty.bluetoothConnectionManager.currentDevice?.address
            val macAddress = macAddressToEncryptString(mac ?: "")

            if(verboseLog) {
                Log.d("ACT:DSA", "Binding data collected: MacAddress: $mac   PassKey: $passKey")
            }

            // encrypt data
            val encryptedPassKey = encryptString(passKey)
            val encryptedMacAddress = encryptString(macAddress)

            // build link
            val link = "${LAROOMY_WEBAPI_BASIS_LINK}devid=$encryptedMacAddress&bdata=$encryptedPassKey"

            if(verboseLog) {
                Log.d("ACT:DSA", "Sharing Link generated: $link")
            }

            // share
            val sendIntent:Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, link)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }
}