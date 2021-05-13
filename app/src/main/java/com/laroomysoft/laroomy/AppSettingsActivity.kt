package com.laroomysoft.laroomy

import android.app.ActionBar
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.addTextChangedListener

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var passwordBox: EditText
    private lateinit var passwordViewModeButton: AppCompatImageButton
    private lateinit var useCustomBindingKeySwitch: SwitchCompat
    private lateinit var autoConnectSwitch: SwitchCompat
    private lateinit var listAllDevicesSwitch: SwitchCompat
    private lateinit var passwordContainer: ConstraintLayout
    private lateinit var enableLogSwitch: SwitchCompat
    private lateinit var passKeyInputNotificationTextView: AppCompatTextView
    private lateinit var keepScreenActiveSwitchCompat: SwitchCompat

    private var buttonNormalizationRequired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        // get the views
        this.passwordBox = findViewById(R.id.setupActivityBindingCodeBox)
        this.passwordViewModeButton = findViewById(R.id.setupActivityBindingCodeVisibilityButton)
        this.passwordContainer = findViewById(R.id.setupActivityBindingCodeContainer)
        this.passKeyInputNotificationTextView = findViewById(R.id.setupActivityBindingKeyNotificationTextView)

        this.autoConnectSwitch = findViewById<SwitchCompat>(R.id.setupActivityAutoConnectSwitch).apply{
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_AutoConnect)
            this.isChecked = state
        }
        this.useCustomBindingKeySwitch = findViewById<SwitchCompat>(R.id.setupActivityCustomBindingCodeSwitch).apply{
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_UseCustomBindingKey)
            this.isChecked = state

            // if the device binding is active, show the password-edit container and set the password to the edit-box
            if(state){
                passwordContainer.visibility = View.VISIBLE
            }
        }
        this.listAllDevicesSwitch = findViewById<SwitchCompat>(R.id.setupActivityListAllDevicesSwitch).apply{
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_ListAllDevices)
            this.isChecked = state
        }
        this.enableLogSwitch = findViewById<SwitchCompat>(R.id.setupActivityEnableLoggingSwitch).apply{
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)
            this.isChecked = state

            // if logging is activated, show the nav-button
            //setShowLogButtonVisibility(state)

            // FIXME: not the best approach???? Why does setting the margins of the separator view in onCreate not work???
            Handler(Looper.getMainLooper()).postDelayed({
                setShowLogButtonVisibility(state)
            }, 200)

            // TODO: delete logArray and time-stamp??
        }
        this.keepScreenActiveSwitchCompat = findViewById<SwitchCompat>(R.id.setupActivityKeepScreenActiveSwitch).apply {
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)
            this.isChecked = state
        }

        // set the saved password (if there is one, actually there should always be one)
        val pw = (this.applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey)
        if(pw != ERROR_NOTFOUND){
            this.passwordBox.setText(pw)
        }

        // add on change listener to the password-box
        this.passwordBox.addTextChangedListener {
            // check if the box is empty, if so: save the entered passkey. Otherwise restore a default value -> the passkey cannot be empty
            val passKey =
                passwordBox.text.toString()
            // the passKey will only be saved if the length is correct (0 > length < 11)
            if(passKey.isNotEmpty()) {
                    if(passKey.length > 10){
                        // passKey too long
                        // notify User
                        setPassKeyInputNotification(getString(R.string.SetupActivity_PassKeyMaxIs10Character), R.color.WarningColor)
                    } else {
                        // check for invalid character
                        if(!validatePassKey(passKey)){
                            // invalid character in passKey string
                            // notify user
                            setPassKeyInputNotification(getString(R.string.SetupActivity_PassKeyContainsInvalidCharacter), R.color.ErrorColor)
                        } else {
                            // everything ok - hide notification (if there is one)
                            if(passKeyInputNotificationTextView.visibility == View.VISIBLE) {
                                setPassKeyInputNotification("", 0)
                            }
                            // save the passKey
                            (applicationContext as ApplicationProperty).saveStringData(
                                passKey,
                                R.string.FileKey_AppSettings,
                                R.string.DataKey_CustomBindingPasskey
                            )

                        }
                    }
            } else {
                val randomKey = createRandomPasskey(10)
                (applicationContext as ApplicationProperty).saveStringData(randomKey, R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey)

                setPassKeyInputNotification(getString(R.string.SetupActivity_PassKeyMustNotBeEmpty), R.color.ErrorColor)
            }
        }

        // add the listener for the switches
        this.autoConnectSwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_AutoConnect)
        }
        this.useCustomBindingKeySwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_UseCustomBindingKey)

            when(b){
                true -> {
                    // show the password-box
                    passwordContainer.visibility = View.VISIBLE

                    // if this is the first time a custom binding key is set, a random value must be set to prevent that the key is empty
                    if((applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey) == ERROR_NOTFOUND){
                        val randomKey = createRandomPasskey(10)
                        (applicationContext as ApplicationProperty).saveStringData(randomKey, R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey)
                        passwordBox.setText(randomKey)
                    } else {
                        // display the default key
                        passwordBox.setText((applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey))
                    }
                }
                else -> passwordContainer.visibility = View.GONE
            }
        }
        this.listAllDevicesSwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_ListAllDevices)
        }
        this.enableLogSwitch.setOnCheckedChangeListener {  _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)
            setShowLogButtonVisibility(b)

            (this.applicationContext as ApplicationProperty).eventLogEnabled = b
        }
        this.keepScreenActiveSwitchCompat.setOnCheckedChangeListener {  _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onResume() {
        super.onResume()

        if(this.buttonNormalizationRequired){
            // normalize uuidManager-Button
            findViewById<ConstraintLayout>(R.id.setupActivityUUIDManagerButton).apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            // normalize showLog-Button
            findViewById<ConstraintLayout>(R.id.setupActivityShowLogButton).apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            // normalize resetData-Button
            findViewById<ConstraintLayout>(R.id.setupActivityResetDataButton).apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            this.buttonNormalizationRequired = false
        }
    }

    private val passwordViewModeVisible: Int = 1
    private val passwordViewModeHidden: Int = 2
    private var currentPasswordViewMode = passwordViewModeHidden

    fun onPasswordViewModeButtonClick(@Suppress("UNUSED_PARAMETER") view: View){
        when(currentPasswordViewMode){
            // the password is hidden -> show it
            passwordViewModeHidden -> {
                // set the crossed eye image
                passwordViewModeButton.setImageResource(R.drawable.eye_hide)
                // show the password
                passwordBox.transformationMethod = HideReturnsTransformationMethod.getInstance()
                // save the state-var
                currentPasswordViewMode = passwordViewModeVisible
            }
            // the password is visible -> hide it
            passwordViewModeVisible -> {
                // set the normal eye image
                passwordViewModeButton.setImageResource(R.drawable.eye_show)
                // hide the password
                passwordBox.transformationMethod = PasswordTransformationMethod.getInstance()
                // save the state-var
                currentPasswordViewMode = passwordViewModeHidden
            }
        }
    }

    private fun setShowLogButtonVisibility(visible: Boolean){
        findViewById<ConstraintLayout>(R.id.setupActivityShowLogButton).apply {
            visibility = when(visible){
                true -> View.VISIBLE
                else -> View.GONE
            }
        }
        findViewById<View>(R.id.setupActivitySixthSeparatorView).apply {
            visibility = when(visible){
                true -> View.VISIBLE
                else -> View.GONE
            }
        }
        findViewById<View>(R.id.setupActivityFifthSeparatorView).apply {

            if(visible) {
                (layoutParams as ViewGroup.MarginLayoutParams).setMargins(0,10,0,0)
            } else {
                (layoutParams as ViewGroup.MarginLayoutParams).setMargins(10,10,10,0)
            }
//            requestLayout()
//            invalidate()
        }
    }

    fun onManageUUIDProfilesButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        findViewById<ConstraintLayout>(R.id.setupActivityUUIDManagerButton).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        this.buttonNormalizationRequired = true

        val intent = Intent(this@AppSettingsActivity, ManageUUIDProfilesActivity::class.java)
        startActivity(intent)
    }

    fun onShowLogButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        findViewById<ConstraintLayout>(R.id.setupActivityShowLogButton).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        this.buttonNormalizationRequired = true

        val intent = Intent(this@AppSettingsActivity, ViewLogActivity::class.java)
        startActivity(intent)
    }

    private fun setPassKeyInputNotification(message: String, colorID: Int){
        if(message.isEmpty()){
            this.passKeyInputNotificationTextView.setTextColor(getColor(R.color.normalTextColor))
            this.passKeyInputNotificationTextView.visibility = View.GONE
        } else {
            this.passKeyInputNotificationTextView.visibility = View.VISIBLE
            this.passKeyInputNotificationTextView.setTextColor(getColor(colorID))
            this.passKeyInputNotificationTextView.text = message
        }
    }

    fun onResetDataButtonClick(view: View) {
        (view as ConstraintLayout).setBackgroundColor(
            getColor(R.color.setupActivityButtonPressedBackground)
        )
        this.buttonNormalizationRequired = true

        val intent = Intent(this@AppSettingsActivity, ResetAppDataActivity::class.java)
        startActivity(intent)
    }
}

