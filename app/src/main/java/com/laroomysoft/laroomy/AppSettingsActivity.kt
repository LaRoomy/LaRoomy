package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.PopupWindow
import android.widget.RadioButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.appcompat.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class AppSettingsActivity : AppCompatActivity(), BillingProcessHelper.BillingEventCallback {

    private lateinit var rootContainer: ConstraintLayout
    private lateinit var autoConnectSwitch: SwitchCompat
    private lateinit var savePropertiesSwitch: SwitchCompat
    private lateinit var enableLogSwitch: SwitchCompat
    private lateinit var keepScreenActiveSwitchCompat: SwitchCompat
    private lateinit var backButton: AppCompatImageButton
    private lateinit var manageUUIDProfileButton: ConstraintLayout
    private lateinit var showLogButton: ConstraintLayout
    private lateinit var resetAppButton: ConstraintLayout
    private lateinit var bindingManagerButton: ConstraintLayout
    private lateinit var designSelectionButton: ConstraintLayout
    private lateinit var popUpWindow: PopupWindow
    private lateinit var enableLoggingHintTextView: AppCompatTextView
    
    private lateinit var currentPremiumStatusTextView: AppCompatTextView
    private lateinit var premiumStatusHintTextView: AppCompatTextView
    private lateinit var premiumTestPeriodRemainderTextView: AppCompatTextView
    private lateinit var premiumMoreInfoLink: AppCompatTextView
    private lateinit var premiumRestorePurchaseLink: AppCompatTextView
    private lateinit var premiumUnlockPurchaseLink: AppCompatTextView
    private lateinit var premiumImageView: AppCompatImageView
    

    private var buttonNormalizationRequired = false
    private var preventPopUpDoubleExecution = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)
        
        // get the root container
        this.rootContainer = findViewById(R.id.setupActivityRootContainer)
        
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
        
        // get elements of premium section and configure it
        this.currentPremiumStatusTextView = findViewById<AppCompatTextView?>(R.id.setupActivityPremiumAppUserStatusTextView).apply {
            var textToSet = ""
            var textSet = false
            
            if((applicationContext as ApplicationProperty).billingHelperCreated){
                if((applicationContext as ApplicationProperty).billingProcessHelper.purchaseIsPending){
                    textToSet = getString(R.string.SetupActivity_PremiumAppUserStatus_PurchasePending)
                    textSet = true
                }
            }
            if(!textSet) {
                textToSet =
                    when ((applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion) {
                        true -> {
                            if ((applicationContext as ApplicationProperty).premiumManager.isTestPeriodActive) {
                                getString(R.string.SetupActivity_PremiumAppUserStatus_TestPeriod)
                            } else {
                                getString(R.string.SetupActivity_PremiumAppUserStatus_Purchased)
                            }
                        }
                        else -> {
                            getString(R.string.SetupActivity_PremiumAppUserStatus_NotPurchased)
                        }
                    }
            }
            text = textToSet
        }
        this.premiumStatusHintTextView = findViewById<AppCompatTextView?>(R.id.setupActivityPremiumAppUserStatusHintTextView).apply {
            var textToSet = ""
            var textSet = false
    
            if((applicationContext as ApplicationProperty).billingHelperCreated){
                if((applicationContext as ApplicationProperty).billingProcessHelper.purchaseIsPending){
                    textToSet = getString(R.string.SetupActivity_PremiumAppStatusHint_PurchasePending)
                    textSet = true
                }
            }
            
            if(!textSet) {
                if ((applicationContext as ApplicationProperty).premiumManager.isTestPeriodActive) {
                    // test period
                    textToSet = getString(R.string.SetupActivity_PremiumAppStatusHint_TestPeriod)
                } else {
                    if ((applicationContext as ApplicationProperty).premiumManager.userHasPurchased) {
                        // purchased
                        visibility = View.GONE
                    } else {
                        // not purchased
                        textToSet = getString(R.string.SetupActivity_PremiumAppStatusHint_NotPurchased)
                    }
                }
            }
            text = textToSet
        }
        
        this.premiumTestPeriodRemainderTextView = findViewById<AppCompatTextView?>(R.id.setupActivityPremiumAppUserTestEndTextView).apply {
            if((applicationContext as ApplicationProperty).premiumManager.isTestPeriodActive){
                val strToSet = "$text  ${(applicationContext as ApplicationProperty).premiumManager.remainingTestPeriodDays}"
                text = strToSet
            } else {
                visibility = View.GONE
            }
        }
        this.premiumImageView = findViewById<AppCompatImageView?>(R.id.setupActivityPremiumImageView).apply {
            if((applicationContext as ApplicationProperty).premiumManager.userHasPurchased){
                setImageResource(R.drawable.ic_premium_purchased_36dp)
            }
        }
        this.premiumMoreInfoLink = findViewById<AppCompatTextView?>(R.id.setupActivityPremiumActionLinkMoreInfoTextView).apply {
            if((applicationContext as ApplicationProperty).premiumManager.userHasPurchased){
                visibility = View.GONE
            } else {
                setOnClickListener {
                    if(verboseLog) {
                        Log.d("SetupActivity", "More Info Link clicked !!!")
                    }
                    val intent = Intent(this@AppSettingsActivity, PremiumInfoActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        this.premiumRestorePurchaseLink = findViewById<AppCompatTextView?>(R.id.setupActivityPremiumActionLinkRestorePurchaseTextView).apply {
            if((applicationContext as ApplicationProperty).premiumManager.userHasPurchased){
                visibility = View.GONE
            } else {
                setOnClickListener {
                    if(verboseLog) {
                        Log.d("SetupActivity", "Restore purchase Link clicked !!!")
                    }
                    if((this@AppSettingsActivity.applicationContext as ApplicationProperty).billingHelperCreated) {
                        (this@AppSettingsActivity.applicationContext as ApplicationProperty).billingProcessHelper.callback = this@AppSettingsActivity
                        (this@AppSettingsActivity.applicationContext as ApplicationProperty).billingProcessHelper.restorePurchase()
                    }
                }
            }
        }
        this.premiumUnlockPurchaseLink = findViewById<AppCompatTextView?>(R.id.setupActivityPremiumActionLinkUnlockPremiumTextView).apply {
            if((applicationContext as ApplicationProperty).premiumManager.userHasPurchased){
                visibility = View.GONE
            } else {
                setOnClickListener {
                    if(verboseLog){
                        Log.d("SetupActivity", "Unlock Premium Link clicked !!!")
                    }
                    if((this@AppSettingsActivity.applicationContext as ApplicationProperty).billingHelperCreated) {
                        (this@AppSettingsActivity.applicationContext as ApplicationProperty).billingProcessHelper.callback = this@AppSettingsActivity
                        MainScope().launch {
                            (this@AppSettingsActivity.applicationContext as ApplicationProperty).billingProcessHelper.processPurchase()
                        }
                    }
                }
            }
        }
        
        // get design button and add functionality
        this.designSelectionButton = findViewById<ConstraintLayout?>(R.id.setupActivityDesignSelectionContainer).apply {
            setOnClickListener {
                onChangeDesignButtonClick(it)
            }
            val designDefaultValue = getString(R.string.DefaultValue_DesignSelection)
            val currentDesign = (applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_DesignSelection, designDefaultValue)
            if(currentDesign != designDefaultValue) {
    
                this.findViewById<AppCompatTextView>(R.id.setupActivityDesignSelectionValueDisplay)
                    .apply {
                        text = if (currentDesign == "light") {
                            getString(R.string.SetupActivity_Design_Light)
                        } else {
                            // currentDesign == "dark"
                            getString(R.string.SetupActivity_Design_Dark)
                        }
                    }
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
                        false
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
    
                if((applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion) {
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
                        if (!b) {
                            (applicationContext as ApplicationProperty).logRecordingTime = ""
                            (applicationContext as ApplicationProperty).connectionLog.clear()
                        }
                    }
                } else {
                    // its not premium disable switch and nav button
                    setShowLogButtonState(false)
                    this.isEnabled = false
                }
            }
        
        this.enableLoggingHintTextView = findViewById<AppCompatTextView?>(R.id.setupActivityEnableLoggingHintTextView).apply {
            if(!(applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion){
                setTextColor(getColor(R.color.important_text_color))
                text = getString(R.string.SetupActivity_LoggingSwitchPremiumHint)
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
        
        // TODO: if a purchase occurred on the premium page and was invoked from this page, the back-navigation to this must check if a purchase occurred and set the UI in relation to it
        // TODO: normalize enableLoggingSwitchHint (textColor + text)
        // TODO: enable showLog nav-button (only if the logging switch is on)
        // TODO: enable the logging switch!
        // TODO: hide the lower premium section and set the status to purchased

        if (this.buttonNormalizationRequired) {
            // normalize uuidManager-Button
            manageUUIDProfileButton.apply {
                setBackgroundColor(
                    getColor(R.color.setupActivityButtonNormalBackground)
                )
            }
            // normalize showLog-Button
            showLogButton.apply {
                val state =
                    (applicationContext as ApplicationProperty).loadBooleanData(
                        R.string.FileKey_AppSettings,
                        R.string.DataKey_EnableLog
                    )
                if(state) {
                    setBackgroundColor(
                        getColor(R.color.setupActivityButtonNormalBackground)
                    )
                } else {
                    setBackgroundColor(
                        getColor(R.color.setupActivityButtonDisabledBackground)
                    )
                }
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
    
    @SuppressLint("InflateParams")
    private fun onChangeDesignButtonClick(view: View){
        
        if(this.preventPopUpDoubleExecution){
            return
        } else {
            val currentDesign = (applicationContext as ApplicationProperty).loadSavedStringData(R.string.FileKey_AppSettings, R.string.DataKey_DesignSelection, getString(R.string.DefaultValue_DesignSelection))
    
            (view as ConstraintLayout).apply {
                setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
            }
            // prevent double execution
            this.preventPopUpDoubleExecution = true
            // shade the background
            this.rootContainer.alpha = 0.2f
    
            // create popUp
            val layoutInflater =
                getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            
            val popUpView =
                layoutInflater.inflate(R.layout.setup_activity_design_selection_popup, null)
    
            this.popUpWindow =
                PopupWindow(
                    popUpView,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    true
                )
    
            // add light radio button functionality
            popUpView.findViewById<RadioButton>(R.id.setupActivity_DesignPopUpRadioButton_Light).apply {
                if(currentDesign == "light"){
                    this.isChecked = true
                }
                this.setOnClickListener {
                    (applicationContext as ApplicationProperty).saveStringData("light", R.string.FileKey_AppSettings, R.string.DataKey_DesignSelection)
                    setDefaultNightMode(MODE_NIGHT_NO)
                    designSelectionButton.findViewById<AppCompatTextView>(R.id.setupActivityDesignSelectionValueDisplay)
                        .apply {
                            text = getString(R.string.SetupActivity_Design_Light)
                        }
                    popUpWindow.apply {
                        dismiss()
                    }
                }
            }
            // add dark radio button functionality
            popUpView.findViewById<RadioButton>(R.id.setupActivity_DesignPopUpRadioButton_Dark).apply {
                if(currentDesign == "dark"){
                    this.isChecked = true
                }
                this.setOnClickListener {
                    (applicationContext as ApplicationProperty).saveStringData("dark", R.string.FileKey_AppSettings, R.string.DataKey_DesignSelection)
                    setDefaultNightMode(MODE_NIGHT_YES)
                    designSelectionButton.findViewById<AppCompatTextView>(R.id.setupActivityDesignSelectionValueDisplay)
                        .apply {
                            text = getString(R.string.SetupActivity_Design_Dark)
                        }
                    popUpWindow.apply {
                        dismiss()
                    }
                }
            }
            // add system default radio button functionality
            popUpView.findViewById<RadioButton>(R.id.setupActivity_DesignPopUpRadioButton_SystemDefault).apply {
                if(currentDesign == getString(R.string.DefaultValue_DesignSelection)){
                    this.isChecked = true
                }
                this.setOnClickListener {
                    (applicationContext as ApplicationProperty).saveStringData(getString(R.string.DefaultValue_DesignSelection), R.string.FileKey_AppSettings, R.string.DataKey_DesignSelection)
                    setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
                    designSelectionButton.findViewById<AppCompatTextView>(R.id.setupActivityDesignSelectionValueDisplay)
                        .apply {
                            text = getString(R.string.SetupActivity_Design_SystemDefault)
                        }
                    popUpWindow.apply {
                        dismiss()
                    }
                }
            }
            // add cancel button functionality
            popUpView.findViewById<AppCompatButton>(R.id.setupActivity_DesignPopUp_CancelButton).apply {
                this.setOnClickListener {
                    popUpWindow.apply {
                        dismiss()
                    }
                }
            }
            
            // set dismiss listener
            this.popUpWindow.setOnDismissListener {
                
                // reset the background of the design button (constraint layout)
                designSelectionButton.setBackgroundColor(getColor(R.color.setupActivityButtonNormalBackground))
                
                // reset other control params
                this.preventPopUpDoubleExecution = false
                
                // normalize the background
                this.rootContainer.alpha = 1.0f
            }
            
            // at last show the popUp centered
            this.popUpWindow.showAtLocation(designSelectionButton, Gravity.CENTER, 0,0)
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
        // the show log button is only clickable if the enable-log switch is checked and in a premium condition (purchased or testPeriod)
        if (this.enableLogSwitch.isChecked && (applicationContext as ApplicationProperty).premiumManager.isPremiumAppVersion) {
            // indicate button down
            (view as ConstraintLayout).apply {
                setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
            }
            // schedule normalization
            this.buttonNormalizationRequired = true
            // invoke ViewLogActivity
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
        // indicate button down
        (view as ConstraintLayout).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        // schedule normalization
        this.buttonNormalizationRequired = true
        // invoke BindingManagerActivity
        val intent = Intent(this@AppSettingsActivity, BindingManagerActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
    }

    private fun onResetDataButtonClick(view: View) {
        // indicate button down
        (view as ConstraintLayout).apply {
            setBackgroundColor(getColor(R.color.setupActivityButtonPressedBackground))
        }
        // schedule normalization
        this.buttonNormalizationRequired = true
        // invoke ResetAppActivity
        val intent = Intent(this@AppSettingsActivity, ResetAppDataActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
    }
    
    override fun onAppPurchasePending() {
        this.recreate()
    }
    
    override fun onAppPurchased() {
        this.recreate()
    }
    
    override fun onPurchaseRestored() {
        this.recreate()
    }
}

