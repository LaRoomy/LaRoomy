package com.laroomysoft.laroomy

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import java.lang.Exception
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LineGraphActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementIndex = -1
    private var relatedUIAdapterIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var preventOnPauseExecutionInStandAloneMode = false
    private var buttonNormalizationRequired = false
    
    private lateinit var lineGraph: LineGraph
    private var lineDataList = ArrayList<LineGraphData>()

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView
    private lateinit var backButton: AppCompatImageButton
    private lateinit var deviceSettingsButton: AppCompatImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_line_graph)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

        // get the element ID + UI-Adapter Index
        relatedElementIndex = intent.getIntExtra("elementID", -1)
        relatedUIAdapterIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // add back button functionality
        this.backButton = findViewById<AppCompatImageButton?>(R.id.lineGraphActivityBackButton).apply {
            setOnClickListener {
                handleBackEvent()
            }
        }
        
        // add device settings button functionality (if applicable)
        this.deviceSettingsButton = findViewById<AppCompatImageButton?>(R.id.lineGraphHeaderSettingsButton).apply {
            if(isStandAlonePropertyMode){
                visibility = View.VISIBLE
                setOnClickListener {
                    setImageResource(R.drawable.ic_settings_pressed_48dp)
                    buttonNormalizationRequired = true
                    // prevent connection suspension in onPause
                    preventOnPauseExecutionInStandAloneMode = true
                    // navigate to the device settings activity..
                    val intent = Intent(this@LineGraphActivity, DeviceSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.lineGraphActivityHeaderTextView).apply {
            text =
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                            relatedUIAdapterIndex
                    ).elementText
        }

        // bind the callbacks of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the notification textView
        this.notificationTextView = findViewById(R.id.lineGraphActivityNotificationTextView)

        // get the lineGraph for further usage
        this.lineGraph = findViewById(R.id.lineGraphActivityLineGraphView)

        // get initial lineGraph state
        val lineGraphState = LineGraphState().apply {
            fromComplexPropertyState(
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedUIAdapterIndex).complexPropertyState
            )
        }

        // set the visual state
        this.setCurrentViewStateFromComplexPropertyState(lineGraphState)
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
                        "LineGraph:onPause",
                        "Line Graph Activity: The user left the app -> suspend connection"
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
                        "LineGraph:onPause",
                        "Line Graph Activity (stand-alone-mode): The user left the app -> suspend connection"
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
            Log.d("M:LGA:onResume", "onResume executed in Line-Graph Activity")
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
                        Log.d("M:LGA:onResume", "The connection was suspended -> try to reconnect")
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

    private fun setCurrentViewStateFromComplexPropertyState(lineGraphState: LineGraphState){

        if(lineGraphState.isValid()){
            this.lineGraph.drawAxisValues = lineGraphState.drawAxisValues
            this.lineGraph.drawGridLines = lineGraphState.drawGridLines

            if(lineGraphState.xIntersectionUnits != 0f) {
                this.lineGraph.xAxisGridIntersectionUnits = lineGraphState.xIntersectionUnits
            }
            if(lineGraphState.yIntersectionUnits != 0f) {
                this.lineGraph.yAxisGridIntersectionUnits = lineGraphState.yIntersectionUnits
            }

            this.lineGraph.setRange(lineGraphState.xAxisMin, lineGraphState.yAxisMin, lineGraphState.xAxisMax, lineGraphState.yAxisMax)
            this.lineGraph.lineGraphData = lineGraphState.lineData
            this.lineDataList = lineGraphState.lineData
            this.lineGraph.invalidate()
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            notificationTextView.setTextColor(getColor(colorID))
            notificationTextView.text = message
        }
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

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        if(verboseLog) {
            Log.d(
                    "M:LGA:ConStateChge",
                    "Connection state changed in LineGraph Activity. New Connection state is: $state"
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
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in LineGraphActivity.")
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
                if(isStandAlonePropertyMode) {
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
            val lineGraphState = LineGraphState()
            lineGraphState.fromComplexPropertyState(element.complexPropertyState)
            this.setCurrentViewStateFromComplexPropertyState(lineGraphState)
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

            val lRange = this.lineGraph.getRange()
            val stringArray = ArrayList<String>()
            var addString = ""
            var addLineData = false
            var eraseLineData = true

            // separate the single definition strings
            data.forEach {
                if((it != ';')&&(it != '\r')){
                    addString += it
                } else {
                    if(addString.isNotEmpty()) {
                        stringArray.add(addString)
                        addString = ""
                    }
                }
            }
            if(addString.isNotEmpty()){
                stringArray.add(addString)
            }

            // read the definitions
            stringArray.forEach {
                try {
                    if ((it[0] == 'x') || (it[0] == 'y') || (it[0] == 'p')) {
                        // must be parameter definition
                        var vName = ""
                        var vValue = "0"
                        var isName = true

                        // record the parameter name and value
                        for (c in it) {
                            if (c == ':') {
                                isName = false
                            } else {
                                if (isName) {
                                    vName += c
                                } else {
                                    vValue += c
                                }
                            }
                        }

                        // dispatch the parameter
                        val fVal = vValue.toFloat()

                        when (vName) {
                            "xsc+" -> {
                                lRange.xmax += fVal
                                lRange.xmin += fVal
                            }
                            "xsc-" -> {
                                lRange.xmax -= fVal
                                lRange.xmin -= fVal
                            }
                            "ysc+" -> {
                                lRange.ymax += fVal
                                lRange.ymin += fVal
                            }
                            "ysc-" -> {
                                lRange.ymax -= fVal
                                lRange.ymin -= fVal
                            }
                            "xmin" -> lRange.xmin = fVal
                            "xmax" -> lRange.xmax = fVal
                            "ymin" -> lRange.ymin = fVal
                            "ymax" -> lRange.ymax = fVal
                            "xisc" -> this.lineGraph.xAxisGridIntersectionUnits = fVal
                            "yisc" -> this.lineGraph.yAxisGridIntersectionUnits = fVal
                            "padd" -> {
                                eraseLineData = false
                                addLineData = true
                            }
                            else -> {
                                // invalid parameter name
                                Log.e("LGA:FastDataPipe", "Invalid parameter name: $vName")
                                (applicationContext as ApplicationProperty).logControl("E: Invalid parameter name in lineGraph fast data pipe: $vName")
                            }
                        }
                    } else {
                        // must be point definition
                        var xValue = ""
                        var yValue = ""
                        var isX = true

                        // if this is not an add sequence, erase the existing line-data, but do it only once!
                        if(eraseLineData){
                            this.lineDataList.clear()
                            eraseLineData = false
                        }

                        for (c in it) {
                            if (c == ':') {
                                isX = false
                            } else {
                                if (isX) {
                                    xValue += c
                                } else {
                                    yValue += c
                                }
                            }
                        }

                        this.lineDataList.add(
                            LineGraphData(
                                xValue.toFloat(),
                                yValue.toFloat()
                            )
                        )

                        // remove lowest point if it is outside of the range (only if this is an add sequence)
                        if(addLineData) {
                            if ((lineDataList.elementAt(0).xVal < lRange.xmin) || (lineDataList.elementAt(
                                    0
                                ).yVal < lRange.ymin)
                            ) {
                                lineDataList.removeAt(0)
                            }
                        }

                        // check the maximum size of the lineData points
                        if(this.lineDataList.size > 1000){
                            if(verboseLog){
                                Log.e("LGA:FastDataPipe", "Error: lineDataList maximum size exceeded - Skip further execution.")
                            }
                            (applicationContext as ApplicationProperty).logControl("E: lineDataList maximum size exceeded - Skip further execution.")
                            return@forEach
                        }
                    }
                } catch (e: Exception){
                    Log.e("LGA:FastDataPipe", "Error reading single value from String: $e")
                }
            }
            if(!this.lineGraph.drawProcessActive) {

                // FIXME: this is useless. The draw routines are async, so the 'drawProcessActive' param will be set to false directly after the method call while the draw is in progress !?

                this.lineGraph.setRange(lRange)
                this.lineGraph.lineGraphData = this.lineDataList
                this.lineGraph.invalidate()
            } else {
                Log.e("LineGraphFastDataSetter", "Operation was skipped. Too much data.")

                // TODO: better log! user log!
            }

        } catch (e: Exception){
            Log.e("LineGraphDataPipe", "Exception occurred: $e")
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