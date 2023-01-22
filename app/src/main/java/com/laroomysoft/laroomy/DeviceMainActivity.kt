package com.laroomysoft.laroomy

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback, OnPropertyClickListener, SeekBar.OnSeekBarChangeListener {

    // device property list elements
    private lateinit var devicePropertyListRecyclerView: DeviceMainPropertyRecyclerView
    private lateinit var devicePropertyListViewAdapter: RecyclerView.Adapter<*>
    private lateinit var devicePropertyListLayoutManager: RecyclerView.LayoutManager
    //private var devicePropertyList= ArrayList<DevicePropertyListContentInformation>()

    private lateinit var deviceTypeHeaderTextView: AppCompatTextView
    private lateinit var deviceConnectionStatusTextView: AppCompatTextView
    private lateinit var deviceMenuButton: AppCompatImageButton
    
    private lateinit var signalStrengthImageView: AppCompatImageView

    //private lateinit var deviceHeaderNotificationImageView: AppCompatImageView

    private lateinit var deviceHeaderNotificationTextView: AppCompatTextView
    private lateinit var deviceHeaderNotificationContainer: ConstraintLayout
    private lateinit var deviceHeaderClickOverlayContainer: ConstraintLayout
    private lateinit var deviceHeaderNameContainer: ConstraintLayout

    //private lateinit var deviceSettingsButton: AppCompatImageButton

    private lateinit var popUpWindow: PopupWindow
    private lateinit var deviceMenuPopUpWindow: PopupWindow

    private var activityWasSuspended = false
    //private var buttonRecoveryRequired = false
    private var restoreIndex = -1
    //private var deviceImageResourceId = -1
    private var currentSeekBarPopUpRelatedGlobalIndex = -1
    private var currentSignalStrength = 0
    private var propertyLoadingFinished = true
    private var levelSelectorPopUpOpen = false
    private var optionSelectorPopUpOpen = false
    private var deviceMenuOpen = false
    private var expectedConnectionLoss = false
    private var propertyStateUpdateRequired = false
    private var scrollToTop = false
    private var deviceHeaderClickAction = DEV_HEADER_CLICK_ACTION_NONE
    
    private val slideUpdateData = SuccessiveSimpleUpdateStorage()
    private val optionSelectUpdateData = SuccessiveSimpleUpdateStorage()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)
        
        // register onBackPressed event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })

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
        this.deviceHeaderNameContainer = findViewById(R.id.deviceMainActivityDeviceHeaderNameContainer)
        
        this.signalStrengthImageView = findViewById(R.id.deviceMainActivitySignalStrengthIndicationImageView)
        
        // add on click listener to name-container
        this.deviceHeaderNameContainer.setOnClickListener {
            this.onReconnectDevice()
        }

        this.deviceHeaderNotificationTextView = findViewById(R.id.deviceMainActivityDeviceInfoSubHeaderTextView)
        this.deviceHeaderNotificationContainer = findViewById(R.id.deviceInfoSubHeaderContainer)
        this.deviceHeaderClickOverlayContainer = findViewById<ConstraintLayout?>(R.id.deviceMainActivityDeviceInfoSubHeaderSubContainer).apply {
            setOnClickListener {
                onDeviceInfoHeaderClick()
            }
        }

        // init recycler view layout manager
        this.devicePropertyListLayoutManager = object : LinearLayoutManager(this) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return false
            }

            override fun onItemsAdded(
                recyclerView: RecyclerView,
                positionStart: Int,
                itemCount: Int
            ) {
                super.onItemsAdded(recyclerView, positionStart, itemCount)

                if(scrollToTop){
                    scrollToTop = false
                    this.scrollToPosition(0)
                }
            }
        }

        // bind array to adapter
        this.devicePropertyListViewAdapter =
            DevicePropertyListAdapter(
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList,
                this
            )

        // bind the elements to the recycler
        this.devicePropertyListRecyclerView =
            findViewById<DeviceMainPropertyRecyclerView>(R.id.devicePropertyListView)
                .apply {
                    //setHasFixedSize(true)
                    layoutManager = devicePropertyListLayoutManager
                    adapter = devicePropertyListViewAdapter
                    itemAnimator = DevicePropertyListItemAnimator()
                    
                    // save context to application property
                    (applicationContext as ApplicationProperty).cViewContext = this.context
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
            // mark this connection break as expected
            this.expectedConnectionLoss = true

            // suspend connection
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()
            setUIConnectionStatus(STATUS_DISCONNECTED)
            this.activityWasSuspended = true

        } else {
            // reset parameter:
            (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = false
        }
    }
    
    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()

        if(verboseLog) {
            Log.d(
                "M:onResume",
                "Activity resumed. Previous loading done: ${this.activityWasSuspended}"
            )
        }

        // reset parameter(s) anyway
        this.expectedConnectionLoss = false

        // check if bluetooth was disabled while the app was in suspended state
        when(ApplicationProperty.bluetoothConnectionManager.checkBluetoothEnabled()){
            BLE_BLUETOOTH_PERMISSION_MISSING -> {
                // permission was revoked while app was in suspended state
                Log.e("DMA:onResume", "Bluetooth permission was revoked while app was in suspended mode.")
                (applicationContext as ApplicationProperty).logControl("E: Bluetooth permission was revoked while app was in suspended mode.")
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
            BLE_IS_DISABLED -> {
                // bluetooth was disabled while app was in suspended state
                Log.e("DMA:onResume", "Bluetooth was disabled while app was in suspended mode.")
                (applicationContext as ApplicationProperty).logControl("E: Bluetooth was disabled while app was in suspended mode.")
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
            else -> {
                // ******************************************************************************************
                // check if this callback will be invoked due to a back-navigation from a property sub-page
                // or if it was invoked on creation or a resume from outside of the application
                // ******************************************************************************************
                if ((this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage) {
                    // **************************************************************************************
                    // THIS IS A BACK NAVIGATION FROM A PROPERTY SUB-PAGE
                    // **************************************************************************************
                    
                    // check if a close-device command was received on the sub-page
                    if((applicationContext as ApplicationProperty).closeDeviceRequested){
                        (applicationContext as ApplicationProperty).closeDeviceRequested = false
                        this.onCloseDeviceRequested()
                        // exit immediately
                        return
                    }

                    // if the device was disconnected on a property sub-page, navigate back with delay
                    if (!ApplicationProperty.bluetoothConnectionManager.isConnected) {

                        // set UI visual state
                        resetSelectedItemBackground()
                        setUIConnectionStatus(STATUS_DISCONNECTED)

                        // schedule back-navigation
                        Handler(Looper.getMainLooper()).postDelayed({
                            (applicationContext as ApplicationProperty).resetPropertyControlParameter()
                            ApplicationProperty.bluetoothConnectionManager.clear()
                            finish()
                        }, 1000)

                    } else {
                        // realign the context objects to the bluetoothManager
                        ApplicationProperty.bluetoothConnectionManager.setBleEventHandler(this)
                        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                        // notify the remote device that the user navigated back to the device main page
                        if((applicationContext as ApplicationProperty).delayedNavigationNotificationRequired) {
                            (applicationContext as ApplicationProperty).delayedNavigationNotificationRequired = false
                            // use a delay, because the activity navigated from requested this (normally it sends data to the device before closing)
                            Executors.newSingleThreadScheduledExecutor().schedule({
                                ApplicationProperty.bluetoothConnectionManager.notifyBackNavigationToDeviceMainPage()
                            }, 300, TimeUnit.MILLISECONDS)
                        } else {
                            ApplicationProperty.bluetoothConnectionManager.notifyBackNavigationToDeviceMainPage()
                        }

                        // set property-item to normal background
                        resetSelectedItemBackground()

                        // reset the parameter
                        restoreIndex = -1
                        (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = false

                        // check if the whole property was invalidated
                        if ((this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage) {

                            // reload properties from remote device
                            this.reloadProperties()

                            // reset all parameter regarding the update-functionality caused by navigation
                            (this.applicationContext as ApplicationProperty).propertyInvalidatedOnSubPage = false
                            (this.applicationContext as ApplicationProperty).uiAdapterChanged = false
                            (this.applicationContext as ApplicationProperty).uiAdapterInvalidatedOnPropertySubPage = false
                        } else {
                            // check if the adapter data is valid or not, if not update
                            if((this.applicationContext as ApplicationProperty).uiAdapterInvalidatedOnPropertySubPage){
                                // this is the last resort, but the adapter data must be considered as completely out of date
                                if (verboseLog) {
                                    Log.d(
                                        "DMA:onResume",
                                        "UI-Adapter was invalidated. Update complete data-set!!"
                                    )
                                }
                                this.devicePropertyListViewAdapter.notifyDataSetChanged()
                                (this.applicationContext as ApplicationProperty).uiAdapterInvalidatedOnPropertySubPage = false
                            } else {
                                // one or more single items in the adapter have changed, so update them
                                if ((this.applicationContext as ApplicationProperty).uiAdapterChanged) {
                                    // reset parameter
                                    (this.applicationContext as ApplicationProperty).uiAdapterChanged =
                                        false

                                    if (verboseLog) {
                                        Log.d(
                                            "DMA:onResume",
                                            "UI-Adapter has changed. Start updating elements with the changed marker:"
                                        )
                                    }

                                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                                        if (devicePropertyListContentInformation.hasChanged) {
                                            if (verboseLog) {
                                                Log.d(
                                                    "DMA:onResume",
                                                    "Updating element: ${devicePropertyListContentInformation.elementText} with internal index: ${devicePropertyListContentInformation.internalElementIndex}"
                                                )
                                            }
                                            ApplicationProperty.bluetoothConnectionManager.uIAdapterList[index].hasChanged =
                                                false
                                            this.devicePropertyListViewAdapter.notifyItemChanged(index)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ********************************************************************
                    // CREATION OR RESUME ACTION
                    // ********************************************************************

                    // make sure to set the right name for the device
                    try {
                        var deviceName: String
                        // try to get the device name from the bluetooth device object (could be empty)
                        deviceName =
                            ApplicationProperty.bluetoothConnectionManager.currentDevice?.name ?: ""
                        // check if there is a name
                        if(deviceName.isEmpty()){
                            // there is no name provided, so lookup in the friendly device list for a name
                            deviceName =
                                (applicationContext as ApplicationProperty)
                                    .addedDevices
                                    .lookupForDeviceNameWithAddress(ApplicationProperty.bluetoothConnectionManager.currentDevice?.address ?: "")
                        }
                        // check if the the name must be truncated due to an image appendix and assign the result to the view
                        this.deviceTypeHeaderTextView.text =
                            removeImageDefinitionFromNameStringIfApplicable(
                                deviceName,
                                (applicationContext as ApplicationProperty).isPremiumAppVersion
                            )
                    } catch (e: SecurityException) {
                        Log.e("DMA:onResume", "Exception while trying to access the current device: $e")
                        (applicationContext as ApplicationProperty).logControl("E: onResume: Exception while trying to access the current device: $e")
                        ApplicationProperty.bluetoothConnectionManager.clear()
                        finish()
                        return
                    }

                    // check if this is a call on creation or resume:
                    if (this.activityWasSuspended) {
                        // must be RESUME

                        // try to reconnect
                        ApplicationProperty.bluetoothConnectionManager.resumeConnection()
                        this.activityWasSuspended = false

                    } else {
                        // must be CREATION
                        // Update the connection status
                        setUIConnectionStatus(
                            if(ApplicationProperty.bluetoothConnectionManager.isConnected){
                                STATUS_CONNECTED
                            } else {
                                STATUS_DISCONNECTED
                            }
                        )
                    }
                }
            }
        }
    }
    
    private fun handleBackEvent(){
        // the loading activity is terminated, so the start(main)-Activity will be invoked
        ApplicationProperty.bluetoothConnectionManager.clear()
        finish()
    }

    private fun setPropertyToSelectedState(index: Int){
        val element =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
            //this.propertyList.elementAt(index)

        if(element.elementType == PROPERTY_ELEMENT){
            val rootLayoutElement =
                this.devicePropertyListLayoutManager.findViewByPosition(index) as? LinearLayout

            if(element.isGroupMember){
                if(element.isLastInGroup){
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(this, R.drawable.p_group_last_element_sel_bkgnd)
                } else {
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(this, R.drawable.p_group_element_sel_bkgnd)
                }
            } else {
                rootLayoutElement?.background =
                    AppCompatResources.getDrawable(this, R.drawable.single_property_list_element_selected_background)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadProperties(){
        if(verboseLog) {
            Log.d(
                "M:DMA:reloadProperties",
                "Reloading Properties requested!"
            )
        }
        (applicationContext as ApplicationProperty).logControl("I: Property-Reload requested.")
    
        // at first update the UI
        runOnUiThread {
            this.propertyLoadingFinished = false
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.clear()
            this.devicePropertyListViewAdapter.notifyDataSetChanged()
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

            val deviceHeaderData = DeviceInfoHeaderData()
            deviceHeaderData.message = getString(R.string.DMA_LoadingPropertiesUserInfo)
            deviceHeaderData.displayTime = 9

            this.showNotificationHeaderAndPostMessage(deviceHeaderData)
        }
        // then start the reload process
        Executors.newSingleThreadScheduledExecutor().schedule({
            ApplicationProperty.bluetoothConnectionManager.reloadProperties()
        }, 500, TimeUnit.MILLISECONDS)
    }

    override fun onPropertyElementButtonClick(index: Int) {
        val devicePropertyListContentInformation =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

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
            makeSimplePropertyExecutionString(
                devicePropertyListContentInformation.internalElementIndex,
                0
            )
        )
    }

    override fun onPropertyElementSwitchClick(index: Int, state: Boolean) {
        val devicePropertyListContentInformation =
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

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

        val c = when(state){
            true -> 1
            else -> 0
        }
        // send state change
        ApplicationProperty.bluetoothConnectionManager.sendData(
            makeSimplePropertyExecutionString(devicePropertyListContentInformation.internalElementIndex, c)
        )
        // update UI List
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).apply {
            this.simplePropertyState = c
            //this.update() - not necessary on a switch
        }
        // update internal property state
        ApplicationProperty.bluetoothConnectionManager.updateInternalSimplePropertyState(devicePropertyListContentInformation.internalElementIndex, c)
    }

    @SuppressLint("InflateParams")
    override fun onPropertyLevelSelectButtonClick(index: Int) {

        val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

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
        this.currentSeekBarPopUpRelatedGlobalIndex = devicePropertyListContentInformation.globalIndex

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
            currentSeekBarPopUpRelatedGlobalIndex = -1
        }

        this.popUpWindow.showAtLocation(this.devicePropertyListRecyclerView, Gravity.CENTER, 0, 0)

        // set the appropriate image and seekbar position
        this.popUpWindow.contentView.findViewById<AppCompatImageView>(R.id.levelSelectorPopUpImageView).setImageResource(
            resourceIdForImageId(devicePropertyListContentInformation.imageID, PROPERTY_ELEMENT, (applicationContext as ApplicationProperty).isPremiumAppVersion)
        )
        val percentageLevelPropertyGenerator =
            PercentageLevelPropertyGenerator(devicePropertyListContentInformation.simplePropertyState)
        this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.levelSelectorPopUpTextView).text =
            percentageLevelPropertyGenerator.percentageString

        // set seekbar properties
        this.popUpWindow.contentView.findViewById<SeekBar>(R.id.levelSelectorPopUpSeekbar).apply {
            this.progress = percentageLevelPropertyGenerator.percentageValue
            this.setOnSeekBarChangeListener(this@DeviceMainActivity)
        }
    }

    @SuppressLint("InflateParams")
    override fun onPropertyOptionSelectButtonClick(index: Int) {
        
        try {
    
            val devicePropertyListContentInformation =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
    
            if (verboseLog) {
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
            if (this.optionSelectorPopUpOpen) {
                return
            }
    
            // get the options array
            val options =
                decryptOptionSelectorString(devicePropertyListContentInformation.elementDescriptorText)
    
            if (options.isEmpty()) {
                Log.e(
                    "decryptOptions",
                    "Error on decrypting options for option-selector from property descriptor!"
                )
                (applicationContext as ApplicationProperty).logControl("E: Error on decrypting options for option-selector from property descriptor!")
                return
            } else {
                if (options.size == 1) {
                    Log.e(
                        "optionSelectorPopup",
                        "Error: no option strings for options selector. Popup creation discarded."
                    )
                    (applicationContext as ApplicationProperty).logControl("E: Error: no option strings for option-selector. Popup creation discarded.")
                    return
                }
            }
    
            // shade the background
            this.devicePropertyListRecyclerView.alpha = 0.2f
    
            // block the activation of background (list) elements during popup-lifecycle
            this.optionSelectorPopUpOpen = true
    
            val layoutInflater =
                getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
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
    
            this.popUpWindow.showAtLocation(
                this.devicePropertyListRecyclerView,
                Gravity.CENTER,
                0,
                0
            )
    
            // set the appropriate image and option selection
            this.popUpWindow.contentView.findViewById<AppCompatImageView>(R.id.optionSelectorPopUpImageView)
                .setImageResource(
                    resourceIdForImageId(
                        devicePropertyListContentInformation.imageID,
                        PROPERTY_ELEMENT,
                        (applicationContext as ApplicationProperty).isPremiumAppVersion
                    )
                )
    
            this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.optionSelectorPopUpTextView).text =
                options.elementAt(0)
    
            options.removeAt(0)
    
            var strArray = arrayOf<String>()
            options.forEach {
                strArray += it
            }
    
            // set picker values
            this.popUpWindow.contentView.findViewById<NumberPicker>(R.id.optionSelectorPopUpNumberPicker)
                .apply {
                    minValue = 0
                    maxValue = options.size - 1
                    displayedValues = strArray
            
                    if (devicePropertyListContentInformation.simplePropertyState < options.size) {
                        value = devicePropertyListContentInformation.simplePropertyState
                    }
            
                    setOnValueChangedListener { _, _, newVal ->
                
                        // update list element
                        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
                            .apply {
                                this.simplePropertyState = newVal
                                this.update(devicePropertyListRecyclerView.context)
                            }
                        devicePropertyListViewAdapter.notifyItemChanged(index)
                
                        // update internal simple state
                        ApplicationProperty.bluetoothConnectionManager.updateInternalSimplePropertyState(
                            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                                index
                            ).internalElementIndex,
                            newVal
                        )
                
                        // control successive transmissions
                        if (!optionSelectUpdateData.isHandled) {
                            // only save the value, because there is a transmission pending
                            optionSelectUpdateData.value = newVal
                        } else {
                            // if there is no transmission pending, send the data delayed
                            optionSelectUpdateData.value = newVal
                            optionSelectUpdateData.isHandled = false
                            optionSelectUpdateData.eIndex =
                                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                                    index
                                ).internalElementIndex
                    
                            Executors.newSingleThreadScheduledExecutor().schedule(
                                {
                                    ApplicationProperty.bluetoothConnectionManager.sendData(
                                        makeSimplePropertyExecutionString(
                                            optionSelectUpdateData.eIndex,
                                            optionSelectUpdateData.value
                                        )
                                    )
                                    optionSelectUpdateData.isHandled = true
                            
                                }, 150, TimeUnit.MILLISECONDS
                            )
                        }
                    }
                }
        } catch (e: java.lang.Exception){
            Log.e("DeviceMainActivity", "onPropertyOptionSelectButtonClick Exception: $e")
            if(this.optionSelectorPopUpOpen){
                this.popUpWindow.dismiss()
            }
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        this.onSeekBarPositionChange(
            this.currentSeekBarPopUpRelatedGlobalIndex,
            progress,
            SEEK_BAR_PROGRESS_CHANGING
        )
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        this.onSeekBarPositionChange(this.currentSeekBarPopUpRelatedGlobalIndex, -1, SEEK_BAR_START_TRACK)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        this.onSeekBarPositionChange(this.currentSeekBarPopUpRelatedGlobalIndex, -1, SEEK_BAR_STOP_TRACK)
    }


    override fun onSeekBarPositionChange(
        index: Int,
        newValue: Int,
        changeType: Int
    ) {
        try {
            if (verboseLog) {
                val devicePropertyListContentInformation =
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
        
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
    
            if (changeType == SEEK_BAR_PROGRESS_CHANGING) {
                // calculate bit-value
                val bitValue =
                    percentTo8Bit(newValue)
        
                // update list element
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
                    .apply {
                        this.simplePropertyState = bitValue
                        this.update(devicePropertyListRecyclerView.context)
                    }
                this.devicePropertyListViewAdapter.notifyItemChanged(index)
        
                // update internal simple state
                ApplicationProperty.bluetoothConnectionManager.updateInternalSimplePropertyState(
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex,
                    bitValue
                )
        
                if (levelSelectorPopUpOpen) {
                    val seekBarText = "$newValue%"
                    // set the seekbar and textview properties
                    this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.levelSelectorPopUpTextView).text =
                        seekBarText
                    this.popUpWindow.contentView.findViewById<SeekBar>(R.id.levelSelectorPopUpSeekbar).progress =
                        newValue
                }
        
                // handle successive transmission
                if (!this.slideUpdateData.isHandled) {
                    // only save the value, because there is a transmission pending
                    this.slideUpdateData.value = bitValue
                } else {
                    // if there is no transmission pending, send the data delayed
                    this.slideUpdateData.value = bitValue
                    this.slideUpdateData.isHandled = false
                    this.slideUpdateData.eIndex =
                        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex
            
                    Executors.newSingleThreadScheduledExecutor().schedule(
                        {
                            ApplicationProperty.bluetoothConnectionManager.sendData(
                                makeSimplePropertyExecutionString(
                                    slideUpdateData.eIndex,
                                    slideUpdateData.value
                                )
                            )
                            slideUpdateData.isHandled = true
                    
                        }, 150, TimeUnit.MILLISECONDS
                    )
                }
            }
        } catch (e: java.lang.Exception){
            Log.e("DeviceMainActivity", "onSeekBarPositionChange Exception: $e")
            if(levelSelectorPopUpOpen){
                this.popUpWindow.dismiss()
            }
        }
    }

    override fun onNavigatableElementClick(index: Int) {
        try {
            val devicePropertyListContentInformation =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
    
            if (verboseLog) {
                Log.d(
                    "M:CB:onNavElementClk",
                    "Property element was clicked. Element-Type is Complex/Navigate forward at index: $index\n\nData is:\n" +
                            "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                            "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                            "Element-ID: ${devicePropertyListContentInformation.internalElementIndex}\n" +
                            "Element-Index: ${devicePropertyListContentInformation.globalIndex}"
                )
            }
    
            // if item is disabled skip execution of this method
            if (!devicePropertyListContentInformation.isEnabled) {
                if (verboseLog) {
                    Log.d(
                        "M:CB:onNavElementClk",
                        "Navigatable element was clicked. Element is disabled. Skip execution!"
                    )
                }
                return
            }
    
            // check if the complex state loop is pending (if so the complex state data is not valid)
            if (!ApplicationProperty.bluetoothConnectionManager.isInitialComplexStateLoopFinished) {
                val infoData = DeviceInfoHeaderData()
                infoData.message = getString(R.string.DMA_WaitForComplexClickMessage)
                infoData.type = USERMESSAGE_TYPE_INFO
                infoData.displayTime = 0
                this.showNotificationHeaderAndPostMessage(infoData)
                return
            }
    
            // set it to selected color
            setPropertyToSelectedState(index)
    
            // save the index of the highlighted item to reset it on back-navigation
            restoreIndex = index
    
            // navigate
            when (devicePropertyListContentInformation.propertyType) {
                COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the RGB Page
                    val intent = Intent(this@DeviceMainActivity, RGBControlActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the extended level selector page
                    val intent =
                        Intent(this@DeviceMainActivity, ExtendedLevelSelectorActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the time selector page with single-select-mode
                    val intent =
                        Intent(this@DeviceMainActivity, SimpleTimeSelectorActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the time-frame selector page
                    val intent =
                        Intent(this@DeviceMainActivity, TimeFrameSelectorActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_DATE_SELECTOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the date selector page
                    val intent =
                        Intent(this@DeviceMainActivity, DateSelectorActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the unlock control page
                    val intent = Intent(this@DeviceMainActivity, UnlockControlActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the navigator page
                    val intent =
                        Intent(this@DeviceMainActivity, NavigatorControlActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_BARGRAPH -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the barGraph page
                    val intent = Intent(this@DeviceMainActivity, BarGraphActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_LINEGRAPH -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the lineGraph page
                    val intent = Intent(this@DeviceMainActivity, LineGraphActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_STRING_INTERROGATOR -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the string interrogator page
                    val intent =
                        Intent(this@DeviceMainActivity, StringInterrogatorActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                COMPLEX_PROPERTY_TYPE_ID_TEXT_LIST_PRESENTER -> {
                    // prevent the normal "onPause" execution
                    (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution =
                        true
                    // navigate to the text-list-presenter page
                    val intent =
                        Intent(this@DeviceMainActivity, TextListPresenterActivity::class.java)
                    intent.putExtra(
                        "elementID",
                        devicePropertyListContentInformation.internalElementIndex
                    )
                    intent.putExtra(
                        "globalElementIndex",
                        devicePropertyListContentInformation.globalIndex
                    )
                    intent.putExtra("isStandAlonePropertyMode", false)
                    startActivity(intent)
                    overridePendingTransition(
                        R.anim.start_activity_slide_animation_in,
                        R.anim.start_activity_slide_animation_out
                    )
                }
                else -> {
                    // what to do here??
                }
            }
        } catch (e: Exception){
            Log.e("DeviceMainActivity", "(onNavigatableElementClick) Fatal Exception: ${e.message}")
            (applicationContext as ApplicationProperty).logControl("E: (onNavigatableElementClick) Fatal Exception: ${e.message}")
        }
    }

    private fun resetSelectedItemBackground(){
        // reset the selected item background if necessary
        if (restoreIndex >= 0 && ApplicationProperty.bluetoothConnectionManager.uIAdapterList.size > 0) {
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
                            R.drawable.p_group_last_element_bkgnd
                        )
                } else {
                    rootLayoutElement?.background =
                        AppCompatResources.getDrawable(
                            this,
                            R.drawable.p_group_element_bkgnd
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
    
    private fun onReconnectDevice(){
        if(!ApplicationProperty.bluetoothConnectionManager.isConnected) {
            ApplicationProperty.bluetoothConnectionManager.suspendConnection()

            this.deviceConnectionStatusTextView.setTextColor(
                getColor(R.color.disconnectedTextColor)
            )
            this.deviceConnectionStatusTextView.text = getString(R.string.DMA_ConnectionStatus_disconnected_tryToReconnect)

            Executors.newSingleThreadScheduledExecutor().schedule({
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()
            }, 500, TimeUnit.MILLISECONDS)
        }
    }

    @SuppressLint("InflateParams")
    fun onDeviceMenuButtonClick(@Suppress("UNUSED_PARAMETER")view: View) {

        // prevent double execution
        if(!this.deviceMenuOpen){

            // shade the background
            this.devicePropertyListRecyclerView.alpha = 0.2f

            // set the menu button to selected state
            this.deviceMenuButton.setImageResource(R.drawable.ic_menu_pressed_36dp)

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
            
            // add cancel button event handler
            this.deviceMenuPopUpWindow.contentView.findViewById<AppCompatImageButton>(R.id.deviceMainActivityPopUpCancelButton).apply {
                setOnClickListener {
                    try {
                        this@DeviceMainActivity.deviceMenuPopUpWindow.dismiss()
                    } catch (e: java.lang.Exception){
                        Log.e("DeviceMainActivity", "Exception in PopUp cancel button click listener: ${e.message}")
                    }
                }
            }
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
            this.deviceHeaderClickAction = deviceHeaderData.onClickAction

            //this.deviceHeaderNotificationContainer.visibility = View.VISIBLE
            this.deviceHeaderNotificationTextView.text = deviceHeaderData.message
            this.deviceHeaderNotificationTextView.setTextColor(
                getColor(
                    colorForUserMessageType(
                        deviceHeaderData.type
                    )
                )
            )

            val expandCollapseExtension = ExpandCollapseExtension()
            expandCollapseExtension.expand(this.deviceHeaderNotificationContainer)//, 600)
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
    
    private fun onDeviceInfoHeaderClick(){
        if(verboseLog){
            Log.d("DeviceMainActivity", "onDeviceInfoHeaderClick: Action is = ${this.deviceHeaderClickAction}")
        }
        when(this.deviceHeaderClickAction){
            DEV_HEADER_CLICK_ACTION_NONE -> {
                runOnUiThread {
                    this.hideNotificationHeader()
                }
            }
            DEV_HEADER_CLICK_ACTION_COMPLEX_RELOAD -> {
                ApplicationProperty.bluetoothConnectionManager.startNewComplexStateLoop()
                Executors.newSingleThreadScheduledExecutor().schedule({
                    runOnUiThread {
                        this.hideNotificationHeader()
                    }
                }, 500, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun hideNotificationHeader(){
        runOnUiThread {
            this.deviceHeaderNotificationTextView.text = ""

            val expandCollapseExtension = ExpandCollapseExtension()
            expandCollapseExtension.collapse(this.deviceHeaderNotificationContainer, 600)
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
                    ApplicationProperty.bluetoothConnectionManager.clear()
                    finish()
                }, 500, TimeUnit.MILLISECONDS)
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        // this callback will only invoked in this activity if:
        // - this is a connection-suspension or a re-connect-process (the initial connection attempt will be executed in the loading activity!)
        // - or the remote device is disconnected unexpectedly
        (applicationContext as ApplicationProperty).logControl("I: Connection State changed in DeviceMainActivity to $state")

        // set the UI State:
        val conStatus = if(state){
            STATUS_CONNECTED
        } else {
            STATUS_DISCONNECTED
        }
        this.setUIConnectionStatus(conStatus)

        // stop the loading circle and set the info-header
        if(state) {
            // check if the property-loading was successful finished
            if(!this.propertyLoadingFinished){
                // property-loading must have been interrupted - start again!
                this.reloadProperties()
            } else {
                // stop loading circle
                runOnUiThread {
                    this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
                }
                // start updating properties if required
                if(this.propertyStateUpdateRequired) {
                    this.propertyStateUpdateRequired = false
                    Executors.newSingleThreadScheduledExecutor().schedule({
                        ApplicationProperty.bluetoothConnectionManager.updatePropertyStates()
                    }, TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT, TimeUnit.MILLISECONDS)
                }
            }
        } else {
            // connection lost - set no signal indicator
            this.onRssiValueRead(-100)
            
            // the connection is lost, check if this was expected
            if(!this.expectedConnectionLoss){
                // the loss of the connection is unexpected, display popup
                if(verboseLog){
                    Log.d("onConnectionStateChanged", "Unexpected loss of connection in DeviceMainActivity.")
                }
                (applicationContext as ApplicationProperty).logControl("W: Unexpected loss of connection. Remote device not reachable.")
                ApplicationProperty.bluetoothConnectionManager.suspendConnection()
                this.connectionLossAlertDialog()
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
    
    override fun onConnectionEvent(eventID: Int) {
        when(eventID){
            BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED -> {
                this.setUIConnectionStatus(STATUS_CONNECTING)
            }
            else -> {
                Log.d("DeviceMainActivity", "Invalid Connection Event!")
            }
        }
    }

    override fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>) {
        super.onUIAdaptableArrayListGenerationComplete(UIArray)
        
        this.propertyLoadingFinished = true

        runOnUiThread {
            this.hideNotificationHeader()
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
        }
    }

    override fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation) {
        // check if the internal parameter and resources are generated
        if(!item.isAccessible){
            // if not -> update it
            item.update(devicePropertyListRecyclerView.context)
        }

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

    override fun onUIAdaptableArrayItemInserted(index: Int) {
        runOnUiThread {
            devicePropertyListViewAdapter.notifyItemInserted(index)
            //devicePropertyListViewAdapter.notifyItemRangeChanged(index, ApplicationProperty.bluetoothConnectionManager.uIAdapterList.size - index)

            if(index == 0){
                val scrollPosition = this.devicePropertyListRecyclerView.computeVerticalScrollOffset()
                if(scrollPosition == 0){
                    scrollToTop = true

                }
            }
        }
    }

    override fun onUIAdaptableArrayItemRemoved(index: Int) {
        runOnUiThread {
            devicePropertyListViewAdapter.notifyItemRemoved(index)
            devicePropertyListViewAdapter.notifyItemRangeChanged(index, ApplicationProperty.bluetoothConnectionManager.uIAdapterList.size)
        }
    }

    override fun onUIAdaptableArrayItemChanged(index: Int) {
        // if the element is not accessible -> update it!
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
            .apply {
                if (!this.isAccessible) {
                    this.update(devicePropertyListRecyclerView.context)
                }
            }
        // apply changes to list
        runOnUiThread {
            devicePropertyListViewAdapter.notifyItemChanged(index)
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
                    // update the appropriate element
                    when (uIElement.propertyType) {
                        PROPERTY_TYPE_BUTTON -> {
                            // NOTE: this is not used, because the button has no state (by now)
                        }
                        PROPERTY_TYPE_SWITCH -> {
                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {
                            // update item
                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                            // update popup if open
                            if(this.levelSelectorPopUpOpen){
                                try {
                                    val percentageLevelPropertyGenerator =
                                        PercentageLevelPropertyGenerator(newState)
                                    this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.levelSelectorPopUpTextView).text =
                                        percentageLevelPropertyGenerator.percentageString
    
                                    // set seekbar properties
                                    this.popUpWindow.contentView.findViewById<SeekBar>(R.id.levelSelectorPopUpSeekbar).apply {
                                        this.setOnSeekBarChangeListener(null)
                                        this.progress = percentageLevelPropertyGenerator.percentageValue
                                        this.setOnSeekBarChangeListener(this@DeviceMainActivity)
                                    }
                                } catch(e: java.lang.Exception){
                                    Log.e("LevelSelectorUpdate", "Error while updating level selector popup: ${e.message}")
                                    (applicationContext as ApplicationProperty).logControl(("E: Error while updating level selector popup: ${e.message}"))
                                }
                            }
                        }
                        PROPERTY_TYPE_LEVEL_INDICATOR -> {
                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                        }
                        PROPERTY_TYPE_OPTION_SELECTOR -> {
                            // update item
                            devicePropertyListViewAdapter.notifyItemChanged(UIAdapterElementIndex)
                            // update popup if open
                            if(this.optionSelectorPopUpOpen){
                                try {
                                    this.popUpWindow.contentView.findViewById<NumberPicker>(R.id.optionSelectorPopUpNumberPicker).apply {
                                        value = newState
                                    }
                                } catch(e: java.lang.Exception){
                                    Log.e("OptionSelectorUpdate", "Error while updating option selector popup: ${e.message}")
                                    (applicationContext as ApplicationProperty).logControl(("E: Error while updating option selector popup: ${e.message}"))
                                }
                            }
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
    
    override fun onPropertyError(error: Int) {
        when(error) {
            BLE_COMPLEX_STATE_LOOP_FINAL_FAILED -> {
                // notify user
                val deviceHeaderData = DeviceInfoHeaderData()
                deviceHeaderData.displayTime = 9L
                deviceHeaderData.onClickAction = DEV_HEADER_CLICK_ACTION_COMPLEX_RELOAD
                deviceHeaderData.type = USERMESSAGE_TYPE_ERROR
                deviceHeaderData.message = getString(R.string.DMA_PropertyDataLoadingIncomplete)
        
                this.showNotificationHeaderAndPostMessage(deviceHeaderData)
            }
            BLE_UI_ADAPTER_GENERATION_FAIL -> {
                ApplicationProperty.bluetoothConnectionManager.clear()
                finish()
            }
            else -> {
                Log.e("DeviceMainActivity", "onPropertyError: unexpected error. Error-ID: $error")
                (applicationContext as ApplicationProperty).logControl("E: DeviceMainPage: unexpected error. Error-ID: $error")
            }
        }
    }
    
    override fun onCloseDeviceRequested() {
        ApplicationProperty.bluetoothConnectionManager.clear()
        finish()
    }

    override fun getCurrentOpenComplexPropPagePropertyIndex(): Int {
        // no complex property page is open
        return -1
    }
    
    override fun onRssiValueRead(rssi: Int) {
        runOnUiThread {
            val strength = when {
                rssi < -99 -> 0
                rssi < -88 -> 1
                rssi < -75 -> 2
                rssi < -65 -> 3
                rssi < -55 -> 4
                else -> 5
            }
            
            if(strength != this.currentSignalStrength){
                this.currentSignalStrength = strength
                
                this.signalStrengthImageView.apply {
                    when(strength){
                        1 -> setImageResource(R.drawable.signal_20perc)
                        2 -> setImageResource(R.drawable.signal_40perc)
                        3 -> setImageResource(R.drawable.signal_60perc)
                        4 -> setImageResource(R.drawable.signal_80perc)
                        5 -> setImageResource(R.drawable.signal_100perc)
                        else -> setImageResource(R.drawable.no_signal)
                    }
                }
            }
        }
    }

    // device property list adapter:
    class DevicePropertyListAdapter(
        private val devicePropertyAdapter: ArrayList<DevicePropertyListContentInformation>,
        private val itemClickListener: OnPropertyClickListener
    ) : RecyclerView.Adapter<DevicePropertyListAdapter.DPLViewHolder>() {
    
        class DPLViewHolder(
            val linearLayout: LinearLayout,
            private val listener: OnPropertyClickListener
        ) : RecyclerView.ViewHolder(linearLayout) {
            private var pType = -1
            private val elementButton: AppCompatButton = linearLayout.findViewById(R.id.elementButton)
            private val elementSwitch: SwitchCompat = linearLayout.findViewById(R.id.elementSwitch)
            var suppressOnClickEvent = false
            

            init {
                elementButton.setOnClickListener {

                    val buttonAnimation = AnimationUtils.loadAnimation(it.context, R.anim.bounce)
                    it.startAnimation(buttonAnimation)

                    when(pType) {
                        PROPERTY_TYPE_BUTTON -> {
                            listener.onPropertyElementButtonClick(bindingAdapterPosition)
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {
                            listener.onPropertyLevelSelectButtonClick(bindingAdapterPosition)
                        }
                        PROPERTY_TYPE_OPTION_SELECTOR -> {
                            listener.onPropertyOptionSelectButtonClick(bindingAdapterPosition)
                        }
                    }
                }
                elementSwitch.setOnCheckedChangeListener { _, b ->
                    if(pType == PROPERTY_TYPE_SWITCH && !this.suppressOnClickEvent){
                        listener.onPropertyElementSwitchClick(bindingAdapterPosition, b)
                    }
                }
                linearLayout.setOnClickListener {
                    if(pType >= COMPLEX_PROPERTY_START_INDEX){
                        listener.onNavigatableElementClick(bindingAdapterPosition)
                    }
                }
            }

            fun activateListenerFromType(propertyType: Int){
                this.pType = propertyType
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DPLViewHolder {
            val linearLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.device_property_list_element, parent, false) as LinearLayout

            return DPLViewHolder(linearLayout, itemClickListener)
        }

        override fun onBindViewHolder(holder: DPLViewHolder, position: Int) {
            
            holder.suppressOnClickEvent = true

            // get regarding element
            val elementToRender = devicePropertyAdapter.elementAt(position)
            
            if(!elementToRender.isAccessible){
                if(verboseLog){
                    Log.e("DeviceMainActivity", "> onBindViewHolder was invoked on an inaccessible element")
                }
            }

            // enable the listener for the regarding property type
            holder.activateListenerFromType(elementToRender.propertyType)   // maybe move on the end of this method?

            // set background
            holder.linearLayout.background = elementToRender.backgroundDrawable

            // set property image
            holder.linearLayout.findViewById<AppCompatImageView>(R.id.devicePropertyIdentificationImage)
                .apply {
                    setImageResource(
                        elementToRender.imageResourceID
                    )
                }

            // set element text properties
            holder.linearLayout.findViewById<AppCompatTextView>(R.id.devicePropertyNameTextView)
                .apply {
                    this.text = elementToRender.elementText
                    this.setTextColor(elementToRender.textColorResource)
                    if(elementToRender.elementType == GROUP_ELEMENT){
                        this.textSize = 18F
                        setTypeface(typeface, Typeface.BOLD)
                    } else {
                        this.textSize = 14F
                        setTypeface(null, Typeface.NORMAL)
                    }
                }

            // button properties
            holder.linearLayout.findViewById<AppCompatButton>(R.id.elementButton)
                .apply {
                    this.visibility = elementToRender.buttonVisibility
                    if(elementToRender.buttonVisibility == View.VISIBLE){
                        this.isEnabled = elementToRender.isEnabled
                        this.text = elementToRender.elementSubText
                    } else {
                        this.text = ""
                    }
                }

            // switch properties
            holder.linearLayout.findViewById<SwitchCompat>(R.id.elementSwitch)
                .apply {
                    this.visibility = elementToRender.switchVisibility
                    this.isChecked = elementToRender.simplePropertyState > 0
                    this.isEnabled = elementToRender.isEnabled
                }

            // level textView properties
            holder.linearLayout.findViewById<AppCompatTextView>(R.id.levelIndicationTextView)
                .apply {
                    this.visibility = elementToRender.levelIndicationTextViewVisibility
                    if(elementToRender.levelIndicationTextViewVisibility == View.VISIBLE){
                        this.text = elementToRender.elementSubText
                        if(elementToRender.hasSubColorDefinition){
                            this.setTextColor(elementToRender.subTextColorResource)
                        }
                    } else {
                        this.text = ""
                    }
                }

            // navigation image properties
            holder.linearLayout.findViewById<AppCompatImageView>(R.id.forwardImage).apply {
                visibility = elementToRender.navigationImageVisibility
                if(elementToRender.hasNavImageColorDefinition){
                    background = elementToRender.navigationImageBackground
                    setImageResource(R.drawable.ic_complex_property_navigation_arrow_invisible)
                } else {
                    if (elementToRender.isEnabled) {
                        setImageResource(R.drawable.ic_complex_property_navigation_arrow)
                    } else {
                        setImageResource(R.drawable.ic_complex_property_disabled_navigation_arrow)
                    }
                }
            }
            
            holder.suppressOnClickEvent = false
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
    }
}
