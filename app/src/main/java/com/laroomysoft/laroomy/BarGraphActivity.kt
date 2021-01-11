package com.laroomysoft.laroomy

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView

class BarGraphActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    var mustReconnect = false
    var relatedElementID = -1
    var relatedGlobalElementIndex = -1

    lateinit var barGraph: BarGraph
    lateinit var notificationTextView: AppCompatTextView

    private var barDataList = ArrayList<BarGraphData>()
    private var maxBarIndex = -1

    private var useValueAsBarDescriptor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bar_graph)

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // set the header-text to the property Name
        findViewById<TextView>(R.id.bgdHeaderTextView).text =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@BarGraphActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // get the notification text-view
        this.notificationTextView = findViewById(R.id.bgdActivityNotificationTextView)

        // get the bar-graph-view for further usage in this component
        this.barGraph = findViewById(R.id.bgdBarGraphView)

        // get the maximum bar count (zero-based!)
        this.maxBarIndex = ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState.valueOne - 1

        // create and set the bar-graph data
        this.setCurrentViewStateFromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
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
    }

    override fun onPause() {
        super.onPause()
        // if this is not called due to a back-navigation, the user must have left the app
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            Log.d("M:BGD:onPause", "Bar Graph Activity: The user closes the app -> suspend connection")
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManger.close()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:BGD:onResume", "onResume executed in Bar-Graph Activity")

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@BarGraphActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            Log.d("M:BGD:onResume", "The connection was suspended -> try to reconnect")
            ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        } else {
            // notify the device that multi-complex property was invoked
            ApplicationProperty.bluetoothConnectionManger.notifyMultiComplexPropertyPageInvoked(this.relatedElementID)
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

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState){
        // set bar-graph properties
        this.useValueAsBarDescriptor = when(complexPropertyState.valueTwo) {
            0 -> false
            else -> true
        }
        if(complexPropertyState.valueThree > 0){
            this.barGraph.fixedMaximumValue = complexPropertyState.valueThree
        }
        // update the bar-graph
        this.initOrAdaptBarGraphDataHolder(complexPropertyState.valueOne)
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
        Log.d("M:BGD:ConStateChge", "Connection state changed in BarGraph Activity. New Connection state is: $state")
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        Log.e("M:BGD:onConnFailed", "Connection Attempt failed in BarGraph Activity")
        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
    }

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
        // display here as notification
        notifyUser(deviceHeaderData.message, R.color.InfoColor)
    }

    override fun onComplexPropertyStateChanged(
        UIAdapterElementIndex: Int,
        newState: ComplexPropertyState
    ) {
        super.onComplexPropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true

        val element =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(UIAdapterElementIndex)

        if(element.elementID == this.relatedElementID){
            Log.d("M:CB:BGD:ComplexPCg", "BarGraph Activity - Complex Property changed - Update the UI")
            this.setCurrentViewStateFromComplexPropertyState(element.complexPropertyState)
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
        }
    }
}