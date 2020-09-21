package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.addTextChangedListener

class AppSettingsActivity : AppCompatActivity() {

    lateinit var passwordBox: EditText
    lateinit var passwordViewModeButton: AppCompatImageButton
    lateinit var useDeviceBindingSwitch: SwitchCompat
    lateinit var autoConnectSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        // get the views
        this.passwordBox = findViewById(R.id.setupActivityBindingCodeBox)
        this.passwordViewModeButton = findViewById(R.id.setupActivityBindingCodeVisibilityButton)
        this.autoConnectSwitch = findViewById<SwitchCompat>(R.id.setupActivityAutoConnectSwitch).apply{
            val state =
                (applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_AutoConnect)
            this.isChecked = state
        }
        this.useDeviceBindingSwitch = findViewById(R.id.setupActivityDeviceBindingSwitch)

        // add on change listener to the password-box
        this.passwordBox.addTextChangedListener {

            // TODO: save the password!
        }

        // add the listener for the switches
        this.autoConnectSwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_AutoConnect)
        }
        this.useDeviceBindingSwitch.setOnCheckedChangeListener{ _: CompoundButton, b: Boolean ->

            (this.applicationContext as ApplicationProperty).saveBooleanData(b, R.string.FileKey_AppSettings, R.string.DataKey_UseDeviceBinding)
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

