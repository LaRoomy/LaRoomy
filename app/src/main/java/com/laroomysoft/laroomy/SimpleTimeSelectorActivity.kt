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

    var relatedElementID = -1
    var relatedGlobalElementIndex = -1
    var mustReconnect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_time_selector)

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // set the header-text to the property Name
        findViewById<TextView>(R.id.stsHeaderTextView).text =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

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
            gradientAngle = 220
            maxLapCount = 1
            currentValue = 12
            maxValue = 59
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
            gradientAngle = 220
            maxLapCount = 1
            currentValue = 30
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
        super.onBackPressed()
        // when the user navigates back, do a final complex-state request to make sure the saved state is the same as the current state
        ApplicationProperty.bluetoothConnectionManger.doComplexPropertyStateRequestForID(this.relatedElementID)
        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = true
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            Log.d("M:STSPage:onPause", "Simple Time Selector Activity: The user closes the app -> suspend connection")
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManger.close()
        }
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

        // TODO !
    }

    private fun notifyUser(message: String, colorID: Int){

        val userNotificationTextView = findViewById<TextView>(R.id.stsUserNotificationTextView)
        userNotificationTextView.setTextColor(getColor(colorID))
        userNotificationTextView.text = message
    }

    fun onHourValueChanged(value: Int){
        //temp:
        notifyUser("Hour value changed: $value", R.color.normalTextColor)
    }

    fun onMinuteValueChanged(value: Int){
        //temp:
        notifyUser("Minute value changed: $value", R.color.normalTextColor)
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
