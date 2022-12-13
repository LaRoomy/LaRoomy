package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UnlockControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1

    private var showPin = false
    //private var currentMode = UC_NORMAL_MODE
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var currentEnteredPin = ""
    private var lockState = UC_STATE_LOCKED

    private var pinChangePopupOpen = false
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var lockStatusTextView: AppCompatTextView
    private lateinit var showHideButton: AppCompatImageButton
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var currentPinDisplayTextView: AppCompatTextView
    private lateinit var lockUnlockImageView: AppCompatImageView
    private lateinit var parentContainer: ConstraintLayout
    private lateinit var popupWindow: PopupWindow
    private lateinit var lockConditionStatusContainer: ConstraintLayout
    private lateinit var backButton: AppCompatImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_control)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // register back event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // get UI-Views
        this.notificationTextView = findViewById(R.id.ucNotificationTextView)
        this.lockStatusTextView = findViewById(R.id.ucLockConditionStatusTextView)
        this.showHideButton = findViewById(R.id.ucShowPinButton)
        this.headerTextView = findViewById(R.id.ucHeaderTextView)
        this.currentPinDisplayTextView = findViewById(R.id.ucCurrentPinDisplayTextView)
        this.lockUnlockImageView = findViewById(R.id.ucLockConditionImageView)
        this.parentContainer = findViewById(R.id.unLockActivityParentContainer)
        this.lockConditionStatusContainer = findViewById(R.id.ucLockConditionStatusContainer)

        // add back button functionality
        this.backButton = findViewById(R.id.unlockControlBackButton)
        this.backButton.setOnClickListener {
            handleBackEvent()
        }

        // add show/hide pin button functionality
        this.showHideButton.setOnClickListener {
            this.onShowHideButtonClick(it)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property-name
        this.headerTextView.text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the related complex state object
        val lockState = UnlockControlState()
        lockState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        // set the UI State
        this.setCurrentViewStateFromComplexPropertyState(lockState)
    }
    
    private fun handleBackEvent(){
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementID
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
            this.expectedConnectionLoss = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()
        if(verboseLog) {
            Log.d("M:UCA:onResume", "onResume executed in Unlock Control Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false

        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                finish()
            }
            else -> {
                ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
                ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                // reconnect to the device if necessary (if the user has left the application)
                if (this.mustReconnect) {
                    if (verboseLog) {
                        Log.d("M:UCA:onResume", "The connection was suspended -> try to reconnect")
                    }
                    ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                    this.mustReconnect = false
                } else {
                    // notify the remote device of the invocation of this property-page
                    ApplicationProperty.bluetoothConnectionManager.notifyComplexPropertyPageInvoked(
                        this.relatedElementID
                    )
                }
            }
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(unlockControlState: UnlockControlState) {
        runOnUiThread {
            if (unlockControlState.isValid()) {
                if (unlockControlState.mode == UC_PIN_CHANGE_MODE) {
                    // if this is a pin-change mode update, this indicates a pin-change success!
                    this.notifyUser(
                        getString(R.string.UnlockCtrl_PinChangeSuccess),
                        R.color.successLightColor
                    )
                } else {
                    if (unlockControlState.unLocked) {
                        this.lockState = UC_STATE_UNLOCKED
                        this.lockUnlockImageView.setImageResource(R.drawable.uc_lock_128_unlocked)
                        this.lockStatusTextView.setTextColor(getColor(R.color.unLockCtrlActivityStatusTextColor_Unlocked))
                        this.lockStatusTextView.text =
                            getString(R.string.UnlockCtrl_ConditionText_Unlocked)
                        this.notifyUser(
                            getString(R.string.UnlockCtrl_LockHintMessageText),
                            R.color.successLightColor
                        )
                    } else {
                        this.lockState = UC_STATE_LOCKED
                        this.lockUnlockImageView.setImageResource(R.drawable.uc_lock_128_locked)
                        this.lockStatusTextView.setTextColor(getColor(R.color.unLockCtrlActivityStatusTextColor_Locked))
                        this.lockStatusTextView.text =
                            getString(R.string.UnlockCtrl_ConditionText_Locked)
                        this.notifyUser("", R.color.normalTextColor)
                    }
                }
            }
        }
    }

    fun onLockImageClick(@Suppress("UNUSED_PARAMETER") view: View) {
        // send lock command if unlocked
        if(this.lockState == UC_STATE_UNLOCKED){
            val unlockControlState = UnlockControlState()
            unlockControlState.mode = UC_NORMAL_MODE
            unlockControlState.unLocked = false // (unlock == false) = lock command

            if(ApplicationProperty.bluetoothConnectionManager.isConnectionDoneWithSharedKey) {
                unlockControlState.flags = unlockControlState.flags or 0x08
            }

            ApplicationProperty.bluetoothConnectionManager.sendData(
                unlockControlState.toExecutionString(this.relatedElementID)
            )
        }
    }

    fun onNumPadButtonClick(view: View){

        val buttonAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce)
        view.startAnimation(buttonAnimation)

        when(view.id){
            R.id.ucZeroButton -> {
                this.currentEnteredPin += '0'
            }
            R.id.ucOneButton -> {
                this.currentEnteredPin += '1'
            }
            R.id.ucTwoButton -> {
                this.currentEnteredPin += '2'
            }
            R.id.ucThreeButton -> {
                this.currentEnteredPin += '3'
            }
            R.id.ucFourButton -> {
                this.currentEnteredPin += '4'
            }
            R.id.ucFiveButton -> {
                this.currentEnteredPin += '5'
            }
            R.id.ucSixButton -> {
                this.currentEnteredPin += '6'
            }
            R.id.ucSevenButton -> {
                this.currentEnteredPin += '7'
            }
            R.id.ucEightButton -> {
                this.currentEnteredPin += '8'
            }
            R.id.ucNineButton -> {
                this.currentEnteredPin += '9'
            }
            R.id.clearButton -> {
                // clear the entered pin
                this.currentEnteredPin = ""
            }
            R.id.OkButton -> {
                val unlockControlState = UnlockControlState()
                unlockControlState.pin = this.currentEnteredPin
                unlockControlState.unLocked = true
                unlockControlState.mode = UC_NORMAL_MODE

                if(ApplicationProperty.bluetoothConnectionManager.isConnectionDoneWithSharedKey) {
                    unlockControlState.flags = unlockControlState.flags or 0x08
                }

                // send unlock request
                ApplicationProperty.bluetoothConnectionManager.sendData(
                    unlockControlState.toExecutionString(this.relatedElementID)
                )
                // clear the entered pin
                this.currentEnteredPin = ""
            }
        }
        this.updatePinDisplay()
    }

    private fun onShowHideButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        showPin = when(showPin){
            true -> {

                if(this.currentEnteredPin.isNotEmpty()) {
                    var hiddenPin = ""
                    for (i in 0..this.currentEnteredPin.length) {
                        hiddenPin += '*'
                    }
                    this.currentPinDisplayTextView.text = hiddenPin
                } else {
                    this.currentPinDisplayTextView.text = ""
                }

                this.showHideButton.setImageResource(R.drawable.ic_visibility_gray)
                false
            }
            else -> {

                if(this.currentEnteredPin.isNotEmpty()) {
                    this.currentPinDisplayTextView.text = this.currentEnteredPin
                } else {
                    this.currentPinDisplayTextView.text = ""
                }

                this.showHideButton.setImageResource(R.drawable.ic_visibility_off_gray)
                true
            }
        }
    }

    @SuppressLint("InflateParams")
    fun onChangePinButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        // exit if popup is already open
        if(this.pinChangePopupOpen){
            return
        }

        // shade background
        this.parentContainer.alpha = 0.2f

        // mark the popup as open
        this.pinChangePopupOpen = true

        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val popupView = layoutInflater.inflate(R.layout.unlock_activity_change_pin_popup, null)

        this.popupWindow =
            PopupWindow(
                popupView,
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                true
            )

        this.popupWindow.setOnDismissListener {
            this.pinChangePopupOpen = false
            this.parentContainer.alpha = 1f
        }

        this.popupWindow.showAtLocation(
            this.lockConditionStatusContainer,
            Gravity.NO_GRAVITY, 0, 0
        )
    }

    fun onPinChangeDialogButtonClick(view: View) {

        if(view.id == R.id.unLockActivityPopupCancelButton){
            this.popupWindow.dismiss()
        } else {

            val curPinInput =
                popupWindow.contentView.findViewById<AppCompatEditText>(R.id.unLockActivityPopupCurrentPinInput)
            val newPinInput =
                popupWindow.contentView.findViewById<AppCompatEditText>(R.id.unLockActivityPopupNewPinInput)
            val repeatedNewPinInput =
                popupWindow.contentView.findViewById<AppCompatEditText>(R.id.unLockActivityPopupRepeatNewPinInput)

            val currentPin =
                curPinInput.text.toString()

            val newPin =
                newPinInput.text.toString()

            val repeatedNewPin =
                repeatedNewPinInput.text.toString()

            var goAhead = true

            when {
                currentPin.length > 10 -> {
                    curPinInput.background = AppCompatResources.getDrawable(
                        this,
                        R.drawable.unlock_control_popup_edittext_error_background
                    )
                    goAhead = false
                }
                currentPin.isEmpty() -> {
                    curPinInput.background = AppCompatResources.getDrawable(
                        this,
                        R.drawable.unlock_control_popup_edittext_error_background
                    )
                    goAhead = false
                }
                newPin.isEmpty() -> {
                    newPinInput.background = AppCompatResources.getDrawable(
                        this,
                        R.drawable.unlock_control_popup_edittext_error_background
                    )
                    goAhead = false
                }
                repeatedNewPin.isEmpty() -> {
                    repeatedNewPinInput.background = AppCompatResources.getDrawable(
                        this,
                        R.drawable.unlock_control_popup_edittext_error_background
                    )
                    goAhead = false
                }
            }

            if (goAhead) {
                if (newPin != repeatedNewPin) {
                    repeatedNewPinInput.background = AppCompatResources.getDrawable(
                        this,
                        R.drawable.unlock_control_popup_edittext_error_background
                    )
                } else {
                    sendPinChangeRequest(currentPin, newPin)
                    this.popupWindow.dismiss()
                }
            }
        }
    }

    private fun sendPinChangeRequest(oldPin: String, newPin: String){

        val unlockControlState = UnlockControlState()
        unlockControlState.unLocked = true
        unlockControlState.mode = UC_PIN_CHANGE_MODE
        unlockControlState.pin = oldPin
        unlockControlState.newPin = newPin

        if(ApplicationProperty.bluetoothConnectionManager.isConnectionDoneWithSharedKey){
            unlockControlState.flags = unlockControlState.flags or 0x08
        }

        ApplicationProperty.bluetoothConnectionManager.sendData(
            unlockControlState.toExecutionString(this.relatedElementID)
        )
    }

    private fun updatePinDisplay(){
        if(this.currentEnteredPin.isNotEmpty()) {
            if (this.showPin) {
                this.currentPinDisplayTextView.text = this.currentEnteredPin
            } else {
                var hiddenPin = ""
                for (i in this.currentEnteredPin.indices) {
                    hiddenPin += '*'
                }
                this.currentPinDisplayTextView.text = hiddenPin
            }
        }
        else {
            this.currentPinDisplayTextView.text = ""
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun connectionLossAlertDialog(){
        runOnUiThread {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage(R.string.GeneralString_UnexpectedConnectionLossMessage)
            dialog.setTitle(R.string.GeneralString_ConnectionLossDialogTitle)
            dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
                // try to reconnect
                this.propertyStateUpdateRequired = true
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                dialogInterface.dismiss()
            }
            dialog.setNegativeButton(R.string.GeneralString_Cancel) { dialogInterface: DialogInterface, _: Int ->
                // cancel action
                Executors.newSingleThreadScheduledExecutor().schedule({
                    // NOTE: do not call clear() on the bleManager, this corrupts the list on the device main page!
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                    finish()
                    if(!isStandAlonePropertyMode) {
                        // only set slide transition if the activity was invoked from the deviceMainActivity
                        overridePendingTransition(
                            R.anim.finish_activity_slide_animation_in,
                            R.anim.finish_activity_slide_animation_out
                        )
                    }
                }, 300, TimeUnit.MILLISECONDS)
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()
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

            if(this.propertyStateUpdateRequired){
                this.propertyStateUpdateRequired = false
                Executors.newSingleThreadScheduledExecutor().schedule({
                    ApplicationProperty.bluetoothConnectionManager.updatePropertyStates()
                }, TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT, TimeUnit.MILLISECONDS)
            }
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)

            if(!expectedConnectionLoss){
                // unexpected loss of connection
                if(verboseLog){
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in UnlockControlActivity.")
                }
                (applicationContext as ApplicationProperty).logControl("W: Unexpected loss of connection. Remote device not reachable.")
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
                this.connectionLossAlertDialog()
            }
        }
    }
    
    override fun onConnectionEvent(eventID: Int) {
        if (eventID == BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED) {
            notifyUser(
                getString(R.string.GeneralMessage_resumingConnection),
                R.color.connectingTextColor
            )
        }
    }
    
    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
        }
    }

    override fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData) {
        super.onRemoteUserMessage(deviceHeaderData)

        val cId = when(deviceHeaderData.type){
            USERMESSAGE_TYPE_ERROR -> R.color.ErrorColor
            USERMESSAGE_TYPE_WARNING -> R.color.WarningColor
            USERMESSAGE_TYPE_INFO -> R.color.InfoColor
            else -> R.color.InfoColor
        }
        notifyUser(deviceHeaderData.message, cId)
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        return this.relatedElementID
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementID){
            if(verboseLog) {
                Log.d(
                    "UnlockControlActivity",
                    "Complex Property state changed - Update the UI"
                )
            }
            val unlockControlState = UnlockControlState()
            unlockControlState.fromComplexPropertyState(newState)
            
            // when the flag value is nonzero, this indicates an error response
            if(unlockControlState.flags != 0){
                // flags are set, so prevent the normal UI-Update and display error message
                when {
                    ((unlockControlState.flags and 0x01) != 0) -> {
                        // unlock failed - pin rejected message
        
                        val animation =
                            AnimationUtils.loadAnimation(this, R.anim.image_view_shake_animation)
                        runOnUiThread {
                            this.lockUnlockImageView.startAnimation(animation)
                        }
                        
                        
                    }
                    ((unlockControlState.flags and 0x02) != 0) -> {
                        // pin change failed - wrong pin
                        notifyUser(getString(R.string.UnlockCtrl_PinChangeFailedWrongPin), R.color.errorLightColor)
                    }
                }
            } else {
                this.setCurrentViewStateFromComplexPropertyState(unlockControlState)
            }
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList[UIAdapterElementIndex].hasChanged = true
    }

    override fun onPropertyInvalidated() {
        if(!isStandAlonePropertyMode) {
            (this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage = true
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true

            finish()

            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }
    
    override fun onRemoteBackNavigationRequested() {
        if (!isStandAlonePropertyMode) {
            Executors.newSingleThreadScheduledExecutor().schedule({
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                
                finish()
                
                overridePendingTransition(
                    R.anim.finish_activity_slide_animation_in,
                    R.anim.finish_activity_slide_animation_out
                )
            }, 500, TimeUnit.MILLISECONDS)
        }
    }
}