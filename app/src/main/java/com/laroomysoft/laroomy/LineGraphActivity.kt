package com.laroomysoft.laroomy

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import java.lang.Exception
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LineGraphActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false

    private lateinit var lineGraph: LineGraph
    private var lineDataList = ArrayList<LineGraphData>()

    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_line_graph)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)


        // set the header-text to the property Name
        this.headerTextView = findViewById<AppCompatTextView>(R.id.lineGraphActivityHeaderTextView).apply {
            text =
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                            relatedGlobalElementIndex
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
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
            )
        }

        // set the visual state
        this.setCurrentViewStateFromComplexPropertyState(lineGraphState)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateIndex = this.relatedElementID

        // close activity
        finish()
        if(!isStandAlonePropertyMode) {
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                    R.anim.finish_activity_slide_animation_in,
                    R.anim.finish_activity_slide_animation_out
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            if(verboseLog) {
                Log.d(
                        "M:LGA:onPause",
                        "Line Graph Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            this.expectedConnectionLoss = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:LGA:onResume", "onResume executed in Line-Graph Activity")
        }

        // reset parameter anyway
        this.expectedConnectionLoss = false

        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()) {
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
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
                        this.relatedElementID
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
                    // NOTE: do not call clear() on the bleManager, this corrupts the list on the device main page!
                    (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
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

    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)
        // if there is a connection failure -> navigate back
        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
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
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
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
        return this.relatedElementID
    }

    override fun onComplexPropertyStateChanged(
            UIAdapterElementIndex: Int,
            newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)

        val element =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementID){
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
            this.lineGraph.setRange(lRange)
            this.lineGraph.lineGraphData = this.lineDataList
            this.lineGraph.invalidate()

        } catch (e: Exception){
            Log.e("LineGraphDataPipe", "Exception occurred: $e")
        }
    }
}