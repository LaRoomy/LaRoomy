package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*

class BillingProcessHelper private constructor(context: Context) : PurchasesUpdatedListener, BillingClientStateListener {
    
    // product values
    private val productID = "my_product_id"
    
    private var billingClient : BillingClient
    private var isReady = false
    private var pendingCall = PendingCalls.NONE
    
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
    
    private enum class PendingCalls {
        NONE,
        CALL_PROCESS_PURCHASES
    }
    
    override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
    
    
    }
    
    override fun onBillingServiceDisconnected() {
        // the service was disconnected and must be reconnected on the next request
        isReady = false
    }
    
    override fun onBillingSetupFinished(p0: BillingResult) {
        if(verboseLog){
            Log.w("BillingProcessHelper", "BillingProcessHelper::onBillingSetupFinished: Response-Code: ${p0.responseCode} , Message: ${p0.debugMessage}")
        }
        when(p0.responseCode){
            BillingClient.BillingResponseCode.OK -> {
                
                // the component is ready to handle purchases
                isReady = true
                
                // check if there were requests which have forced the connection to establish (in case of disconnection)
                if(!checkForPendingCalls()){
                    // if there were no pending calls, try to restore purchases
                    restorePurchases()
                }
            }
            else -> {
                Log.e("BillingProcessHelper", "Negative Billing Response Code: ${p0.responseCode}")
            }
        }
    }
    
    private fun checkForPendingCalls() : Boolean {
        when(pendingCall){
            PendingCalls.CALL_PROCESS_PURCHASES -> {
                
                // TODO: is it right to call it from the main scope ??!
                
                MainScope().launch {
                    processPurchases()
                }
                return true
            }
            else -> {
                Log.e("BillingProcessHelper", "BillingProcessHelper::checkForPendingCalls: Unknown pending call constant: $pendingCall")
                return false
            }
        }
        
    }
    
    suspend fun processPurchases() {
        
        // make sure there is a connection to google play, otherwise start connection first
        if(!isReady){
            if(verboseLog){
                Log.w("BillingProcessHelper", "BillingProcessHelper::processPurchases: Billing client not connected. Trying to start connection..")
            }
            // start connection again
            billingClient.startConnection(this)
            // save this action
            pendingCall = PendingCalls.CALL_PROCESS_PURCHASES
            // exit
            return
        }
        
        if(verboseLog){
            Log.d("BillingProcessHelper", "BillingProcessHelper::processPurchases: Retrieving product list")
        }
        
        val productList =
            ArrayList<QueryProductDetailsParams.Product>()
        
        productList.add(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        
        val params =
            QueryProductDetailsParams.newBuilder()
        
        params.setProductList(productList)
        
        // leverage queryProductDetails Kotlin extension function
        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params.build())
        }
        
        // Process the result.
        if(productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK){
            
            
            launchPurchaseFlow(productDetailsResult.productDetailsList)
        
        } else {
            Log.w("BillingProcessHelper", "BillingProcessHelper::processPurchases: negative billing response code: ${productDetailsResult.billingResult.responseCode}")
        }
    }
    
    fun launchPurchaseFlow(pList: List<ProductDetails>?){
        
        // log !!!
        
    }
    
    
    fun restorePurchases() {
        
        // log !!!
        
    }
   
   
   
   
    
    
//    private fun queryForProductDetails(){
//        val queryProductDetailsParams =
//            QueryProductDetailsParams.newBuilder()
//                .setProductList(
//                    ImmutableList.of(
//                        QueryProductDetailsParams.Product.newBuilder()
//                            .setProductId(productID)
//                            .setProductType(BillingClient.ProductType.INAPP)
//                            .build()))
//                .build()
//
//        billingClient.queryProductDetailsAsync(queryProductDetailsParams) {
//                billingResult,
//                productDetailsList ->
//
//                // check billingResult
//                // process returned productDetailsList
//            }
//    }
    
    
}