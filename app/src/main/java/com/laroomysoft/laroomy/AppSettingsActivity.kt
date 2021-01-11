package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
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
    lateinit var useDeviceBindingSwitch: SwitchCompat
    lateinit var autoConnectSwitch: SwitchCompat
    lateinit var passwordContainer: ConstraintLayout

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
        this.useDeviceBindingSwitch = findViewById<SwitchCompat>(R.id.setupActivityDeviceBindingSwitch).apply{
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_UseDeviceBinding)
            this.isChecked = state

            // if the device binding is active, show the password-edit container
            if(state){
                passwordContainer.visibility = View.VISIBLE
            }
        }

        // add on change listener to the password-box
        this.passwordBox.addTextChangedListener {

            // TODO: test if the editable <> string conversion works!!!!!!!!!!!!

            if(passwordBox.text.isNotEmpty()){
                (applicationContext as ApplicationProperty).saveStringData(passwordBox.text.toString(), R.string.FileKey_AppSettings, R.string.DataKey_BindingPasskey)
            } else {
                (applicationContext as ApplicationProperty).deleteData(R.string.FileKey_AppSettings, R.string.DataKey_BindingPasskey)
            }
        }

        // set the saved password (if there is one)
        val pw = (this.applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_BindingPasskey)
        if(pw.isNotEmpty()){
            //this.passwordBox.text =

            //TODO: set the password
            // TODO: if binding is required and is not activated in app-settings, the authentication process must be interrupted with an error message
        }

        // add the listener for the switches
        this.autoConnectSwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_AutoConnect)
        }
        this.useDeviceBindingSwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_UseDeviceBinding)

            when(b){
                true -> passwordContainer.visibility = View.VISIBLE
                else -> passwordContainer.visibility = View.GONE
            }
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

}

