package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BillingProcessHelper private constructor(context: Context, private val defaultScope: CoroutineScope) : PurchasesUpdatedListener, BillingClientStateListener {
    
    private var billingClient : BillingClient
    
    init {
        if(verboseLog){
            Log.w("BillingProcessHelper", "BillingProcessHelper::init: creating billing client and starting connection")
        }
        //Connecting the billing client
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        billingClient.startConnection(this)
    }
    
    override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
    
    
    }
    
    override fun onBillingServiceDisconnected() {
    
    }
    
    override fun onBillingSetupFinished(p0: BillingResult) {
        if(verboseLog){
            Log.w("BillingProcessHelper", "BillingProcessHelper::onBillingSetupFinished: Response-Code: ${p0.responseCode} , Message: ${p0.debugMessage}")
        }
        when(p0.responseCode){
            BillingClient.BillingResponseCode.OK -> {
                defaultScope.launch {
                    querySkuDetailsAsync()
                    restorePurchases()
                }
            
            }
            else -> {
                Log.e("BillingProcessHelper", "Negative Billing Response Code: ${p0.responseCode}")
            }
        }
    }
    
    private suspend fun querySkuDetailsAsync() {
    
    }
    
    private suspend fun restorePurchases() {
    
    }
    
    
}