package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout

class NavigatorControlActivity : AppCompatActivity(), BLEConnectionManager.BleEventCallback, BLEConnectionManager.PropertyCallback, View.OnTouchListener {

    private var relatedElementID = -1
    private var relatedGlobalElementIndex = -1
    private var mustReconnect = false
    private var isStandAlonePropertyMode = COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE

    private lateinit var userNotificationTextView: AppCompatTextView
    private lateinit var headerTextView: AppCompatTextView


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigator_control)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // get the element ID + UI-Adapter Index
        relatedElementID = intent.getIntExtra("elementID", -1)
        relatedGlobalElementIndex = intent.getIntExtra("globalElementIndex", -1)

        // detect invocation method
        isStandAlonePropertyMode = intent.getBooleanExtra("isStandAlonePropertyMode", COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE)

        // get the complex state data for the navigator
        val navigatorState = NavigatorState()
        navigatorState.fromComplexPropertyState(
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).complexPropertyState
        )

        // get UI-Views
        this.userNotificationTextView = findViewById(R.id.navConUserNotificationTextView)
        this.headerTextView = findViewById(R.id.navConHeaderTextView)

        // set header text to property name
        this.headerTextView.text = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(relatedGlobalElementIndex).elementText

        // bind the callbacks and context of the bluetooth-manager to this activity
        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@NavigatorControlActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

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
            if(verboseLog) {
                Log.d(
                    "M:NavCon:onPause",
                    "Navigator Activity: The user closes the app -> suspend connection"
                )
            }
            // suspend connection and set indication-parameter
            this.mustReconnect = true
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
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
        if(!isStandAlonePropertyMode) {
            // only set slide transition if the activity was invoked from the deviceMainActivity
            overridePendingTransition(
                R.anim.finish_activity_slide_animation_in,
                R.anim.finish_activity_slide_animation_out
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if(verboseLog) {
            Log.d("M:NavCon:onResume", "onResume executed in Navigator Control Activity")
        }

        ApplicationProperty.bluetoothConnectionManager.reAlignContextReferences(this@NavigatorControlActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // reconnect to the device if necessary (if the user has left the application)
        if(this.mustReconnect){
            if(verboseLog) {
                Log.d("M:NavCon:onResume", "The connection was suspended -> try to reconnect")
            }
            ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            this.mustReconnect = false
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {

        // TODO: what does the return value mean????!

        // TODO: what is with multi-touch, implement this if possible!!!
        //val isDown = (v as AppCompatButton).isPressed

        when(event?.action){
            MotionEvent.ACTION_CANCEL -> {
                executeButtonCommand(v?.id, false)
            }
            MotionEvent.ACTION_DOWN -> {
                executeButtonCommand(v?.id, true)
            }
            MotionEvent.ACTION_UP -> {
                executeButtonCommand(v?.id, false)
                v?.performClick()
            }
            else -> {}
        }
        // NOTE: returning true blocks the image-changes on user-interaction
        return false
    }

    private fun executeButtonCommand(Id: Int?, activate: Boolean){

        val navigatorState = NavigatorState()

        when(Id){
            R.id.navConNavigateUpButton -> navigatorState.upperButton = true
            R.id.navConNavigateRightButton -> navigatorState.rightButton = true
            R.id.navConNavigateDownButton -> navigatorState.downButton = true
            R.id.navConNavigateLeftButton -> navigatorState.leftButton = true
            R.id.navConNavigateMiddleButton -> navigatorState.midButton = true
        }
        navigatorState.touchType = when(activate){
            true -> NAV_TOUCH_TYPE_DOWN
            else -> NAV_TOUCH_TYPE_RELEASE
        }
        ApplicationProperty.bluetoothConnectionManager.sendData(
            navigatorState.toExecutionString(this.relatedElementID)
        )
    }

    private fun setCurrentViewStateFromComplexPropertyState(navigatorState: NavigatorState) {
        var minHeightVal = 280

        if(navigatorState.upperButton){
            findViewById<AppCompatImageButton>(R.id.navConNavigateUpButton).visibility = View.GONE
            minHeightVal -= 70
        }
        if(navigatorState.rightButton){
            findViewById<AppCompatImageButton>(R.id.navConNavigateRightButton).visibility = View.GONE
        }
        if(navigatorState.downButton){
            findViewById<AppCompatImageButton>(R.id.navConNavigateDownButton).visibility = View.GONE
            minHeightVal -= 70
        }
        if(navigatorState.leftButton){
            findViewById<AppCompatImageButton>(R.id.navConNavigateLeftButton).visibility = View.GONE
        }
        if(navigatorState.midButton){
            findViewById<AppCompatImageButton>(R.id.navConNavigateMiddleButton).visibility = View.GONE
        }
        if(minHeightVal < 280){
            findViewById<ConstraintLayout>(R.id.navConNavigatorContainer).minHeight = minHeightVal
        }
    }

    private fun notifyUser(message: String, colorID: Int){
        runOnUiThread {
            this.userNotificationTextView.setTextColor(getColor(colorID))
            this.userNotificationTextView.text = message
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)

        if(verboseLog) {
            Log.d(
                "M:NavCon:ConStateChge",
                "Connection state changed in Navigator Control Activity. New Connection state is: $state"
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
                    "M:CB:NavCon:ComplexPCg",
                    "Navigator Control Activity - Complex Property changed - Update the UI"
                )
            }
            val navState = NavigatorState()
            navState.fromComplexPropertyState(newState)

            runOnUiThread {
                this.setCurrentViewStateFromComplexPropertyState(navState)
            }
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)
        // mark the property as changed for the back-navigation-update
        (this.applicationContext as ApplicationProperty).uiAdapterChanged = true
    }

}

