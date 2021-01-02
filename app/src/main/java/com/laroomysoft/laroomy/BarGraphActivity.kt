package com.laroomysoft.laroomy

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.mdgiitr.suyash.graphkit.BarGraph
import com.mdgiitr.suyash.graphkit.DataPoint

class BarGraphActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    var mustReconnect = false
    var relatedElementID = -1
    var relatedGlobalElementIndex = -1

    lateinit var barGraph: BarGraph
    lateinit var notificationTextView: AppCompatTextView
    private var barGraphPoints = ArrayList<DataPoint>()

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

        // get the bar-graph-view and set the initial data
        this.barGraph = findViewById(R.id.bgdBarGraphView)

        // TODO: set the data!

        for(i in 0 .. ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState.valueOne){

            // TODO: change colors!

            val p = DataPoint("- $i -", 0F, Color.parseColor("#FFFFFF"))
            this.barGraphPoints.add(p)
        }
        this.barGraph.setPoints(this.barGraphPoints)


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

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState){

        // TODO: set data!

        this.barGraphPoints.clear()

        for(i in 0 .. complexPropertyState.valueOne){

            // TODO: change colors!

            val p = DataPoint("- $i -", 0F, Color.parseColor("#FFFFFF"))
            this.barGraphPoints.add(p)
        }
        this.barGraph.setPoints(this.barGraphPoints)
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
        // set the bar-graph info

        val oldPoint = this.barGraphPoints.elementAt(data.dataIndex)

        val newName = when(data.isName){
            true -> data.dataName
            else -> oldPoint.name
        }
        val newValue = when(data.isName){
            true -> oldPoint.data
            else -> data.dataValue
        }
        val newPoint = DataPoint(newName, newValue.toFloat(), oldPoint.color)
        this.barGraphPoints[data.dataIndex] = newPoint
        this.barGraph.setPoints(this.barGraphPoints)
    }
}