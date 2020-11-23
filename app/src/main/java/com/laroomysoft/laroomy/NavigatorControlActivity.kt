package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout

class NavigatorControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, View.OnTouchListener {

    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var mustReconnect = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigator_control)

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // get the complex state data for the navigator
        val navigatorState =
            ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@NavigatorControlActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        findViewById<AppCompatImageButton>(R.id.navConNavigateMiddleButton).setOnTouchListener(this)
        findViewById<AppCompatImageButton>(R.id.navConNavigateLeftButton).setOnTouchListener(this)
        findViewById<AppCompatImageButton>(R.id.navConNavigateRightButton).setOnTouchListener(this)
        findViewById<AppCompatImageButton>(R.id.navConNavigateUpButton).setOnTouchListener(this)
        findViewById<AppCompatImageButton>(R.id.navConNavigateDownButton).setOnTouchListener(this)

        // set the view-state from the complex property state
        this.setCurrentViewStateFromComplexPropertyState(navigatorState)
    }

    override fun onPause() {
        super.onPause()
        if(!(this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){
            Log.d("M:NavCon:onPause", "Navigator Activity: The user closes the app -> suspend connection")
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
        Log.d("M:NavCon:onResume", "onResume executed in Navigator Control Activity")

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@NavigatorControlActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            Log.d("M:NavCon:onResume", "The connection was suspended -> try to reconnect")
            ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
            this.mustReconnect = false
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {

        // TODO: what does the return value mean????!

        when(event?.action){
            MotionEvent.ACTION_CANCEL -> {
                executeButtonCommand(v?.id, false)
            }
            MotionEvent.ACTION_DOWN -> {
                executeButtonCommand(v?.id, false)
            }
            MotionEvent.ACTION_UP -> {
                executeButtonCommand(v?.id, true)
                v?.performClick()
            }
            else -> {}
        }
        return true
    }

    private fun executeButtonCommand(Id: Int?, activate: Boolean){
        val directionChar = when(Id){
            R.id.navConNavigateUpButton -> '1'
            R.id.navConNavigateRightButton -> '2'
            R.id.navConNavigateDownButton -> '3'
            R.id.navConNavigateLeftButton -> '4'
            R.id.navConNavigateMiddleButton -> '5'
            else -> "0"// zero has no function (error command)
        }
        val touchDownType = when(activate){
            true -> '1'
            else -> '2'
        }
        val executionString = "C${a8BitValueToString(relatedElementID)}$directionChar$touchDownType$"
        ApplicationProperty.bluetoothConnectionManger.sendData(executionString)
    }

    private fun setCurrentViewStateFromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        var minHeightVal = 280

        if(complexPropertyState.valueOne != 1){
            findViewById<AppCompatImageButton>(R.id.navConNavigateUpButton).visibility = View.GONE
            minHeightVal -= 70
        }
        if(complexPropertyState.valueTwo != 1){
            findViewById<AppCompatImageButton>(R.id.navConNavigateRightButton).visibility = View.GONE
        }
        if(complexPropertyState.valueThree != 1){
            findViewById<AppCompatImageButton>(R.id.navConNavigateDownButton).visibility = View.GONE
            minHeightVal -= 70
        }
        if(complexPropertyState.valueFour != 1){
            findViewById<AppCompatImageButton>(R.id.navConNavigateLeftButton).visibility = View.GONE
        }
        if(complexPropertyState.valueFive != 1){
            findViewById<AppCompatImageButton>(R.id.navConNavigateMiddleButton).visibility = View.GONE
        }
        if(minHeightVal < 280){
            findViewById<ConstraintLayout>(R.id.navConNavigatorContainer).minHeight = minHeightVal
        }
    }

    private fun notifyUser(message: String, colorID: Int){

        val userNotificationTextView = findViewById<TextView>(R.id.navConUserNotificationTextView)
        userNotificationTextView.setTextColor(getColor(colorID))
        userNotificationTextView.text = message
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        Log.d("M:NavCon:ConStateChge", "Connection state changed in Navigator Control Activity. New Connection state is: $state")
        if(state){
            notifyUser(getString(R.string.GeneralMessage_reconnected), R.color.connectedTextColor)
        } else {
            notifyUser(getString(R.string.GeneralMessage_connectionSuspended), R.color.disconnectedTextColor)
        }
    }

//    fun onNavigateButtonClick(view: View){
//
//        val directionChar = when(view.id){
//            R.id.navConNavigateUpButton -> '1'
//            R.id.navConNavigateRightButton -> '2'
//            R.id.navConNavigateDownButton -> '3'
//            R.id.navConNavigateLeftButton -> '4'
//            R.id.navConNavigateMiddleButton -> '5'
//            else -> "0"// zero has no function (error command)
//        }
//        val executionString = "C${a8BitValueToString(relatedElementID)}$directionChar$"
//        ApplicationProperty.bluetoothConnectionManger.sendData(executionString)
//    }

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
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
            Log.d("M:CB:NavCon:ComplexPCg", "Navigator Control Activity - Complex Property changed - Update the UI")
            this.setCurrentViewStateFromComplexPropertyState(element.complexPropertyState)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }

}

