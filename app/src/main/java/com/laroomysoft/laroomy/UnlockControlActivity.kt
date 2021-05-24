package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView

class UnlockControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1

    private var showPin = false
    private var pinChangeModeActive = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE


    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var lockStatusTextView: AppCompatTextView
    private lateinit var showHideButton: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_control)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get UI-Views
        this.notificationTextView = findViewById(R.id.ucNotificationTextView)
        this.lockStatusTextView = findViewById(R.id.ucLockConditionStatusTextView)
        this.showHideButton = findViewById(R.id.ucShowPinButton)

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@UnlockControlActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the related complex state object
        val lockState =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // set the UI State
        this.setCurrentViewStateFromComplexPropertyState(lockState)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateID = this.relatedElementID
        // close activity
        finish()
        if(!isStandAlonePropertyMode) {
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }

    override fun onPause() {
        super.onPause()
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "M:UCA:onPause",
                    "Unlock Control Activity: The user closes the app -> suspend connection"
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
            Log.d("M:UCA:onResume", "onResume executed in Unlock Control Activity")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@UnlockControlActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect) {
            if(verboseLog) {
                Log.d("M:UCA:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState) {

    }




    fun onLockImageClick(@Suppress("UNUSED_PARAMETER") view: View) {

        // TODO: send lock command if unlocked

    }

    fun onNumPadButtonClick(view: View){

        // TODO!

        when(view.id){
            R.id.ucOneButton -> {}
            R.id.ucTwoButton -> {}
            else -> {}
        }

    }

    fun onShowHideButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        showPin = when(showPin){
            true -> {

                // TODO: show the pin

                this.showHideButton.text = getString(R.string.UnlockCtrl_ButtonText_Show)


                false

            }
            else -> {

                // TODO: hide the pin

                this.showHideButton.text = getString(R.string.UnlockCtrl_ButtonText_Hide)

                true
            }
        }

    }

    fun onChangePinButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        //  TODO: in change-pin mode set the image of the button to cancel image and implement the appropriate function
    }



    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "M:UCA:ConStateChge",
                "Connection state changed in UnlockControl Activity. New Connection state is: $state"
            )
        }
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)

        Log.e("M:UCA:onConnFailed", "Connection Attempt failed in UnlockControl Activity")
        (applicationContext as ApplicationProperty).logControl("E: Connection failed in UnlockControlActivity")

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

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.elementID == this.relatedElementID){
            if(verboseLog) {
                Log.d(
                    "M:CB:UCA:ComplexPCg",
                    "Unlock Control Activity - Complex Property changed - Update the UI"
                )
            }
            this.setCurrentViewStateFromComplexPropertyState(element.complexPropertyState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }
}