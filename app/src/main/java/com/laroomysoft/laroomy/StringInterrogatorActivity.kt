package com.laroomysoft.laroomy

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StringInterrogatorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var backButton: AppCompatImageButton

    private lateinit var fieldOneDescriptor: AppCompatTextView
    private lateinit var fieldTwoDescriptor: AppCompatTextView
    private lateinit var fieldOneInputText: AppCompatEditText
    private lateinit var fieldTwoInputText: AppCompatEditText
    private lateinit var fieldOneContainer: ConstraintLayout
    private lateinit var fieldTwoContainer: ConstraintLayout
    private lateinit var confirmButton: AppCompatButton

    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var navigateBackOnButtonPress = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_string_interrogator)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // register back event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
        
        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.stringInterrogatorHeaderTextView).apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedGlobalElementIndex
                ).elementText
        }

        // add back button functionality
        this.backButton = findViewById(R.id.stringInterrogatorBackButton)
        this.backButton.setOnClickListener {
            handleBackEvent()
        }

        // bind the callbacks of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the notification text-view
        this.notificationTextView = findViewById(R.id.stringInterrogatorNotificationTextView)

        // get UI Elements
        this.confirmButton = findViewById(R.id.stringInterrogatorPositiveButton)
        this.fieldOneDescriptor = findViewById(R.id.stringInterrogatorFieldOneDescriptor)
        this.fieldTwoDescriptor = findViewById(R.id.stringInterrogatorFieldTwoDescriptor)
        this.fieldOneInputText = findViewById(R.id.stringInterrogatorFieldOneInput)
        this.fieldTwoInputText = findViewById(R.id.stringInterrogatorFieldTwoInput)
        this.fieldOneContainer = findViewById(R.id.stringInterrogatorFieldOneContainer)
        this.fieldTwoContainer = findViewById(R.id.stringInterrogatorFieldTwoContainer)

        // add confirm button onClick listener
        this.confirmButton.setOnClickListener {
            val buttonAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bounce)
            it.startAnimation(buttonAnimation)

            this.collectDataAndSendCommand()

            if(navigateBackOnButtonPress){
                (applicationContext as ApplicationProperty).delayedNavigationNotificationRequired = true
                handleBackEvent()
            }
        }

        val stringInterrogatorState = StringInterrogatorState()
        stringInterrogatorState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        this.setCurrentViewStateFromComplexPropertyState(stringInterrogatorState)
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

        // make sure to hide the soft keyboard
        val imm =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(this.fieldTwoInputText.windowToken, 0)

        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                    "StringInterrogator:onPause",
                    "String Interrogator Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.expectedConnectionLoss = true
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("StringInterrogator:onResume", "onResume executed in String Interrogator Activity")
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
                        Log.d("StringInterrogator:onResume", "The connection was suspended -> try to reconnect")
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

    private fun setCurrentViewStateFromComplexPropertyState(stringInterrogatorState: StringInterrogatorState){

        // save action type on button press
        this.navigateBackOnButtonPress = stringInterrogatorState.navigateBackOnButtonPress

        // set button text (if there is one)
        if(stringInterrogatorState.buttonDescriptor.isNotEmpty()){
            this.confirmButton.text = stringInterrogatorState.buttonDescriptor
        }

        // set field 1 container visibility and sub element properties
        this.fieldOneContainer.visibility = if(stringInterrogatorState.fieldOneVisible){
            // if the field descriptor is empty, hide it
            this.fieldOneDescriptor.visibility = if(stringInterrogatorState.fieldOneDescriptor.isEmpty()){
                View.GONE
            } else {
                this.fieldOneDescriptor.text = stringInterrogatorState.fieldOneDescriptor
                View.VISIBLE
            }
            // if the hint is not empty set it
            if(stringInterrogatorState.fieldOneHint.isNotEmpty()){
                this.fieldOneInputText.hint = stringInterrogatorState.fieldOneHint
            }
            // if the content is not empty set it
            if(stringInterrogatorState.fieldOneContent.isNotEmpty()){
                this.fieldOneInputText.setText(stringInterrogatorState.fieldOneContent)
            }
            // set the input type of the field 1 editText
            this.fieldOneInputText.inputType =
                when(stringInterrogatorState.fieldOneInputType){
                    SI_INPUT_TYPE_TEXT -> {
                        InputType.TYPE_CLASS_TEXT
                    }
                    SI_INPUT_TYPE_TEXT_PASSWORD -> {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    SI_INPUT_TYPE_NUMBER -> {
                        InputType.TYPE_CLASS_NUMBER
                    }
                    SI_INPUT_TYPE_NUMBER_PASSWORD -> {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    }
                    else -> {
                        InputType.TYPE_CLASS_TEXT
                    }
                }

            View.VISIBLE
        } else {
            View.GONE
        }

        // set field 2 container visibility and sub element properties
        this.fieldTwoContainer.visibility = if(stringInterrogatorState.fieldTwoVisible){
            // if the field descriptor is empty, hide it
            this.fieldTwoDescriptor.visibility = if(stringInterrogatorState.fieldTwoDescriptor.isEmpty()){
                View.GONE
            } else {
                this.fieldTwoDescriptor.text = stringInterrogatorState.fieldTwoDescriptor
                View.VISIBLE
            }
            // if the hint is not empty set it
            if(stringInterrogatorState.fieldTwoHint.isNotEmpty()){
                this.fieldTwoInputText.hint = stringInterrogatorState.fieldTwoHint
            }
            // if the content is not empty set it
            if(stringInterrogatorState.fieldTwoContent.isNotEmpty()){
                this.fieldTwoInputText.setText(stringInterrogatorState.fieldTwoContent)
            }
            // set the input type of the field 2 editText
            this.fieldTwoInputText.inputType =
                when(stringInterrogatorState.fieldTwoInputType){
                    SI_INPUT_TYPE_TEXT -> {
                        InputType.TYPE_CLASS_TEXT
                    }
                    SI_INPUT_TYPE_TEXT_PASSWORD -> {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    SI_INPUT_TYPE_NUMBER -> {
                        InputType.TYPE_CLASS_NUMBER
                    }
                    SI_INPUT_TYPE_NUMBER_PASSWORD -> {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    }
                    else -> {
                        InputType.TYPE_CLASS_TEXT
                    }
                }

            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun collectDataAndSendCommand(){
        val stringInterrogatorState = StringInterrogatorState()
        stringInterrogatorState.fieldOneContent = this.fieldOneInputText.text.toString()
        stringInterrogatorState.fieldTwoContent = this.fieldTwoInputText.text.toString()

        ApplicationProperty.bluetoothConnectionManager.sendData(
            stringInterrogatorState.toExecutionString(this.relatedElementID)
        )
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "StringInterrogator:CSC",
                "Connection state changed in String Interrogator Activity. New Connection state is: $state"
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
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in StringInterrogatorActivity.")
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
        // display here as notification
        notifyUser(deviceHeaderData.message, R.color.InfoColor)
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        return this.relatedElementID
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        if (UIAdapterElementIndex == this.relatedGlobalElementIndex) {
            val element =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    UIAdapterElementIndex
                )

            if (element.internalElementIndex == this.relatedElementID) {
                if (verboseLog) {
                    Log.d(
                        "StringInterrogator",
                        "Complex Property changed - Update the UI !"
                    )
                }
                val stringInterrogatorState = StringInterrogatorState()
                stringInterrogatorState.fromComplexPropertyState(element.complexPropertyState)
                runOnUiThread {
                    this.setCurrentViewStateFromComplexPropertyState(stringInterrogatorState)
                }
            }
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
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

}