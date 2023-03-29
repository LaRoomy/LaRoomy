package com.laroomysoft.laroomy

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*

class BillingProcessHelper private constructor(activity: Activity) : PurchasesUpdatedListener, BillingClientStateListener {
    
    // product values
    private val productID = "my_product_id"
    
    private var billingClient : BillingClient
    private var isReady = false
    private var pendingCall = PendingCalls.NONE
    private var routineScope: CoroutineScope
    private var cActivity: Activity
    
    init {
        if(verboseLog){
            Log.w("BillingProcessHelper", "BillingProcessHelper::init: creating billing client and starting connection")
        }
        
        // save activity
        this.cActivity = activity
        
        // inti coroutine scope
        this.routineScope = MainScope()
        
        //Connecting the billing client
        billingClient = BillingClient.newBuilder(activity.applicationContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        billingClient.startConnection(this)
    }
    
    fun terminate(){
        try {
            this.routineScope.cancel("Cancel Job")
        } catch (e: java.lang.Exception){
            Log.w("BillingProcessHelper", "BillingProcessHelper::terminate: error: no coroutine job was running")
        }
    }
    
    private enum class PendingCalls {
        NONE,
        CALL_PROCESS_PURCHASES
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when {
            (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) -> {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
            (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) -> {
                // Handle an error caused by a user cancelling the purchase flow.
                if(verboseLog) {
                    Log.w(
                        "BillingProcessHelper",
                        "BillingProcessHelper::onPurchasesUpdated: User cancelled purchase flow"
                    )
                }
            }
            else -> {
                // Handle any other error codes.
                Log.e(
                    "BillingProcessHelper",
                    "BillingProcessHelper::onPurchasesUpdated: Unexpected error: ${billingResult.responseCode}, ${billingResult.debugMessage}"
                )
            }
        }
    }
    
    override fun onBillingServiceDisconnected() {
        // the service was disconnected and must be reconnected on the next request
        isReady = false
    }
    
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if(verboseLog){
            Log.w("BillingProcessHelper", "BillingProcessHelper::onBillingSetupFinished: Response-Code: ${billingResult.responseCode} , Message: ${billingResult.debugMessage}")
        }
        when(billingResult.responseCode){
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
                Log.e("BillingProcessHelper", "Negative Billing Response Code: ${billingResult.responseCode}")
            }
        }
    }
    
    private fun checkForPendingCalls() : Boolean {
        return when(pendingCall){
            PendingCalls.CALL_PROCESS_PURCHASES -> {
                routineScope.launch {
                    processPurchase()
                }
                true
            }
            else -> {
                Log.e("BillingProcessHelper", "BillingProcessHelper::checkForPendingCalls: Unknown pending call constant: $pendingCall")
                false
            }
        }
    }
    
    suspend fun processPurchase() {
        
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
            launchPurchaseFlow(productDetailsResult)
        } else {
            Log.w(
                "BillingProcessHelper",
                "BillingProcessHelper::processPurchases: negative billing response code: ${productDetailsResult.billingResult.responseCode}, ${productDetailsResult.billingResult.debugMessage}"
            )
        }
    }
    
    private fun launchPurchaseFlow(productDetailsResult: ProductDetailsResult){
        
        if(verboseLog){
            Log.d("BillingProcessHelper", "BillingProcessHelper::launchPurchaseFlow: Invoked")
        }
        
        val productDetails =
            productDetailsResult.productDetailsList?.elementAt(0)
    
        if(productDetails != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                    .setProductDetails(productDetails)
                    // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                    // for a list of offers that are available to the user
                    //.setOfferToken(selectedOfferToken)
                    .build()
            )
    
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
    
            // Launch the billing flow
            val billingResult =
                billingClient.launchBillingFlow(cActivity, billingFlowParams)
            
            when(billingResult.responseCode){
                BillingClient.BillingResponseCode.OK -> {
                    Log.d("BillingProcessHelper", "BillingProcessHelper::launchPurchaseFlow: Billing flow successful launched")
                }
                else -> {
                    // negative result
                    Log.e(
                        "BillingProcessHelper",
                        "BillingProcessHelper::launchPurchaseFlow: negative result: ${billingResult.responseCode}, ${billingResult.debugMessage}"
                    )
                }
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
    
        // TODO: save purchase token and order ID
        
        if(purchase.purchaseState == Purchase.PurchaseState.PURCHASED){
            
            if(verboseLog){
                Log.d("BillingProcessHelper", "BillingProcessHelper::handlePurchase: User has purchased!")
            }
            
            // TODO: mark the app as purchased !!!
            
            // TODO: notify user !? Is maybe not necessary at this point.. ?
            
            if(!purchase.isAcknowledged){
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                
                routineScope.launch {
                    val ackPurchaseResult =
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams.build())
    
                    if(verboseLog){
                        Log.d(
                            "BillingProcessHelper",
                            "BillingProcessHelper::handlePurchase: Purchase acknowledge response: ${ackPurchaseResult.responseCode}, ${ackPurchaseResult.debugMessage}"
                        )
                    }
                }
            }
        } else {
            Log.d("BillingProcessHelper", "BillingProcessHelper::handlePurchase invoked, but state is not purchased. State: ${purchase.purchaseState}")
    
            if(purchase.purchaseState == Purchase.PurchaseState.PENDING){
                // TODO: set the premium section on settings activity to 'pending' state !!
            }
        }
    }
    
    fun restorePurchases() { // TODO: do this in onResume !
        if(verboseLog){
            Log.d("BillingProcessHelper", "BillingProcessHelper::restorePurchase: invoked !")
        }
    
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)

        
        routineScope.launch {
            val purchasesResult =
                billingClient.queryPurchasesAsync(params.build())
            
            if(verboseLog){
                Log.d("BillingProcessHelper", "BillingProcessHelper::restorePurchases: purchase result: ${purchasesResult.billingResult.debugMessage}")
            }
            
            purchasesResult.purchasesList.forEach {
                if(it.purchaseState == Purchase.PurchaseState.PURCHASED){
                    
                    // TODO: save purchase token and order ID
                    
                    // TODO: mark the app as purchased !
                    
                    // TODO: notify user !
                    
                    return@forEach
                }
            }
        }
    }
}