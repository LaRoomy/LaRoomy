package com.laroomysoft.laroomy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var autoConnectSwitch: SwitchCompat
    private lateinit var savePropertiesSwitch: SwitchCompat
    private lateinit var enableLogSwitch: SwitchCompat
    private lateinit var keepScreenActiveSwitchCompat: SwitchCompat
    private lateinit var backButton: AppCompatImageButton
    private lateinit var manageUUIDProfileButton: ConstraintLayout
    private lateinit var showLogButton: ConstraintLayout
    private lateinit var resetAppButton: ConstraintLayout
    private lateinit var bindingManagerButton: ConstraintLayout

    private var buttonNormalizationRequired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // get backButton and add functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.setupActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // get manage UUID Button (ConstraintLayout) and register onClick event
        this.manageUUIDProfileButton = findViewById<ConstraintLayout?>(R.id.setupActivityUUIDManagerButton).apply {
            setOnClickListener {
                onManageUUIDProfilesButtonClick(it)
            }
        }
        
        // get show-log Button (ConstraintLayout) and register onClick event
        this.showLogButton = findViewById<ConstraintLayout?>(R.id.setupActivityShowLogButton).apply {
            setOnClickListener {
                onShowLogButtonClick(it)
            }
        }
        
        // get binding-manager Button (ConstraintLayout) and register onClick event
        this.bindingManagerButton = findViewById<ConstraintLayout?>(R.id.setupActivityBindingManagerButton).apply {
            setOnClickListener {
                onBindingManagerButtonClick(it)
            }
        }
        
        // get reset-app Button (ConstraintLayout) and register onClick event
        this.resetAppButton = findViewById<ConstraintLayout?>(R.id.setupActivityResetDataButton).apply {
            setOnClickListener {
                onResetDataButtonClick(it)
            }
        }

        this.autoConnectSwitch =
            findViewById<SwitchCompat>(R.id.setupActivityAutoConnectSwitch).apply {
                val state =
                    (applicationContext as ApplicationProperty).loadBooleanData(
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_AutoConnect
                    )
                this.isChecked = state
    
                setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
        
                    (applicationContext as ApplicationProperty).saveBooleanData(
                        b,
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_AutoConnect
                    )
                }
            }

        this.savePropertiesSwitch =
            findViewById<SwitchCompat>(R.id.setupActivitySavePropertiesSwitch).apply {
                val state =
                    (applicationContext as ApplicationProperty).loadBooleanData(
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_SaveProperties,
                        true
                    )
                this.isChecked = state
    
                setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
        
                    (applicationContext as ApplicationProperty).saveBooleanData(
                        b,
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_SaveProperties
                    )
                }
            }

        this.enableLogSwitch =
            findViewById<SwitchCompat>(R.id.setupActivityEnableLoggingSwitch).apply {
                val state =
                    (applicationContext as ApplicationProperty).loadBooleanData(
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_EnableLog
                    )
                this.isChecked = state
    
                // set the visual state of the log nav button regarding to the state value
                setShowLogButtonState(state)
    
                setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
        
                    (applicationContext as ApplicationProperty).saveBooleanData(
                        b,
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_EnableLog
                    )
                    setShowLogButtonState(b)
        
                    (applicationContext as ApplicationProperty).eventLogEnabled = b
        
                    // delete log data if the switch is set to off state
                    if(!b) {
                        (applicationContext as ApplicationProperty).logRecordingTime = ""
                        (applicationContext as ApplicationProperty).connectionLog.clear()
                    }
                }
            }
        
        this.keepScreenActiveSwitchCompat =
            findViewById<SwitchCompat>(R.id.setupActivityKeepScreenActiveSwitch).apply {
                val state =
                    (applicationContext as ApplicationProperty).loadBooleanData(
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_KeepScreenActive
                    )
                this.isChecked = state
    
                setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
        
                    (applicationContext as ApplicationProperty).saveBooleanData(
                        b,
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_KeepScreenActive
                    )
                }
            }
    }
    
    override fun onResume() {
        super.onResume()

        if (this.buttonNormalizationRequired) {
            // normalize uuidManager-Button
            manageUUIDProfileButton.apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            // normalize showLog-Button
            showLogButton.apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            // normalize binding-manager-Button
            bindingManagerButton.apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            // normalize resetData-Button
            resetAppButton.apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            this.buttonNormalizationRequired = false
        }

        if ((applicationContext as ApplicationProperty).appSettingsResetDone) {
            // app setting were reset by reset-activity -> apply changes to UI:
            // this are the default values:
            this.autoConnectSwitch.isChecked = false
            this.enableLogSwitch.isChecked = false
            this.setShowLogButtonState(false)
            this.keepScreenActiveSwitchCompat.isChecked = false

            (applicationContext as ApplicationProperty).appSettingsResetDone = false
        }
    }
    
    private fun handleBackEvent(){
        finish()
    }
    
    private fun setShowLogButtonState(logActive: Boolean) {
        findViewById<ConstraintLayout>(R.id.setupActivityShowLogButton).apply {
            when(logActive){
                true -> {
                    setBackgroundColor(getColor(R.color.setupActivityButtonNormalBackground))
                }
                else -> {
                    setBackgroundColor(getColor(R.color.setupActivityButtonDisabledBackground))
                }
            }
        }
        findViewById<AppCompatTextView>(R.id.setupActivityShowLogButtonDescriptor).apply {
            when(logActive){
                true -> {
                    setTextColor(getColor(R.color.colorTextPrimary))
                }
                else -> {
                    setTextColor(getColor(R.color.disabledTextColor))
                }
            }
        }
        findViewById<AppCompatImageView>(R.id.setupActivityShowLogButtonImage).apply {
            when(logActive){
                true -> {
                    setImageResource(R.drawable.ic_general_navigation_arrow_48dp)
                }
                else -> {
                    setImageResource(R.drawable.ic_general_disable_navigation_arrow_48dp)
                }
            }
        }
    }

    private fun onManageUUIDProfilesButtonClick(view: View) {
        (view as ConstraintLayout).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        this.buttonNormalizationRequired = true

        val intent = Intent(this@AppSettingsActivity, ManageUUIDProfilesActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)

    }
    
    private fun onShowLogButtonClick(view: View) {
        if (this.enableLogSwitch.isChecked) {
            (view as ConstraintLayout).apply {
                setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
            }
            this.buttonNormalizationRequired = true
            
            val intent = Intent(this@AppSettingsActivity, ViewLogActivity::class.java)
            intent.putExtra("wasInvokedFromSettingsActivity", true)
            startActivity(intent)
            overridePendingTransition(
                R.anim.start_activity_slide_animation_in,
                R.anim.start_activity_slide_animation_out
            )
        }
    }
    
    private fun onBindingManagerButtonClick(view: View){
        (view as ConstraintLayout).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        this.buttonNormalizationRequired = true
        
        val intent = Intent(this@AppSettingsActivity, BindingManagerActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
    }

    private fun onResetDataButtonClick(view: View) {
        (view as ConstraintLayout).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        this.buttonNormalizationRequired = true

        val intent = Intent(this@AppSettingsActivity, ResetAppDataActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
    }
}

