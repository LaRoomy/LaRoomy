package com.laroomysoft.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.addTextChangedListener

class AppSettingsActivity : AppCompatActivity() {

    lateinit var passwordBox: EditText
    lateinit var passwordViewModeButton: AppCompatImageButton
    lateinit var useCustomBindingKeySwitch: SwitchCompat
    lateinit var autoConnectSwitch: SwitchCompat
    lateinit var listAllDevicesSwitch: SwitchCompat
    lateinit var passwordContainer: ConstraintLayout
    lateinit var enableLogSwitch: SwitchCompat

    private var buttonNormalizationRequired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        // get the views
        this.passwordBox = findViewById(R.id.setupActivityBindingCodeBox)
        this.passwordViewModeButton = findViewById(R.id.setupActivityBindingCodeVisibilityButton)
        this.passwordContainer = findViewById(R.id.setupActivityBindingCodeContainer)

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
            setShowLogButtonVisibiliy(state)
        }

        // set the saved password (if there is one, actually there should always be one)
        val pw = (this.applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey)
        if(pw != ERROR_NOTFOUND){
            this.passwordBox.setText(pw)
        }

        // add on change listener to the password-box
        this.passwordBox.addTextChangedListener {
            // check if the box is empty, if so: save the entered passkey. Otherwise restore a default value -> the passkey cannot be empty
            if(passwordBox.text.isNotEmpty()){
                (applicationContext as ApplicationProperty).saveStringData(passwordBox.text.toString(), R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey)
            } else {
                val randomKey = createRandomPasskey(10)
                (applicationContext as ApplicationProperty).saveStringData(randomKey, R.string.FileKey_AppSettings, R.string.DataKey_CustomBindingPasskey)
                this.passwordBox.hint = randomKey
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
            setShowLogButtonVisibiliy(b)
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
                setBackgroundColor(getColor(R.color.setupActivityButtonNormalBackground))
            }
            // normalize showLog-Button
            findViewById<ConstraintLayout>(R.id.setupActivityShowLogButton).apply {
                setBackgroundColor(getColor(R.color.setupActivityButtonNormalBackground))
            }
            this.buttonNormalizationRequired = false
        }
    }

    private val passwordViewModeVisible: Int = 1
    private val passwordViewModeHidden: Int = 2
    var currentPasswordViewMode = passwordViewModeHidden

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

    private fun setShowLogButtonVisibiliy(visible: Boolean){
        findViewById<ConstraintLayout>(R.id.setupActivityShowLogButton).apply {
            visibility = when(visible){
                true -> View.VISIBLE
                else -> View.GONE
            }
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

}

