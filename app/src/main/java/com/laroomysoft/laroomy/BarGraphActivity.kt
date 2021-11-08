package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatTextView

class BarGraphActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var mustReconnect = false
    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    private lateinit var barGraph: BarGraph
    private lateinit var notificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView

    private var barDataList = ArrayList<BarGraphData>()
    private var maxBarIndex = -1
    private var numBars = -1

    private var useValueAsBarDescriptor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bar_graph)

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
        this.headerTextView = findViewById<AppCompatTextView>(R.id.bgdHeaderTextView).apply {
            text =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    relatedGlobalElementIndex
                ).elementText
        }

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@BarGraphActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get the notification text-view
        this.notificationTextView = findViewById(R.id.bgdActivityNotificationTextView)

        // get the bar-graph-view for further usage in this component
        this.barGraph = findViewById(R.id.bgdBarGraphView)

        // get the maximum bar count (zero-based!) (old)
        //this.maxBarIndex = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState.valueOne - 1

        // get the initial bar data
        val barGraphState = BarGraphState()
        barGraphState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        // create and set the bar-graph data
        this.setCurrentViewStateFromComplexPropertyState(
            barGraphState
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()
        super.onBackPressed()
        // when the user navigates back, schedule a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateID = this.relatedElementID

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
                    "M:BGD:onPause",
                    "Bar Graph Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
        }
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d("M:BGD:onResume", "onResume executed in Bar-Graph Activity")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@BarGraphActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:BGD:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        } else {
            // notify the device that multi-complex property was invoked
            ApplicationProperty.bluetoothConnectionManager.notifyMultiComplexPropertyPageInvoked(this.relatedElementID)
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

    private fun setCurrentViewStateFromComplexPropertyState(barGraphState: BarGraphState){
        // set bar-graph properties
        this.useValueAsBarDescriptor = barGraphState.useValueAsBarDescriptor
        this.maxBarIndex = barGraphState.numBars - 1
        this.numBars = barGraphState.numBars
        this.barDataList = barGraphState.getBarGraphDataList()

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
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
        }
    }

    override fun onComponentError(message: String) {
        super.onComponentError(message)
        // if there is a connection failure -> navigate back
        when(message){
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

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
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
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true

        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.internalElementIndex == this.relatedElementID){
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
    }

    override fun onMultiComplexPropertyDataUpdated(data: MultiComplexPropertyData) {
        super.onMultiComplexPropertyDataUpdated(data)

        // if the index is in range replace the element and update the graph
        if(data.dataIndex <= this.maxBarIndex){

            if(data.isName && !this.useValueAsBarDescriptor){
                barDataList.elementAt(data.dataIndex).barText = data.dataName
            } else {

                barDataList.elementAt(data.dataIndex).barValue = data.dataValue.toFloat()

                if(this.useValueAsBarDescriptor){
                    barDataList.elementAt(data.dataIndex).barText = data.dataValue.toString()
                }
            }
            this.refreshBarGraph()
        } else {
            (applicationContext as ApplicationProperty).logControl("E: BarGraph dataIndex invalid. Index was: ${data.dataIndex}. Maximum Bar index is $maxBarIndex")
        }
    }
}