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

const val STATUS_DISCONNECTED = 0
const val STATUS_CONNECTED = 1
const val STATUS_CONNECTING = 2

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

                    // if the device was disconnected on a property sub-page, navigate back with delay
                    if (!ApplicationProperty.bluetoothConnectionManager.isConnected) {

                        // set UI visual state
                        resetSelectedItemBackground()
                        setUIConnectionStatus(STATUS_DISCONNECTED)

                        // schedule back-navigation
                        Handler(Looper.getMainLooper()).postDelayed({
                            (applicationContext as ApplicationProperty).resetControlParameter()
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
                        // try to get the device name from the bluetooth device object (could be empty)
                        this.deviceTypeHeaderTextView.text =
                            ApplicationProperty.bluetoothConnectionManager.currentDevice?.name
                        // check if there is a name
                        if(this.deviceTypeHeaderTextView.text.isEmpty()){
                            // there is no name provided, so lookup in the friendly device list for a name
                            this.deviceTypeHeaderTextView.text =
                                (applicationContext as ApplicationProperty)
                                    .addedDevices
                                    .lookupForDeviceNameWithAddress(ApplicationProperty.bluetoothConnectionManager.currentDevice?.address ?: "")
                        }
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
            //this.propertyList.clear()
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.clear()
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
        // TODO: check if the newState comes with the right terminology

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

        val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

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
            decryptOptionSelectorString(devicePropertyListContentInformation.elementDescriptorText)

        if(options.isEmpty()){
            Log.e("decryptOptions", "Error on decrypting options for option-selector from property descriptor!")
            return
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

        this.popUpWindow.showAtLocation(this.devicePropertyListRecyclerView, Gravity.CENTER, 0, 0)

        // set the appropriate image and option selection
        this.popUpWindow.contentView.findViewById<AppCompatImageView>(R.id.optionSelectorPopUpImageView).setImageResource(
            resourceIdForImageId(devicePropertyListContentInformation.imageID, PROPERTY_ELEMENT, (applicationContext as ApplicationProperty).isPremiumAppVersion)
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

                if(devicePropertyListContentInformation.simplePropertyState < options.size) {
                    value = devicePropertyListContentInformation.simplePropertyState
                }

                setOnValueChangedListener { _, _, newVal ->
    
                    // update list element
                    ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
                        .apply {
                            this.simplePropertyState = newVal
                            this.update(applicationContext)
                        }
                    devicePropertyListViewAdapter.notifyItemChanged(index)
                    
                    // update internal simple state
                    ApplicationProperty.bluetoothConnectionManager.updateInternalSimplePropertyState(
                        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex,
                        newVal
                    )
                    
                    // control successive transmissions
                    if(!optionSelectUpdateData.isHandled){
                        // only save the value, because there is a transmission pending
                        optionSelectUpdateData.value = newVal
                    } else {
                        // if there is no transmission pending, send the data delayed
                        optionSelectUpdateData.value = newVal
                        optionSelectUpdateData.isHandled = false
                        optionSelectUpdateData.eIndex = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex
    
                        Executors.newSingleThreadScheduledExecutor().schedule(
                            {
                                ApplicationProperty.bluetoothConnectionManager.sendData(
                                    makeSimplePropertyExecutionString(
                                        optionSelectUpdateData.eIndex,
                                        optionSelectUpdateData.value
                                    )
                                )
                                optionSelectUpdateData.isHandled = true
            
                            }, 150, TimeUnit.MILLISECONDS)
                    }
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
            ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
                .apply {
                    this.simplePropertyState = bitValue
                    this.update(applicationContext)
                }
            this.devicePropertyListViewAdapter.notifyItemChanged(index)
            
            // update internal simple state
            ApplicationProperty.bluetoothConnectionManager.updateInternalSimplePropertyState(
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex,
                bitValue
            )

            if(levelSelectorPopUpOpen){
                val seekBarText =  "$newValue%"
                // set the seekbar and textview properties
                this.popUpWindow.contentView.findViewById<AppCompatTextView>(R.id.levelSelectorPopUpTextView).text = seekBarText
                this.popUpWindow.contentView.findViewById<SeekBar>(R.id.levelSelectorPopUpSeekbar).progress = newValue
            }
            
            // handle successive transmission
            if(!this.slideUpdateData.isHandled){
                // only save the value, because there is a transmission pending
                this.slideUpdateData.value = bitValue
            } else {
                // if there is no transmission pending, send the data delayed
                this.slideUpdateData.value = bitValue
                this.slideUpdateData.isHandled = false
                this.slideUpdateData.eIndex = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index).internalElementIndex
                
                Executors.newSingleThreadScheduledExecutor().schedule(
                    {
                        ApplicationProperty.bluetoothConnectionManager.sendData(
                            makeSimplePropertyExecutionString(
                                slideUpdateData.eIndex,
                                slideUpdateData.value
                            )
                        )
                        slideUpdateData.isHandled = true
                        
                    }, 150, TimeUnit.MILLISECONDS)
            }
        }
    }

    override fun onNavigatableElementClick(index: Int) {

        val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

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

        // if item is disabled skip execution of this method
        if(!devicePropertyListContentInformation.isEnabled){
            if(verboseLog){
                Log.d("M:CB:onNavElementClk", "Navigatable element was clicked. Element is disabled. Skip execution!")
            }
            return
        }

        // set it to selected color
        setPropertyToSelectedState(index)

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
            COMPLEX_PROPERTY_TYPE_ID_STRING_INTERROGATOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the string interrogator page
                val intent = Intent(this@DeviceMainActivity, StringInterrogatorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.internalElementIndex)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                intent.putExtra("isStandAlonePropertyMode", false)
                startActivity(intent)
                overridePendingTransition(R.anim.start_activity_slide_animation_in, R.anim.start_activity_slide_animation_out)
            }
            COMPLEX_PROPERTY_TYPE_ID_TEXT_LIST_PRESENTER -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the text-list-presenter page
                val intent = Intent(this@DeviceMainActivity, TextListPresenterActivity::class.java)
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

    fun onDeviceMainNotificationAreaClick(@Suppress("UNUSED_PARAMETER")view: View) {
        runOnUiThread {
            this.hideNotificationHeader()
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
        // check if the internal parameter and resources are generated
        if(!item.isAccessible){
            // if not -> update it
            item.update(applicationContext)
        }

//        if(item.globalIndex == -1){
//            Log.e("itemAdded", "severe error, invalid index! globalIndex: ${item.globalIndex} | Internal index: ${item.internalElementIndex}")
//        }

        // add the data to the UI-List
        //this.propertyList.add(item)

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

        // TODO: if this is a new group or inserted at the end of the group, make sure to redraw other impacted items to keep the visual state

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

        // TODO: if this is the last of a group or other case which impacts the visual state of other items, make sure to redraw them!

        // - remove the item from the list in this class
        // - redraw the element before and after the element
        // - bring the internal element indexes of the ui-list in the correct order (use the list in bleManager)

        //this.propertyList.removeAt(index)

        //this.propertyList.clear()
        //this.propertyList = ApplicationProperty.bluetoothConnectionManager.uIAdapterList


        // TODO: update the all indexes (global + internal) in the propertyList or use the uIAdapter direct!?

        runOnUiThread {
            devicePropertyListViewAdapter.notifyItemRemoved(index)
            //devicePropertyListViewAdapter.notifyDataSetChanged()



            //devicePropertyListViewAdapter.notifyItemChanged(index)

            //for(i in index until this.propertyList.size){
            devicePropertyListViewAdapter.notifyItemRangeChanged(index, ApplicationProperty.bluetoothConnectionManager.uIAdapterList.size)
            //}
        }
    }

    override fun onUIAdaptableArrayItemChanged(index: Int) {
        // if the element is not accessible -> update it!
        ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)
            .apply {
                if (!this.isAccessible) {
                    this.update(applicationContext)
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

                    // temp:
                    //Log.e("onSimpleStateChanged", "Callback invoked: type is: ${uIElement.propertyType}")


//                    val linearLayout =
//                        this.devicePropertyListLayoutManager.findViewByPosition(
//                            UIAdapterElementIndex
//                        ) as? LinearLayout

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
            )
            : RecyclerView.ViewHolder(linearLayout) {
                private var pType = -1
                private val elementButton: AppCompatButton = linearLayout.findViewById(R.id.elementButton)
                private val elementSwitch: SwitchCompat = linearLayout.findViewById(R.id.elementSwitch)

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
                    if(pType == PROPERTY_TYPE_SWITCH){
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

            // get regarding element
            val elementToRender = devicePropertyAdapter.elementAt(position)
            
            if(!elementToRender.isAccessible){
                if(verboseLog){
                    Log.e("DeviceMainActivity", "> onBindViewHolder was invoked on an inaccessible element")
                }
            }

            // enable the listener for the regarding property type
            holder.activateListenerFromType(elementToRender.propertyType)

            // set background
            holder.linearLayout.background = elementToRender.backgroundDrawable

            // set property image
            holder.linearLayout.findViewById<AppCompatImageView>(R.id.devicePropertyIdentificationImage)
                .apply {
                    setBackgroundResource(
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
                        setTypeface(typeface, Typeface.NORMAL)
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
        }

            /*

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
        }*/

        override fun getItemCount(): Int {
            return devicePropertyAdapter.size
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }

//        fun getElementAt(index: Int) : DevicePropertyListContentInformation{
//            return devicePropertyAdapter.elementAt(index)
//        }
    }
}
