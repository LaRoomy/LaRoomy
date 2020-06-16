package com.laroomysoft.laroomy

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.agilie.circularpicker.presenter.CircularPickerContract
import com.agilie.circularpicker.ui.view.CircularPickerView
import com.agilie.circularpicker.ui.view.PickerPagerTransformer
import kotlinx.android.synthetic.main.activity_simple_time_selector.*

class SimpleTimeSelectorActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback {

    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var mustReconnect = false
    private var currentHour = 0
    private var currentMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_time_selector)

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // set the header-text to the property Name
        findViewById<TextView>(R.id.stsHeaderTextView).text =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        // get the complex state data for the time selector
        val timeSelectorState =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // set the displayed time to the current device setting
        this.currentHour = timeSelectorState.valueOne
        this.currentMinute = timeSelectorState.valueTwo
        this.updateTimeDisplay(this.currentHour, this.currentMinute)

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@SimpleTimeSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // config time selector
        view_pager.apply {
            clipChildren = false
            setPageTransformer(false, PickerPagerTransformer(context, 300))
        }
        // add the Picker to the pager
        view_pager.onAddView(CircularPickerView(this@SimpleTimeSelectorActivity).apply {

            colors = (intArrayOf(
                Color.parseColor("#00EDE9"),
                Color.parseColor("#0087D9"),
                Color.parseColor("#8A1CC3")))
            gradientAngle = 0                               // was 220 !!!!!!!!!!!
            maxLapCount = 1
            currentValue = currentHour
            maxValue = 23
            centeredTextSize = 60f
            centeredText = getString(R.string.STS_Hours)
            valueChangedListener = object : CircularPickerContract.Behavior.ValueChangedListener{
                override fun onValueChanged(value: Int) {
                    onHourValueChanged(value)
                }
            }
        })
        view_pager.onAddView(CircularPickerView(this@SimpleTimeSelectorActivity).apply {

            colors = (intArrayOf(
                Color.parseColor("#FF8D00"),
                Color.parseColor("#FF0058"),
                Color.parseColor("#920084")))
            gradientAngle = 0                               // was 220 !!!!!!!!!!!!
            maxLapCount = 1
            currentValue = currentMinute
            maxValue = 59
            centeredTextSize = 60f
            centeredText = getString(R.string.STS_Minutes)
            valueChangedListener = object : CircularPickerContract.Behavior.ValueChangedListener{
                override fun onValueChanged(value: Int) {
                    onMinuteValueChanged(value)
                }
            }
        })

    }

    override fun onPause() {
        super.onPause()
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            Log.d("M:STSPage:onPause", "Simple Time Selector Activity: The user closes the app -> suspend connection")
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManger.close()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // when the user navigates back, do a final complex-state request to make sure the saved state is the same as the current state
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = true
        (this.applicationContext as ApplicationProperty).complexUpdateID = this.relatedElementID
        // close activity
        finish()
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:STSPage:onResume", "onResume executed in Simple Time Selector Control")

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@SimpleTimeSelectorActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            Log.d("M:STSPage:onResume", "The connection was suspended -> try to reconnect")
            ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState){
        this.currentHour = complexPropertyState.valueOne
        this.currentMinute = complexPropertyState.valueTwo
        updateTimeDisplay(complexPropertyState.valueOne, complexPropertyState.valueTwo)

        // update the time selector
        // TODO: check if this works!!!
        view_pager.getView(0).currentValue = this.currentHour
        view_pager.getView(1).currentValue = this.currentMinute
    }

    private fun notifyUser(message: String, colorID: Int){

        val userNotificationTextView = findViewById<TextView>(R.id.stsUserNotificationTextView)
        userNotificationTextView.setTextColor(getColor(colorID))
        userNotificationTextView.text = message
    }

    private fun updateTimeDisplay(hour: Int, minute: Int){

        val time: String = if((hour < 10) && (minute > 9)){
            "0$hour : $minute"
        } else if((hour > 9) && (minute < 10)){
            "$hour : 0$minute"
        } else {
            "$hour : $minute"
        }
        findViewById<TextView>(R.id.timeDisplayTextView).text = time
    }

    private fun generateExecutionString() : String {
        return "C${a8BitValueToString(relatedElementID)}" +
                "${ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(
                    relatedGlobalElementIndex
                ).complexPropertyState.timeSetterIndex}" +
                a8BitValueAsTwoCharString(currentHour) +
                a8BitValueAsTwoCharString(currentMinute) +
                "00$"
    }

    fun onHourValueChanged(value: Int){
        Log.d("M:STSPage:HourChanged", "Hour value changed in SimpleTimeSelector Activity. New value is: $value")

        //temp:
        //notifyUser("Hour value changed: $value", R.color.normalTextColor)

        // update UI
        this.currentHour = value
        this.updateTimeDisplay(value, this.currentMinute)
        // update device
        ApplicationProperty.bluetoothConnectionManger.sendData(
            this.generateExecutionString()
        )
    }

    fun onMinuteValueChanged(value: Int){
        Log.d("M:STSPage:MinuteChanged", "Minute value changed in SimpleTimeSelector Activity. New value is: $value")

        //temp:
        //notifyUser("Minute value changed: $value", R.color.normalTextColor)

        // update UI
        this.currentMinute = value
        this.updateTimeDisplay(this.currentHour, value)
        // update device
        ApplicationProperty.bluetoothConnectionManger.sendData(
            this.generateExecutionString()
        )
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:STSPage:ConStateChge", "Connection state changed in SimpleTimeSelector Activity. New Connection state is: $state")
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
        }
    }

    override fun onConnectionAttemptFailed(message: String) {
        super.onConnectionAttemptFailed(message)
        Log.e("M:STSPage:onConnFailed", "Connection Attempt failed in Simple Time Selector Activity")
        notifyUser("${getString(R.string.GeneralMessage_connectingFailed)} $message", R.color.ErrorColor)
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
            Log.d("M:CB:STSPage:ComplexPCg", "Simple Time Selector Activity - Complex Property changed - Update the UI")
            this.setCurrentViewStateFromComplexPropertyState(element.complexPropertyState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }
}
