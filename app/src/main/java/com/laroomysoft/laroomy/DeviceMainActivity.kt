package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView
import java.lang.Exception
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

const val STATUS_DISCONNECTED = 0
const val STATUS_CONNECTED = 1
const val STATUS_CONNECTING = 2

class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback, OnPropertyClickListener {

    // device property list elements
    private lateinit var devicePropertyListRecyclerView: DeviceMainPropertyRecyclerView
    private lateinit var devicePropertyListViewAdapter: RecyclerView.Adapter<*>
    private lateinit var devicePropertyListLayoutManager: RecyclerView.LayoutManager
    //private var devicePropertyList= ArrayList<DevicePropertyListContentInformation>()

    private lateinit var deviceTypeHeaderTextView: AppCompatTextView
    private lateinit var deviceConnectionStatusTextView: AppCompatTextView
    private lateinit var deviceMenuButton: AppCompatImageButton

    //private lateinit var deviceHeaderNotificationImageView: AppCompatImageView

    private lateinit var deviceHeaderNotificationTextView: AppCompatTextView
    private lateinit var deviceHeaderNotificationContainer: ConstraintLayout

    //private lateinit var deviceSettingsButton: AppCompatImageButton

    private lateinit var popUpWindow: PopupWindow
    private lateinit var deviceMenuPopUpWindow: PopupWindow


    private var activityWasSuspended = false
    //private var buttonRecoveryRequired = false
    private var restoreIndex = -1
    //private var deviceImageResourceId = -1
    private var propertyLoadingFinished = true
    private var levelSelectorPopUpOpen = false
    private var optionSelectorPopUpOpen = false
    private var deviceMenuOpen = false

    private val propertyList = ArrayList<DevicePropertyListContentInformation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        //this.deviceImageResourceId = intent.getIntExtra("BondedDeviceImageResourceId", -1)

        // realign the context to the bluetoothManager (NOTE: only on creation - onResume handles this on navigation)
        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get UI Elements
        this.deviceMenuButton = findViewById(R.id.deviceMenuImageButton)
        this.deviceTypeHeaderTextView = findViewById(R.id.deviceMainActivityDeviceTypeHeaderNameTextView)
        this.deviceConnectionStatusTextView = findViewById(R.id.deviceMainActivityDeviceConnectionStatusTextView)

        //this.deviceHeaderNotificationImageView = findViewById(R.id.deviceMainActivityDeviceInfoSubHeaderImageView)

        this.deviceHeaderNotificationTextView = findViewById(R.id.deviceMainActivityDeviceInfoSubHeaderTextView)
        this.deviceHeaderNotificationContainer = findViewById(R.id.deviceInfoSubHeaderContainer)

        //this.deviceSettingsButton = findViewById(R.id.deviceMainActivityDeviceSettingsButton)

        // init recycler view!!
        this.devicePropertyListLayoutManager = LinearLayoutManager(this)

        // set empty list placeholder in array-list
/*
        val dc = DevicePropertyListContentInformation()
        dc.elementType = NO_CONTENT_ELEMENT
        this.devicePropertyList.add(dc)
*/

        // TODO: this is a new way, must be tested, if the ui-array is complete, copy it
        if(ApplicationProperty.bluetoothConnectionManager.uIAdapterList.isNotEmpty()){
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.forEach {
                this.propertyList.add(it)
            }
        }

        // bind array to adapter
        this.devicePropertyListViewAdapter =
            DevicePropertyListAdapter(
                //ApplicationProperty.bluetoothConnectionManager.uIAdapterList,
                this.propertyList,
                this,
                this@DeviceMainActivity,
                this)

        this.devicePropertyListViewAdapter.setHasStableIds(true)

        // bind the elements to the recycler
        this.devicePropertyListRecyclerView =
            findViewById<DeviceMainPropertyRecyclerView>(R.id.devicePropertyListView)
                .apply {
                    setHasFixedSize(true)
                    layoutManager = devicePropertyListLayoutManager
                    adapter = devicePropertyListViewAdapter
                    itemAnimator = null
                }
    }

    override fun onPause() {
        super.onPause()
        // only suspend the connection if the user left this application
        if(!(this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution) {

            if(verboseLog) {
                Log.d(
                    "M:CB:onPause",
                    "onPause executed in DeviceMainActivity - User left the app or navigated back - suspend connection"
                )
            }

            // suspend connection
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            setUIConnectionStatus(STATUS_DISCONNECTED)
            this.activityWasSuspended = true

        } else {
            // reset parameter:
            (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = false
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // the loading activity is terminated, so the start(main)-Activity will be invoked
        ApplicationProperty.bluetoothConnectionManager.clear()
        finish()
    }

    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d(
                "M:onResume",
                "Activity resumed. Previous loading done: ${this.activityWasSuspended}"
            )
        }

        // at first check if this callback will be invoked due to a back-navigation from a property sub-page
        // or if it was invoked on creation or a resume from outside of the application
        if ((this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {

            // this is a back navigation from a property sub-page

            // if the device was disconnected on a property sub-page, navigate back with delay
            if (!ApplicationProperty.bluetoothConnectionManager.isConnected) {

                // set UI visual state
                resetSelectedItemBackground()
                setUIConnectionStatus(STATUS_DISCONNECTED)

                // TODO: or show a dialog ??

                // try to reconnect ??? try it several times like in loading activity


                //showNotificationHeaderAndPostMessage(30, getString(R.string.DMA_NoConnection))

                // schedule back-navigation
                Handler(Looper.getMainLooper()).postDelayed({
                    (applicationContext as ApplicationProperty).resetControlParameter()
                    ApplicationProperty.bluetoothConnectionManager.clear()
                    finish()
                }, 1500)

            } else {
                // do a complex state- update if required...
/*
                if ((this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired) {
                    if(verboseLog) {
                        Log.d(
                            "M:onResume",
                            "Complex-State-Update required for Property-Index ${(this.applicationContext as ApplicationProperty).complexUpdateIndex}"
                        )
                    }

                    (applicationContext as ApplicationProperty).logControl("Resumed Device Main Activity: Complex-State-Update required for Index: ${(this.applicationContext as ApplicationProperty).complexUpdateIndex}")

                    (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired =
                        false

                    if (ApplicationProperty.bluetoothConnectionManager.isMultiComplexProperty((this.applicationContext as ApplicationProperty).complexUpdateIndex)) {
                        // delay the complex state update
                        Handler(Looper.getMainLooper()).postDelayed(
                            {
                                ApplicationProperty.bluetoothConnectionManager.doComplexPropertyStateRequestForPropertyIndex(
                                    (this.applicationContext as ApplicationProperty).complexUpdateIndex
                                )
                                (this.applicationContext as ApplicationProperty).complexUpdateIndex =
                                    -1
                            },
                            500
                        )
                    } else {
                        // this is not a multicomplex property, do the complex state update immediately
                        ApplicationProperty.bluetoothConnectionManager.doComplexPropertyStateRequestForPropertyIndex(
                            (this.applicationContext as ApplicationProperty).complexUpdateIndex
                        )
                        (this.applicationContext as ApplicationProperty).complexUpdateIndex = -1
                    }
                }
*/


                // realign the context objects to the bluetoothManager
                ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
                ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                // notify the device that the user navigated back to the device main page
                ApplicationProperty.bluetoothConnectionManager.notifyBackNavigationToDeviceMainPage()

                // set property-item to normal background
                resetSelectedItemBackground()

                // reset the parameter
                restoreIndex = -1
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                    false

                if ((this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage) {

                    this.reloadProperties()

                    (this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage =
                        false
                }





                if ((this.applicationContext as ApplicationProperty).uiAdapterChanged) {
                    (this.applicationContext as ApplicationProperty).uiAdapterChanged = false

                    // TODO: update data in a loop!? or is this not necessary anymore????

                }


                // TODO: detect state-changes and update the property-list-items
            }

        } else {
            // creation or resume action:

            // make sure to set the right Name and image for the device
            this.deviceTypeHeaderTextView.text =
                ApplicationProperty.bluetoothConnectionManager.currentDevice?.name

            // show the loading circle
            //this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

            // check if this is a call on creation or resume:
            if (this.activityWasSuspended) {
                // must be a resume action

                // Update the connection status
                //setUIConnectionStatus(STATUS_CONNECTING)  // ?????????


                // realign objects is not necessary here!
                //ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceMainActivity, this)
                //ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

                // try to reconnect
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                this.activityWasSuspended = false

            } else {
                // Update the connection status
                setUIConnectionStatus(
                    if(ApplicationProperty.bluetoothConnectionManager.isConnected){
                        STATUS_CONNECTED
                    } else {
                        STATUS_DISCONNECTED
                    }
                )

                // must be the creation process -> start property listing
                //ApplicationProperty.bluetoothConnectionManager.startPropertyListing()
            }

            // TODO: how to confirm the device-properties
            // maybe make a changed-parameter in the device firmware and call that to upgrade performance and hold the line clear for communication?
        }
    }

    private fun setItemBackgroundColor(index: Int, colorID: Int){
        val linearLayout = this.devicePropertyListLayoutManager.findViewByPosition(index) as? LinearLayout
        linearLayout?.setBackgroundColor(getColor(colorID))

        // maybe get the sub holder constraintLayout (ID: contentHolderLayout) ????
    }

    private fun setPropertyToSelectedState(index: Int){
        val element =
            this.propertyList.elementAt(index)

        if(element.elementType == PROPERTY_ELEMENT){
            val rootLayoutElement =
                this.devicePropertyListLayoutManager.findViewByPosition(index) as? LinearLayout

            if(element.isGroupMember){
                if(element.isLastInGroup){
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(this, R.drawable.inside_group_property_last_list_element_selected_background)
                } else {
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(this, R.drawable.inside_group_property_list_element_selected_background)
                }
            } else {
                rootLayoutElement?.background =
                    AppCompatResources.getDrawable(this, R.drawable.single_property_list_element_selected_background)
            }
        }
    }

    private fun getBackgroundDrawableFromElementIndex(index: Int){}

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadProperties(){

        // TODO: log

        if(verboseLog) {
            Log.d(
                "M:DMA:reloadProperties",
                "Reloading Properties requested!"
            )
        }

        // at first update the UI
        runOnUiThread {
            this.propertyLoadingFinished = false
            this.propertyList.clear()
            this.devicePropertyListViewAdapter.notifyDataSetChanged()
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

            val deviceHeaderData = DeviceInfoHeaderData()
            deviceHeaderData.message = getString(R.string.DMA_LoadingPropertiesUserInfo)
            deviceHeaderData.displayTime = 9

            this.showNotificationHeaderAndPostMessage(deviceHeaderData)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            ApplicationProperty.bluetoothConnectionManager.reloadProperties()
        }, 500) // TODO: shorter time?
    }

//    override fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation) {
//
//        Log.d("M:onPropEntryClk", "Property list entry was clicked at index: $index")
//
//        //if(devicePropertyList.elementAt(index).canNavigateForward && (devicePropertyList.elementAt(index).elementType == PROPERTY_ELEMENT)){
//
//            // navigate to the appropriate property-page
//
//            // make sure the onPause routine does not disconnect the device
//
//            // animate the element to indicate the press
//       // }
//    }

    override fun onPropertyElementButtonClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        if(verboseLog) {
            Log.d(
                "M:CB:onPropBtnClk",
                "Property element was clicked. Element-Type is BUTTON at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Property-Index: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                        "UI-Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }
        // NOTE: the button has no state the execution command contains always "1"

        // send execution command
        ApplicationProperty.bluetoothConnectionManager.sendData(
            makeSimplePropertyExecutionString(index, 0)
        )
    }

    override fun onPropertyElementSwitchClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation,
        switch: SwitchCompat
    ) {
        if(verboseLog) {
            Log.d(
                "M:CB:onPropSwitchClk",
                "Property element was clicked. Element-Type is SWITCH at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Property-Index: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                        "UI-Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }
        // TODO: check if the newState comes with the right terminology

        val c = when(switch.isChecked){
            true -> 1
            else -> 0
        }
        ApplicationProperty.bluetoothConnectionManager.sendData(
            makeSimplePropertyExecutionString(index, c)
        )
    }

    @SuppressLint("InflateParams")
    override fun onPropertyLevelSelectButtonClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        if(verboseLog) {
            Log.d(
                "M:CB:onPropLevelSelClk",
                "Property element was clicked. Element-Type is LEVEL-SELECTOR at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Property-Index: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                        "UI-Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }
        // return if popup is open
        if(this.levelSelectorPopUpOpen){
            return
        }

        // shade the background
        this.devicePropertyListRecyclerView.alpha = 0.2f

        this.levelSelectorPopUpOpen = true

        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val popUpView =
            layoutInflater.inflate(R.layout.device_main_level_selector_popup, null)

        this.popUpWindow =
            PopupWindow(
                popUpView,
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                true
            )

        this.popUpWindow.setOnDismissListener {
            devicePropertyListRecyclerView.alpha = 1f
            levelSelectorPopUpOpen = false
        }

        this.popUpWindow.showAtLocation(this.devicePropertyListRecyclerView, Gravity.CENTER, 0, 0)

        // set the appropriate image and seekbar position
        this.popUpWindow.contentView.findViewById<AppCompatImageView>(R.id.levelSelectorPopUpImageView).setImageResource(
            resourceIdForImageId(devicePropertyListContentInformation.imageID)
        )
        val percentageLevelPropertyGenerator =
            PercentageLevelPropertyGenerator(devicePropertyListContentInformation.simplePropertyState)
        this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.levelSelectorPopUpTextView).text =
            percentageLevelPropertyGenerator.percentageString

        // add the handler to the element
        devicePropertyListContentInformation.handler = this
        // set seekbar properties
        this.popUpWindow.contentView.findViewById<SeekBar>(R.id.levelSelectorPopUpSeekbar).apply {
            this.progress = percentageLevelPropertyGenerator.percentageValue
            this.setOnSeekBarChangeListener(devicePropertyListContentInformation)
        }
    }

    @SuppressLint("InflateParams")
    override fun onPropertyOptionSelectButtonClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        if(verboseLog) {
            Log.d(
                "M:CB:onPropOptionSelClk",
                "Property element was clicked. Element-Type is OPTION-SELECTOR at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Property-Index: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                        "UI-Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }
        // return if popup is open
        if(this.optionSelectorPopUpOpen){
            return
        }

        // get the options array
        val options =
            decryptOptionSelectorString(devicePropertyListContentInformation.elementText)

        if(options.isEmpty()){
            return
        }

        // shade the background
        this.devicePropertyListRecyclerView.alpha = 0.2f

        // TODO: block the activation of background (list) elements during popup-lifecycle
        this.optionSelectorPopUpOpen = true

        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val popUpView =
            layoutInflater.inflate(R.layout.device_main_option_selector_popup, null)

        this.popUpWindow =
            PopupWindow(
                popUpView,
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                true
            )

        this.popUpWindow.setOnDismissListener {
            devicePropertyListRecyclerView.alpha = 1f
            optionSelectorPopUpOpen = false
        }

        this.popUpWindow.showAtLocation(this.devicePropertyListRecyclerView, Gravity.CENTER, 0, 0)

        // set the appropriate image and option selection
        this.popUpWindow.contentView.findViewById<AppCompatImageView>(R.id.optionSelectorPopUpImageView).setImageResource(
            resourceIdForImageId(devicePropertyListContentInformation.imageID)
        )

        this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.optionSelectorPopUpTextView).text =
            options.elementAt(0)

        options.removeAt(0)

        var strArray = arrayOf<String>()
        options.forEach {
            strArray += it
        }

        // set picker values
        this.popUpWindow.contentView.findViewById<NumberPicker>(R.id.optionSelectorPopUpNumberPicker).apply {
            minValue = 0
            maxValue = options.size - 1

            displayedValues = strArray

//            setFormatter {
//                options.elementAt(it)
//            }

            if(devicePropertyListContentInformation.simplePropertyState < options.size) {
                value = devicePropertyListContentInformation.simplePropertyState
            }

            setOnValueChangedListener { _, _, newVal ->

                // send execution command
                ApplicationProperty.bluetoothConnectionManager.sendData(
                    makeSimplePropertyExecutionString(devicePropertyListContentInformation.internalElementIndex, newVal)
                )

                // update list element
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).simplePropertyState = newVal
                devicePropertyListViewAdapter.notifyItemChanged(index)
            }
        }
    }

    override fun onSeekBarPositionChange(
        index: Int,
        newValue: Int,
        changeType: Int
    ) {
        if(verboseLog) {
            val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

            Log.d(
                "M:CB:onSeekBarChange",
                "Property element was clicked. Element-Type is SEEKBAR at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Property-Index: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                        "UI-Element-Index: ${devicePropertyListContentInformation.globalIndex}\n\n" +
                        "SeekBar specific values:\n" +
                        "New Value: $newValue\n" +
                        "Change-Type: ${
                            when (changeType) {
                                SEEK_BAR_START_TRACK -> "Start tracking"
                                SEEK_BAR_PROGRESS_CHANGING -> "Tracking"
                                SEEK_BAR_STOP_TRACK -> "Stop tracking"
                                else -> "error"
                            }
                        }"
            )
        }

        if(changeType == SEEK_BAR_PROGRESS_CHANGING){
            // calculate bit-value
            val bitValue =
                percentTo8Bit(newValue)

            // update list element
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).simplePropertyState = bitValue
            this.devicePropertyListViewAdapter.notifyItemChanged(index)

            if(levelSelectorPopUpOpen){
                val seekBarText =  "$newValue%"
                // set the seekbar and textview properties
                this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.levelSelectorPopUpTextView).text = seekBarText
                this.popUpWindow.contentView.findViewById<SeekBar>(R.id.levelSelectorPopUpSeekbar).progress = newValue
            }

            ApplicationProperty.bluetoothConnectionManager.sendData(
                makeSimplePropertyExecutionString(
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex,
                    bitValue
                )
            )
        }
    }

    override fun onNavigatableElementClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        if(verboseLog) {
            Log.d(
                "M:CB:onNavElementClk",
                "Property element was clicked. Element-Type is Complex/Navigate forward at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Element-ID: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                        "Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }

        // set it to selected color

        //TODO: set item drawable to selected state

        setPropertyToSelectedState(index)

        //setItemBackgroundColor(index, R.color.DMA_ItemSelectedColor)
        //setItemSeparatorViewColors(index, R.color.selectedSeparatorColor)
        //setItemTopSeparatorColor(index, R.color.selectedSeparatorColor)
        //setItemBottomSeparatorColor(index, R.color.selectedSeparatorColor)

        // save the index of the highlighted item to reset it on back-navigation
        restoreIndex = index

        // navigate
        when(devicePropertyListContentInformation.propertyType){
            COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the RGB Page
                val intent = Intent(this@DeviceMainActivity, RGBControlActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the extended level selector page
                val intent = Intent(this@DeviceMainActivity, ExtendedLevelSelectorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the time selector page with single-select-mode
                val intent = Intent(this@DeviceMainActivity, SimpleTimeSelectorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the time-frame selector page
                val intent = Intent(this@DeviceMainActivity, TimeFrameSelectorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the unlock control page
                val intent = Intent(this@DeviceMainActivity, UnlockControlActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the navigator page
                val intent = Intent(this@DeviceMainActivity, NavigatorControlActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPH -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the barGraph page
                val intent = Intent(this@DeviceMainActivity, BarGraphActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_LINEGRAPH -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the lineGraph page
                val intent = Intent(this@DeviceMainActivity, LineGraphActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            else -> {
                // what to do here??
            }
        }
    }

    private fun resetSelectedItemBackground(){

        // TODO: set item drawable to normal state

        // reset the selected item background if necessary
        if (restoreIndex >= 0) {
            val element =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(restoreIndex)

            val rootLayoutElement =
                this.devicePropertyListLayoutManager.findViewByPosition(restoreIndex) as? LinearLayout

            // check if the property is part of a group and set the appropriate background-color
            if (element.isGroupMember) {
                // this element is part of a group, now check if this is the last in the group
                if (element.isLastInGroup) {
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(
                            this,
                            R.drawable.inside_group_property_last_list_element_background
                        )
                } else {
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(
                            this,
                            R.drawable.inside_group_property_list_element_background
                        )
                }

            } else {
                // must be a single (group-less) property
                rootLayoutElement?.background =
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.single_property_list_element_background
                    )
            }
        }
    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
        // same procedure like onBackPressed!
        try {
            this.deviceMenuPopUpWindow.dismiss()
            ApplicationProperty.bluetoothConnectionManager.clear()
            finish()
        } catch (e: Exception){
            Log.e("discardDevButtonClick", "Exception occurred on discard device button click. Info: $e")
        }
    }

    fun onReloadPropertiesButtonClick(@Suppress("UNUSED_PARAMETER")view: View){

        try {
            this.deviceMenuPopUpWindow.dismiss()
            this.reloadProperties()

        } catch (e: Exception){
            Log.e("reloadPropButtonClick", "Exception occurred on reload properties button click. Info: $e")
        }

    }

    fun onDeviceSettingsButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        try {
            if (ApplicationProperty.bluetoothConnectionManager.isConnected) {
                // dismiss popup
                this.deviceMenuPopUpWindow.dismiss()
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                    true
                // navigate to the device settings activity..
                val intent = Intent(this@DeviceMainActivity, DeviceSettingsActivity::class.java)
                startActivity(intent)
            }
        } catch (e: Exception){
            Log.e("onDevSettingButtonClick", "Exception occurred on device settings button click. Info: $e")
        }
    }

    fun onDeviceMainNotificationAreaClick(@Suppress("UNUSED_PARAMETER")view: View) {
        runOnUiThread {
            this.hideNotificationHeader()
        }
    }


    fun onReconnectDevice(@Suppress("UNUSED_PARAMETER")view: View){
        // re-connect
        // confirm device-properties???
        // re-connect and re-new the device-properties???

        if(!ApplicationProperty.bluetoothConnectionManager.isConnected) {
            ApplicationProperty.bluetoothConnectionManager.close()
            ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
        } else {
            // TODO: remove this, this is temporary
            //ApplicationProperty.bluetoothConnectionManager.sendData("D024$")
            //this.reloadProperties()
        }
    }

    @SuppressLint("InflateParams")
    fun onDeviceMenuButtonClick(@Suppress("UNUSED_PARAMETER")view: View) {

        // prevent double execution
        if(!this.deviceMenuOpen){

            // shade the background
            this.devicePropertyListRecyclerView.alpha = 0.2f

            // set the menu button to selected state
            this.deviceMenuButton.setImageResource(R.drawable.ic_menu_yellow_36dp)

            // set popup open parameter
            this.deviceMenuOpen = true

            // get layout inflater
            val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

            // get recycler view position
            val recyclerViewPos = intArrayOf(0, 0)
            this.devicePropertyListRecyclerView.getLocationInWindow(recyclerViewPos)

            // inflate the view
            val popUpView =
                layoutInflater.inflate(R.layout.device_main_activity_popup_menu_flyout, null)

            // and create the instance
            this.deviceMenuPopUpWindow =
                PopupWindow(
                    popUpView,
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    true
                )

            // set animation
            this.deviceMenuPopUpWindow.animationStyle = R.style.sideSlidePopUpAnimationStyle

            // set on dismiss listener
            this.deviceMenuPopUpWindow.setOnDismissListener {
                // normalize parameter with delay that equals the animation slide time
                Executors.newSingleThreadScheduledExecutor().schedule({
                    this.devicePropertyListRecyclerView.alpha = 1f
                    this.deviceMenuOpen = false
                    this.deviceMenuButton.setImageResource(R.drawable.ic_menu_white_36dp)
                }, 300, TimeUnit.MILLISECONDS)
            }

            // show it!
            this.deviceMenuPopUpWindow.showAtLocation(
                this.devicePropertyListRecyclerView,
                Gravity.NO_GRAVITY, 0, recyclerViewPos.elementAt(1) - 5
            )
        }
    }


    private fun setUIConnectionStatus(status :Int){

        runOnUiThread {
            when (status) {
                STATUS_CONNECTED -> {
                    this.deviceConnectionStatusTextView.setTextColor(getColor(R.color.connectedTextColor))
                    this.deviceConnectionStatusTextView.text =
                        getString(R.string.DMA_ConnectionStatus_connected)
                }
                STATUS_CONNECTING -> {
                    this.deviceConnectionStatusTextView.setTextColor(getColor(R.color.connectingTextColor))
                    this.deviceConnectionStatusTextView.text = getString(R.string.DMA_ReconnectDevice)
                }
                else -> {
                    this.deviceConnectionStatusTextView.setTextColor(getColor(R.color.disconnectedTextColor))
                    this.deviceConnectionStatusTextView.text =
                        getString(R.string.DMA_ConnectionStatus_disconnected)
                }
            }
        }
    }

    private fun showNotificationHeaderAndPostMessage(deviceHeaderData: DeviceInfoHeaderData) {
        runOnUiThread {
            //this.deviceHeaderNotificationImageView.setImageResource(
            //resourceIdForImageId(imageID)
            //)

            //this.deviceHeaderNotificationContainer.visibility = View.VISIBLE
            this.deviceHeaderNotificationTextView.text = deviceHeaderData.message
            this.deviceHeaderNotificationTextView.setTextColor(
                getColor(
                    colorForUserMessageType(
                        deviceHeaderData.type
                    )
                )
            )

            //this.deviceHeaderNotificationContainer.visibility = View.VISIBLE

            val expandCollapseExtension = ExpandCollapseExtension()
            expandCollapseExtension.expand(this.deviceHeaderNotificationContainer, 600)
        }

        // schedule the hide of the container (if requested)

        if (deviceHeaderData.displayTime > 0) {
            Executors.newSingleThreadScheduledExecutor().schedule({
                runOnUiThread {
                    this.hideNotificationHeader()
                }
            }, deviceHeaderData.displayTime, TimeUnit.MILLISECONDS)
        }
    }

    private fun hideNotificationHeader(){

//        val translateAnimation = TranslateAnimation(0f,0f, 0f, -(this.deviceHeaderNotificationContainer.height.toFloat()))
//        translateAnimation.duration = 500
//        translateAnimation.fillAfter = false

        //val scaleAnimation = ScaleAnimation(0f, 0f, 0f, this.deviceHeaderNotificationContainer.height.toFloat())
        //scaleAnimation.duration = 500


        //this.deviceHeaderNotificationContainer.startAnimation(scaleAnimation)

//        val transition = Slide(Gravity.TOP)
//        transition.duration = 600
//        transition.addTarget(this.deviceHeaderNotificationContainer)
//
//        TransitionManager.beginDelayedTransition(findViewById(R.id.deviceMainParentContainer), transition)

        //this.deviceHeaderNotificationTextView.visibility = View.GONE


        //this.deviceHeaderNotificationContainer.visibility = View.GONE

        runOnUiThread {
            this.deviceHeaderNotificationTextView.text = ""

            val expandCollapseExtension = ExpandCollapseExtension()
            expandCollapseExtension.collapse(this.deviceHeaderNotificationContainer, 600)
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        // this callback will only invoked in this activity if this is a re-connect-process (the initial connection attempt will be executed in the loading activity!)

        (applicationContext as ApplicationProperty).logControl("I: Connection State changed in DeviceMainActivity to $state")

        // set the UI State to connected:
        val conStatus = if(state){
            STATUS_CONNECTED
        } else {
            STATUS_DISCONNECTED
        }
        this.setUIConnectionStatus(conStatus)

        // stop the loading circle and set the info-header
        if(state) {


            // send a test command // FIXME: why?
            //ApplicationProperty.bluetoothConnectionManager.testConnection(200)// TODO: this must be tested


            // check if the property-loading was successful finished
            if(!this.propertyLoadingFinished){
                // property-loading must have been interrupted - start again!
                this.reloadProperties()
            } else {
                // stop spinner and set info header
                runOnUiThread {
                    this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE

                    //val deviceHeaderData = DeviceInfoHeaderData()
                    //deviceHeaderData.message = getString(R.string.DMA_Ready)
                    //this.showNotificationHeaderAndPostMessage(deviceHeaderData)

                    //this.hideNotificationHeader()
                }
            }
        }
    }

    override fun onDeviceReadyForCommunication() {
        super.onDeviceReadyForCommunication()
        // this callback will only invoked in this activity if this is a re-connect-process (the initial connection attempt will be executed in the loading activity!)

        // make sure to hide the loading circle
        runOnUiThread {
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
        }
    }

    override fun onConnectionError(errorID: Int) {
        super.onConnectionError(errorID)

        when(errorID){
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE -> {
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE -> {
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
        }
    }

    override fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>) {
        super.onUIAdaptableArrayListGenerationComplete(UIArray)

        // ?? description!


        this.propertyLoadingFinished = true


        runOnUiThread {

            // TODO: ??
            //this.deviceHeaderNotificationContainer.visibility = View.GONE

            this.hideNotificationHeader()

            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE




            //this.setDeviceInfoHeader(43, getString(R.string.DMA_Ready))


            // TODO: test!
            //this.devicePropertyListViewAdapter.notifyDataSetChanged()
        }
    }

    override fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation) {
        super.onUIAdaptableArrayListItemAdded(item)

        // add the data to the UI-List
        this.propertyList.add(item)

        try {
            runOnUiThread {
                this.devicePropertyListViewAdapter.notifyItemInserted(item.globalIndex)
            }
        }
        catch (e: Exception){
            Log.e("onUIAddItem", "Error while adding an item to property list: ${e.message}")

            Executors.newSingleThreadScheduledExecutor().schedule({
                this.devicePropertyListViewAdapter.notifyItemInserted(item.globalIndex)
            }, 500, TimeUnit.MILLISECONDS)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)

            runOnUiThread {
                try {
                    val uIElement =
                        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                            UIAdapterElementIndex
                        )

                    // temp:
                    //Log.e("onSimpleStateChanged", "Callback invoked: type is: ${uIElement.propertyType}")


                    val linearLayout =
                        this.devicePropertyListLayoutManager.findViewByPosition(
                            UIAdapterElementIndex
                        ) as? LinearLayout

                    // update the appropriate element
                    when (uIElement.propertyType) {
                        PROPERTY_TYPE_BUTTON -> {
                            // NOTE: this is not used, because the button has no state (by now)
                        }
                        PROPERTY_TYPE_SWITCH -> {
//                    val switch =
//                        linearLayout?.findViewById<SwitchCompat>(R.id.elementSwitch)
//                    switch?.isChecked = (newState != 0)


                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {

                            // TODO: set textview value to the newState!

                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)

                            /*
                    val seekBar =
                        linearLayout?.findViewById<SeekBar>(R.id.elementSeekBar)
                    seekBar?.progress = get8BitValueAsPercent(newState)
                    */
                        }
                        PROPERTY_TYPE_LEVEL_INDICATOR -> {
//                    val textView =
//                        linearLayout?.findViewById<TextView>(R.id.levelIndicationTextView)
//                    val percentageLevelPropertyGenerator = PercentageLevelPropertyGenerator(
//                        get8BitValueAsPercent(newState)
//                    )
//                    textView?.setTextColor(percentageLevelPropertyGenerator.colorID)
//                    textView?.text = percentageLevelPropertyGenerator.percentageString
                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                        }
                        PROPERTY_TYPE_OPTION_SELECTOR -> {
                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                        }
                        else -> {
                            if(verboseLog){
                                Log.e("onSimpleStateChanged", "Callback invoked. Invalid propertyType detected.\nType was: ${uIElement.propertyType}\nUIElementIndex: $UIAdapterElementIndex")
                            }
                            (applicationContext as ApplicationProperty).logControl("E: Simple Property State changed. Invalid property type detected. - Type was: ${uIElement.propertyType} - UIElementIndex: $UIAdapterElementIndex")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(
                        "DMA:onSimplePropState",
                        "DeviceMainActivity - onSimplePropertyState changed. Exception: $e"
                    )
                }
            }
    }

    override fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData) {
        this.showNotificationHeaderAndPostMessage(deviceHeaderData)
    }

    override fun onPropertyInvalidated() {
        if(verboseLog){
            Log.d("DMA:PropInvalidated", "Property was invalidated from remote device -> Reload Properties")
        }
        (applicationContext as ApplicationProperty).logControl("I: Property was invalidated from remote device -> Reload Properties")

        this.reloadProperties()
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        // no complex property page is open
        return -1
    }

    override fun onUIAdaptableArrayItemChanged(index: Int) {
        runOnUiThread {
            devicePropertyListViewAdapter.notifyItemChanged(index)
        }
    }

    // device property list adapter:
    class DevicePropertyListAdapter(
        private val devicePropertyAdapter: ArrayList<DevicePropertyListContentInformation>,
        private val itemClickListener: OnPropertyClickListener,
        private val activityContext: Context,
        private val callingActivity: DeviceMainActivity
    ) : RecyclerView.Adapter<DevicePropertyListAdapter.DPLViewHolder>() {



        class DPLViewHolder(
            val linearLayout: LinearLayout)
            : RecyclerView.ViewHolder(linearLayout) {

//            fun bind(data: DevicePropertyListContentInformation, itemClick: OnPropertyClickListener, position: Int){
//                itemClick.onPropertyClicked(position, data)
//            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DPLViewHolder {
            val linearLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.device_property_list_element, parent, false) as LinearLayout

            return DPLViewHolder(linearLayout)
        }

        //@SuppressLint("CutPasteId")
        override fun onBindViewHolder(holder: DPLViewHolder, position: Int) {

            val elementToRender = devicePropertyAdapter.elementAt(position)
            val rootContentHolder = holder.linearLayout

            when(elementToRender.elementType) {
                UNDEFINED_ELEMENT -> {
                    // should not happen
                    // set the text for the element
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                        .apply {
                            //visibility = View.VISIBLE
                            text = activityContext.getString(R.string.DMA_ErrorElementText)
                            textSize = 16F
                            setTypeface(typeface, Typeface.BOLD)
                        }
                }
                GROUP_ELEMENT -> {
                    // This is a Group-Element. Visible Elements: Image, Textview. Background: group element background
                    // The image and the textview are visible by default, so nothing must be set to visible.

                    // set group-header background
                    rootContentHolder.background = AppCompatResources.getDrawable(
                        activityContext,
                        R.drawable.property_list_group_header_element_background
                    )
                    // set the image for the element
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage)
                        .apply {
                            setBackgroundResource(
                                resourceIdForImageId(elementToRender.imageID)
                            )
                        }
                    // set the text for the element
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                        .apply {
                            //visibility = View.VISIBLE
                            text = elementToRender.elementText
                            textSize = 18F
                            setTypeface(typeface, Typeface.BOLD)
                        }
                }
                PROPERTY_ELEMENT -> {
                    // this is a property element

                    // if this is a navigatable property, show the nav-arrow
                    if (elementToRender.canNavigateForward) {
                        holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility =
                            View.VISIBLE
                    }

                    // set the background appropriate to the group-status
                    if (!elementToRender.isGroupMember) {
                        // this is a single property element
                        rootContentHolder.background =
                            AppCompatResources.getDrawable(
                                activityContext,
                                R.drawable.single_property_list_element_background
                            )
                    } else {
                        // the element is part of a group, check if this is the last element in the group
                        // check the next item
                        if (elementToRender.isLastInGroup) {
                            rootContentHolder.background =
                                AppCompatResources.getDrawable(
                                    activityContext,
                                    R.drawable.inside_group_property_last_list_element_background
                                )
                        } else {
                            rootContentHolder.background =
                                AppCompatResources.getDrawable(
                                    activityContext,
                                    R.drawable.inside_group_property_list_element_background
                                )
                        }
                    }

                    // set the appropriate image for the imageID
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage)
                        .apply {
                            setBackgroundResource(
                                resourceIdForImageId(elementToRender.imageID)
                            )
                        }

                    // TODO: hide the image-view if the imageID is not set or show default???

                    // set the appropriate elements for the type of the property:
                    when (elementToRender.propertyType) {
                        -1 -> return // must be error
                        0 -> return // must be error
                        PROPERTY_TYPE_BUTTON -> {

                            // check if dual description is required
                            val dualDescription = checkForDualDescriptor(elementToRender.elementText)

                            if(dualDescription.isDual){
                                val textView =
                                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                                textView.text = dualDescription.elementText
                            }

                            // apply to the button
                            holder.linearLayout.findViewById<Button>(R.id.elementButton).apply {
                                // show the button
                                visibility = View.VISIBLE
                                // set the text of the button
                                text = if(dualDescription.isDual){
                                    dualDescription.actionText
                                } else {
                                    elementToRender.elementText
                                }
                                // set the onClick handler
                                setOnClickListener {
                                    itemClickListener.onPropertyElementButtonClick(
                                        elementToRender.internalElementIndex,
                                        elementToRender
                                    )
                                }
                            }
                        }
                        PROPERTY_TYPE_SWITCH -> {
                            // get the switch
                            val switch =
                                holder.linearLayout.findViewById<SwitchCompat>(R.id.elementSwitch)
                            // show the switch
                            switch.visibility = View.VISIBLE
                            // set the onClick handler
                            switch.setOnClickListener {
                                itemClickListener.onPropertyElementSwitchClick(
                                    elementToRender.internalElementIndex,
                                    elementToRender,
                                    switch
                                )
                            }
                            switch.isChecked = elementToRender.simplePropertyState > 0

                            // show the text-view
                            val textView =
                                holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
//                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = elementToRender.elementText
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {
                            // TODO: show this in a popup!!!
                            // show seek-bar layout container
                            //holder.linearLayout.findViewById<LinearLayout>(R.id.seekBarContainer).visibility = View.VISIBLE
                            // set the handler for the seekBar
                            //elementToRender.handler = callingActivity


                            // test!!!!
                            /*holder.linearLayout.findViewById<SeekBar>(R.id.elementSeekBar).apply {
                                this.setOnSeekBarChangeListener(elementToRender)
                                this.progress =
                                    get8BitValueAsPercent(elementToRender.simplePropertyState)
                            }*/

                            // TODO: show the button and display the value as button-text!


                            // show the property text-view
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                                .apply {
                                    //visibility = View.VISIBLE
                                    // set the text
                                    text = elementToRender.elementText
                                }
                            // show the level in the button
                            val percentageLevelPropertyGenerator =
                                PercentageLevelPropertyGenerator(elementToRender.simplePropertyState)

                            // TODO: add the possibility for the user to display other values than percentage values!?

                            // show the level text-view
                            holder.linearLayout.findViewById<Button>(R.id.elementButton).apply {
                                visibility = View.VISIBLE
                                text = percentageLevelPropertyGenerator.percentageString

                                setOnClickListener {
                                    itemClickListener.onPropertyLevelSelectButtonClick(
                                        elementToRender.internalElementIndex,
                                        elementToRender
                                    )
                                }

                                //setTextColor(percentageLevelPropertyGenerator.colorID)

                            }

                        }
                        PROPERTY_TYPE_OPTION_SELECTOR -> {

                            val optionSelectorStrings =
                                decryptOptionSelectorString(elementToRender.elementText)

                            // NOTE: the first string is the element description!

                            if (optionSelectorStrings.isNotEmpty()) {

                                val textView =
                                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                                textView.text = optionSelectorStrings.elementAt(0)

                                // remove the first element, since this is the descriptor-text
                                optionSelectorStrings.removeAt(0)

                                // apply to the button
                                holder.linearLayout.findViewById<Button>(R.id.elementButton).apply {
                                    // show the button
                                    visibility = View.VISIBLE

                                    // set the text of the button (if valid)
                                    if (elementToRender.simplePropertyState < optionSelectorStrings.size) {
                                        text =
                                            optionSelectorStrings.elementAt(elementToRender.simplePropertyState)

                                        // set the onClick handler
                                        setOnClickListener {
                                            itemClickListener.onPropertyOptionSelectButtonClick(
                                                elementToRender.globalIndex,
                                                elementToRender
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        PROPERTY_TYPE_LEVEL_INDICATOR -> {
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                                .apply {
                                    // set visibility
                                    //visibility = View.VISIBLE
                                    // set the text
                                    text = elementToRender.elementText
                                }

                            // show a level indication e.g. "96%"
                            val percentageLevelPropertyGenerator =
                                PercentageLevelPropertyGenerator(elementToRender.simplePropertyState)

                            holder.linearLayout.findViewById<TextView>(R.id.levelIndicationTextView)
                                .apply {
                                    visibility = View.VISIBLE
                                    text = percentageLevelPropertyGenerator.percentageString
                                    setTextColor(percentageLevelPropertyGenerator.colorID)
                                }
                        }
                        PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> {

                            // TODO: integrate textcolor???

                            // show the textView
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                                .apply {
                                    //visibility = View.VISIBLE
                                    // set the text
                                    text = elementToRender.elementText
                                }
                        }
                        else -> {
                            // must be complex type!

                            // set handler
                            holder.linearLayout.setOnClickListener {
                                itemClickListener.onNavigatableElementClick(
                                    position,
                                    elementToRender
                                )
                            }

                            // show the textView
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                                .apply {
                                    //visibility = View.VISIBLE
                                    // set the text
                                    text = elementToRender.elementText
                                }
                            // show the navigate arrow
                            holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility =
                                View.VISIBLE
                        }
                    }
                }
            }
            // bind it!
            //holder.bind(elementToRender, itemClickListener, position)
        }

        override fun getItemCount(): Int {
            return devicePropertyAdapter.size
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }

        fun getElementAt(index: Int) : DevicePropertyListContentInformation{
            return devicePropertyAdapter.elementAt(index)
        }
    }
}
