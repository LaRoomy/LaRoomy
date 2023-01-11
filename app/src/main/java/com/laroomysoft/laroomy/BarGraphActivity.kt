package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BarGraphActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    private lateinit var barGraph: BarGraph
    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var backButton: AppCompatImageButton
    private lateinit var deviceSettingsButton: AppCompatImageButton

    private var barDataList = ArrayList<BarGraphData>()
    private var maxBarIndex = -1
    private var numBars = -1

    private var useValueAsBarDescriptor = false
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bar_graph)
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedUIAdapterIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.bgdHeaderTextView).apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedUIAdapterIndex
                ).elementText
        }

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.barGraphActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // add device settings button functionality (if applicable)
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.bgdHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to the device settings activity..
                    val intent = Intent(this@BarGraphActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // bind the callbacks of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the notification text-view
        this.notificationTextView = findViewById(R.id.bgdActivityNotificationTextView)

        // get the bar-graph-view for further usage in this component
        this.barGraph = findViewById(R.id.bgdBarGraphView)

        // get the initial bar data
        val barGraphState = BarGraphState()
        barGraphState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState
        )

        // create and set the bar-graph data
        this.setCurrentViewStateFromComplexPropertyState(
            barGraphState
        )
    }
    
    private fun handleBackEvent(){
        // check the mode and act in relation to it
        if(!isStandAlonePropertyMode) {
            // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
            (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementIndex
            // close activity
            finish()
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        } else {
            // this is stand-alone mode, so when back navigation occurs, the connection must be cleared
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if(!isStandAlonePropertyMode) {
            // NOT stand-alone mode:
            // if the following is true, onBackPressed was executed before and the connection must remain active
            // because this is a back navigation to the device main activity
            if (!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {
                if (verboseLog) {
                    Log.d(
                        "BarGraph:onPause",
                        "Bar Graph Activity: The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        } else {
            // this is stand-alone property mode
            if(this.preventOnPauseExecutionInStandAloneMode){
                this.preventOnPauseExecutionInStandAloneMode = false
            } else {
                // normal onPause execution:
                if (verboseLog) {
                    Log.d(
                        "BarGraph:onPause",
                        "Bar Graph Activity (stand-alone-mode): The user left the app -> suspend connection"
                    )
                }
                // suspend connection and set indication-parameter
                this.mustReconnect = true
                this.expectedConnectionLoss = true
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:BGD:onResume", "onResume executed in Bar-Graph Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false
    
        // recover button if applicable
        if (isStandAlonePropertyMode && buttonNormalizationRequired) {
            buttonNormalizationRequired = false
            this.deviceSettingsButton.setImageResource(R.drawable.ic_settings_48dp)
        }
    
        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                if(isStandAlonePropertyMode){
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
            }
            else -> {
                ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
                ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                // reconnect to the device if necessary (if the user has left the application)
                if (this.mustReconnect) {
                    if (verboseLog) {
                        Log.d("M:BGD:onResume", "The connection was suspended -> try to reconnect")
                    }
                    ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                    this.mustReconnect = false
                } else {
                    // notify the remote device of the invocation of this property-page
                    ApplicationProperty.bluetoothConnectionManager.notifyComplexPropertyPageInvoked(
                        this.relatedElementIndex
                    )
                }
            }
        }
    }
    
    private fun initOrAdaptBarGraphDataHolder(newSize: Int){

        if(this.barDataList.isEmpty()){
            // initialize the barGraphData holder
            for (i in 0 until newSize) {
                val bData = BarGraphData(0F, "---")
                this.barDataList.add(bData)
            }
        } else {
            // the holder is not empty, check if size has changed, if not -> do nothing
            if(this.barDataList.size != newSize){
                // temporarily save the old parameter
                val oldSize = this.barDataList.size
                val oldList = this.barDataList
                // clear the old data
                this.barDataList.clear()
                // re-init the data
                this.maxBarIndex = newSize - 1
                    // if the new size is larger than the old -> set placeholder data
                    // if the new size is lower -> discard the remaining data
                for(i in 0 until newSize){
                    if(i < oldSize)
                        this.barDataList.add(oldList.elementAt(i))
                    else
                        this.barDataList.add(BarGraphData(0F, "---"))
                }
            }
        }
    }

    private fun refreshBarGraph(){
        this.barGraph.barDataList = this.barDataList
        this.barGraph.invalidate()
    }

    private fun connectionLossAlertDialog(){
        runOnUiThread {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage(R.string.GeneralString_UnexpectedConnectionLossMessage)
            dialog.setTitle(R.string.GeneralString_ConnectionLossDialogTitle)
            dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
                // try to reconnect
                this.propertyStateUpdateRequired = true
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                dialogInterface.dismiss()
            }
            dialog.setNegativeButton(R.string.GeneralString_Cancel) { dialogInterface: DialogInterface, _: Int ->
                // cancel action
                Executors.newSingleThreadScheduledExecutor().schedule({
                    if(!isStandAlonePropertyMode) {
                        // do not call clear() on the bleManager in normal mode, this corrupts the list on the device main page!
                        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                            true
                    } else {
                        // stand-alone-mode: here 'clear()' must be called - finish goes back to main activity directly
                        ApplicationProperty.bluetoothConnectionManager.clear()
                    }
                    finish()
                    if(!isStandAlonePropertyMode) {
                        // only set slide transition if the activity was invoked from the deviceMainActivity
                        overridePendingTransition(
                            R.anim.finish_activity_slide_animation_in,
                            R.anim.finish_activity_slide_animation_out
                        )
                    }
                }, 300, TimeUnit.MILLISECONDS)
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(barGraphState: BarGraphState){
        // set bar-graph properties
        this.useValueAsBarDescriptor = barGraphState.useValueAsBarDescriptor
        this.maxBarIndex = barGraphState.numBars - 1
        this.numBars = barGraphState.numBars
        this.barDataList = barGraphState.getBarGraphDataList()

        // if value as bar-descriptor is requested override the barText
        if(this.useValueAsBarDescriptor){
            this.barDataList.forEach {
                it.barText = it.barValue.toString()
            }
        }

        if(barGraphState.useFixedMaximumValue){
            this.barGraph.fixedMaximumValue = barGraphState.fixedMaximumValue.toInt()
        }
        // update the bar-graph
        this.initOrAdaptBarGraphDataHolder(barGraphState.numBars)
        this.refreshBarGraph()
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                "M:BGD:ConStateChge",
                "Connection state changed in BarGraph Activity. New Connection state is: $state"
            )
        }
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)

            if(this.propertyStateUpdateRequired){
                this.propertyStateUpdateRequired = false
                Executors.newSingleThreadScheduledExecutor().schedule({
                    ApplicationProperty.bluetoothConnectionManager.updatePropertyStates()
                }, TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT, TimeUnit.MILLISECONDS)
            }
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)

            if(!expectedConnectionLoss){
                // unexpected loss of connection
                if(verboseLog){
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in BarGraphActivity.")
                }
                (applicationContext as ApplicationProperty).logControl("W: Unexpected loss of connection. Remote device not reachable.")
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
                this.connectionLossAlertDialog()
            }
        }
    }
    
    override fun onConnectionEvent(eventID: Int) {
        if (eventID == BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED) {
            notifyUser(
                getString(R.string.GeneralMessage_resumingConnection),
                R.color.connectingTextColor
            )
        }
    }
    
    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                if(!isStandAlonePropertyMode) {
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                        true
                } else {
                    ApplicationProperty.bluetoothConnectionManager.clear()
                }
                finish()
                if(!isStandAlonePropertyMode) {
                    // only set slide transition if the activity was invoked from the deviceMainActivity
                    overridePendingTransition(
                        R.anim.finish_activity_slide_animation_in,
                        R.anim.finish_activity_slide_animation_out
                    )
                }
            }
        }
    }

    override fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData) {
        super.onRemoteUserMessage(deviceHeaderData)
        // display here as notification
        notifyUser(deviceHeaderData.message, R.color.InfoColor)
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        return this.relatedElementIndex
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementIndex){
            if(verboseLog) {
                Log.d(
                    "M:CB:BGD:ComplexPCg",
                    "BarGraph Activity - Complex Property changed - Update the UI"
                )
            }
            val barGraphState = BarGraphState()
            barGraphState.fromComplexPropertyState(element.complexPropertyState)
            this.setCurrentViewStateFromComplexPropertyState(barGraphState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList[UIAdapterElementIndex].hasChanged = true
    }

    override fun onFastDataPipeInvoked(propertyID: Int, data: String) {
        try {
            val strArray = ArrayList<String>()
            var recString = ""
            var nextValidIndex = -1

            // separate the bar-definition strings
            data.forEachIndexed { index, c ->

                when (c) {
                    ';' -> {
                        // check exit condition
                        if (data[index + 1] == ';') {
                            // end of bar definition
                            if (recString.isNotEmpty()) {
                                strArray.add(recString)
                            }
                            recString = ""
                            nextValidIndex = index + 2
                        }
                    }
                    '\r' -> {
                        if (recString.isNotEmpty()) {
                            strArray.add(recString)
                            recString = ""
                        }
                        return@forEachIndexed
                    }
                }

                if (index >= nextValidIndex) {
                    recString += c
                }
            }
            if (recString.isNotEmpty()) {
                strArray.add(recString)
            }

            // update values from bar definition strings
            if(strArray.isNotEmpty()) {
                strArray.forEach {
                    // get bar-index
                    val barIndex = it.elementAt(0).toString().toInt()

                    if ((it[1] == ':') && (it[2] == ':')) {

                        var strValueRec = ""

                        it.forEachIndexed { index, c ->
                            if (index > 2) {
                                strValueRec += c
                            }
                        }

                        if (strValueRec.isNotEmpty()) {

                            val barValue = strValueRec.toFloat()

                            if (barIndex == 9) {
                                // this is the fixed value definition
                                this.barGraph.fixedMaximumValue = barValue.toInt()
                            } else {
                                // set bar value
                                this.barDataList[barIndex].barValue = barValue
                            }
                        }
                    }
                }
                this.refreshBarGraph()
            }
        } catch (e: Exception){
            Log.e("BarGraphDataPipe", "Exception occurred: $e")
            (applicationContext as ApplicationProperty).logControl("E: BarGraphDataPipe exception: $e.")
        }
    }
    
    override fun onPropertyInvalidated() {
        if(!isStandAlonePropertyMode) {
            (this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage = true
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            
            finish()
            
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
        // else: do nothing: property reload is not supported in stand-alone mode
    }
    
    override fun onRemoteBackNavigationRequested() {
        if (!isStandAlonePropertyMode) {
            Executors.newSingleThreadScheduledExecutor().schedule({
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
                
                finish()
                
                overridePendingTransition(
                    R.anim.finish_activity_slide_animation_in,
                    R.anim.finish_activity_slide_animation_out
                )
            }, 500, TimeUnit.MILLISECONDS)
        }
        // else: do nothing: back navigation to device main is not possible in stand-alone-mode
    }
    
    override fun onCloseDeviceRequested() {
        if(isStandAlonePropertyMode){
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        } else {
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
            (this.applicationContext as ApplicationProperty).closeDeviceRequested = true
            
            finish()
            
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }
}