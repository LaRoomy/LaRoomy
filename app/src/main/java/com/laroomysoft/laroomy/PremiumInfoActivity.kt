package com.laroomysoft.laroomy

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class PremiumInfoActivity : AppCompatActivity(), BillingProcessHelper.BillingEventCallback {
    
    private lateinit var unlockPremiumButton: AppCompatButton
    private lateinit var backButton: AppCompatImageButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium_info)
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
        
        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.premiumInfoActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
    
        // add unlock premium button functionality
        this.unlockPremiumButton = findViewById<AppCompatButton?>(R.id.premiumInfoActivityUnlockButton).apply {
            setOnClickListener {
                if((this@PremiumInfoActivity.applicationContext as ApplicationProperty).billingHelperCreated) {
                    (this@PremiumInfoActivity.applicationContext as ApplicationProperty).billingProcessHelper.callback = this@PremiumInfoActivity
                    MainScope().launch {
                        (this@PremiumInfoActivity.applicationContext as ApplicationProperty).billingProcessHelper.processPurchase()
                    }
                }
            }
        }
    }
    
    private fun handleBackEvent(){
        finish()
    }
    
    override fun onAppPurchasePending() {
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage(getString(R.string.MA_PurchasePending))
        dialog.setIcon(R.drawable.ic_announcement_36dp)
        dialog.setTitle(R.string.GeneralString_Purchase)
        dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
            this@PremiumInfoActivity.finish()
        }
        dialog.create()
        dialog.show()
    }
    
    override fun onAppPurchased() {
        (applicationContext as ApplicationProperty).premiumManager.checkPremiumAppStatus()
        finish()
    }
}