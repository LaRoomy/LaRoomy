package com.laroomysoft.laroomy

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
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
import androidx.core.widget.addTextChangedListener
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StringInterrogatorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
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
    private lateinit var deviceSettingsButton: AppCompatImageButton

    private var acceptNonAscii = false
    private var firstTextWasInvalid = false
    private var secondTextWasInvalid = false
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var navigateBackOnButtonPress = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false
    
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
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedUIAdapterIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.stringInterrogatorHeaderTextView).apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedUIAdapterIndex
                ).elementText
        }

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.stringInterrogatorBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // add device settings button functionality (if applicable)
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.stringInterrogatorHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to the device settings activity..
                    val intent = Intent(this@StringInterrogatorActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
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
        this.fieldOneContainer = findViewById(R.id.stringInterrogatorFieldOneContainer)
        this.fieldTwoContainer = findViewById(R.id.stringInterrogatorFieldTwoContainer)
    
        this.fieldOneInputText = findViewById<AppCompatEditText?>(R.id.stringInterrogatorFieldOneInput).apply {
            addTextChangedListener {
                if(!acceptNonAscii){
                    if(checkStringForNonAsciiCharacters(it.toString())){
                        firstTextWasInvalid = true
                        // notify user
                        notifyUser(getString(R.string.StringInterrogatorActivity_InvalidAsciiInputMessage_FieldOne), R.color.warningLightColor)
                    } else {
                        if(firstTextWasInvalid){
                            firstTextWasInvalid = false
                            // reset notification area
                            notifyUser("", R.color.normalTextColor)
                        }
                    }
                }
            }
        }
        this.fieldTwoInputText = findViewById<AppCompatEditText?>(R.id.stringInterrogatorFieldTwoInput).apply {
            addTextChangedListener {
                if(!acceptNonAscii){
                    if(checkStringForNonAsciiCharacters(it.toString())){
                        secondTextWasInvalid = true
                        // notify user
                        notifyUser(getString(R.string.StringInterrogatorActivity_InvalidAsciiInputMessage_FieldTwo), R.color.warningLightColor)
                    } else {
                        if(secondTextWasInvalid){
                            secondTextWasInvalid = false
                            // reset notification area
                            notifyUser("", R.color.normalTextColor)
                        }
                    }
                }
            }
        }
        
        // add confirm button onClick listener
        this.confirmButton.setOnClickListener {
            if(this.validateUserInput()) {
                val buttonAnimation =
                    AnimationUtils.loadAnimation(applicationContext, R.anim.bounce)
                it.startAnimation(buttonAnimation)
    
                if(this.collectDataAndSendCommand()) {
                    if (navigateBackOnButtonPress) {
                        if (isStandAlonePropertyMode) {
                            // make sure to hide the soft keyboard
                            val imm =
                                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(this.fieldTwoInputText.windowToken, 0)
                            // navigate back with a delay
                            Executors.newSingleThreadScheduledExecutor().schedule({
                                handleBackEvent()
                            }, 600, TimeUnit.MILLISECONDS)
                        } else {
                            (applicationContext as ApplicationProperty).delayedNavigationNotificationRequired =
                                true
                            handleBackEvent()
                        }
                    }
                }
            } else {
                // invalid user input - notify user
                notifyUser(
                    getString(R.string.StringInterrogatorActivity_InvalidInputMessage),
                    R.color.errorLightColor
                )
            }
        }

        val stringInterrogatorState = StringInterrogatorState()
        stringInterrogatorState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState
        )

        this.setCurrentViewStateFromComplexPropertyState(stringInterrogatorState)
    }
    
    private fun handleBackEvent(){
        // check the mode and act in relation to it
        if(!isStandAlonePropertyMode) {
            // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
            (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementIndex
            // close activity
            finish()
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        } else {
            // this is stand-alone mode, so when back navigation occurs, the connection must be cleared
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()

        // make sure to hide the soft keyboard
        val imm =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(this.fieldTwoInputText.windowToken, 0)
    
        if(!isStandAlonePropertyMode) {
            // NOT stand-alone mode:
            // if the following is true, onBackPressed was executed before and the connection must remain active
            // because this is a back navigation to the device main activity
            if (!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {
                if (verboseLog) {
                    Log.d(
                        "StringInterrogator:onPause",
                        "String Interrogator Activity: The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        } else {
            // this is stand-alone property mode
            if(this.preventOnPauseExecutionInStandAloneMode){
                this.preventOnPauseExecutionInStandAloneMode = false
            } else {
                // normal onPause execution:
                if (verboseLog) {
                    Log.d(
                        "String Interrogator:onPause",
                        "String Interrogator Activity (stand-alone-mode): The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                this.expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("StringInterrogator:onResume", "onResume executed in String Interrogator Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false
    
        // recover button if applicable
        if (isStandAlonePropertyMode && buttonNormalizationRequired) {
            buttonNormalizationRequired = false
            this.deviceSettingsButton.setImageResource(R.drawable.ic_settings_48dp)
        }
    
        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
                        this.relatedElementIndex
                    )
                }
            }
        }
    }
    
    private fun validateUserInput() : Boolean {
        val fieldOneInputData = this.fieldOneInputText.text.toString()
        val fieldTwoInputData = this.fieldTwoInputText.text.toString()
        return when {
            fieldOneInputData.contains(";;") -> false
            fieldOneInputData == "_" -> false
            fieldTwoInputData.contains(";;") -> false
            fieldTwoInputData == "_" -> false
            else -> true
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
                    if(!isStandAlonePropertyMode) {
                        // do not call clear() on the bleManager in normal mode, this corrupts the list on the device main page!
                        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                            true
                    } else {
                        // stand-alone-mode: here 'clear()' must be called - finish goes back to main activity directly
                        ApplicationProperty.bluetoothConnectionManager.clear()
                    }
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
        // set char acceptance
        this.acceptNonAscii = stringInterrogatorState.acceptNonAsciiCharacters
        // UI config
        runOnUiThread {
            // save action type on button press
            this.navigateBackOnButtonPress = stringInterrogatorState.navigateBackOnButtonPress
    
            // set button text (if there is one)
            if (stringInterrogatorState.buttonDescriptor.isNotEmpty()) {
                this.confirmButton.text = stringInterrogatorState.buttonDescriptor
            }
    
            // set field 1 container visibility and sub element properties
            this.fieldOneContainer.visibility = if (stringInterrogatorState.fieldOneVisible) {
                // if the field descriptor is empty, hide it
                this.fieldOneDescriptor.visibility =
                    if (stringInterrogatorState.fieldOneDescriptor.isEmpty()) {
                        View.GONE
                    } else {
                        this.fieldOneDescriptor.text = stringInterrogatorState.fieldOneDescriptor
                        View.VISIBLE
                    }
                // if the hint is not empty set it
                if (stringInterrogatorState.fieldOneHint.isNotEmpty()) {
                    this.fieldOneInputText.hint = stringInterrogatorState.fieldOneHint
                }
                // if the content is not empty set it
                if (stringInterrogatorState.fieldOneContent.isNotEmpty()) {
                    this.fieldOneInputText.setText(stringInterrogatorState.fieldOneContent)
                }
                // set the input type of the field 1 editText
                this.fieldOneInputText.inputType =
                    when (stringInterrogatorState.fieldOneInputType) {
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
            this.fieldTwoContainer.visibility = if (stringInterrogatorState.fieldTwoVisible) {
                // if the field descriptor is empty, hide it
                this.fieldTwoDescriptor.visibility =
                    if (stringInterrogatorState.fieldTwoDescriptor.isEmpty()) {
                        View.GONE
                    } else {
                        this.fieldTwoDescriptor.text = stringInterrogatorState.fieldTwoDescriptor
                        View.VISIBLE
                    }
                // if the hint is not empty set it
                if (stringInterrogatorState.fieldTwoHint.isNotEmpty()) {
                    this.fieldTwoInputText.hint = stringInterrogatorState.fieldTwoHint
                }
                // if the content is not empty set it
                if (stringInterrogatorState.fieldTwoContent.isNotEmpty()) {
                    this.fieldTwoInputText.setText(stringInterrogatorState.fieldTwoContent)
                }
                // set the input type of the field 2 editText
                this.fieldTwoInputText.inputType =
                    when (stringInterrogatorState.fieldTwoInputType) {
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
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    private fun collectDataAndSendCommand() : Boolean{
        val fieldOneCont = this.fieldOneInputText.text.toString()
        val fieldTwoCont = this.fieldTwoInputText.text.toString()
    
        return if(!this.acceptNonAscii && (checkStringForNonAsciiCharacters(fieldOneCont) || checkStringForNonAsciiCharacters(fieldTwoCont))){
            // input invalid -> notify user
            notifyUser(getString(R.string.StringInterrogatorActivity_InputIsNotAsciiConform), R.color.warningLightColor)
            false
        } else {
            val stringInterrogatorState = StringInterrogatorState()
            stringInterrogatorState.fieldOneContent = fieldOneCont
            stringInterrogatorState.fieldTwoContent = fieldTwoCont
    
            ApplicationProperty.bluetoothConnectionManager.sendData(
                stringInterrogatorState.toExecutionString(this.relatedElementIndex)
            )
            true
        }
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
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
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
        return this.relatedElementIndex
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        if (UIAdapterElementIndex == this.relatedUIAdapterIndex) {
            val element =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    UIAdapterElementIndex
                )

            if (element.internalElementIndex == this.relatedElementIndex) {
                if (verboseLog) {
                    Log.d(
                        "StringInterrogator",
                        "Complex Property changed - Update the UI !"
                    )
                }
                val stringInterrogatorState = StringInterrogatorState()
                stringInterrogatorState.fromComplexPropertyState(element.complexPropertyState)
                this.setCurrentViewStateFromComplexPropertyState(stringInterrogatorState)
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
        // else: do nothing: property reload is not supported in stand-alone mode
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
        // else: do nothing: back navigation to device main is not possible in stand-alone-mode
    }
    
    override fun onCloseDeviceRequested() {
        if(isStandAlonePropertyMode){
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        } else {
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).closeDeviceRequested = true
            
            finish()
            
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }
}