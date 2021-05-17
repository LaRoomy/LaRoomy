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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView
import java.util.*
import kotlin.collections.ArrayList


class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback, OnPropertyClickListener {

    // device property list elements
    private lateinit var devicePropertyListRecyclerView: RecyclerView
    private lateinit var devicePropertyListViewAdapter: RecyclerView.Adapter<*>
    private lateinit var devicePropertyListLayoutManager: RecyclerView.LayoutManager
    //private var devicePropertyList= ArrayList<DevicePropertyListContentInformation>()

    private lateinit var deviceTypeHeaderImageView: AppCompatImageView
    private lateinit var deviceTypeHeaderTextView: AppCompatTextView
    private lateinit var deviceConnectionStatusTextView: AppCompatTextView
    private lateinit var deviceHeaderNotificationImageView: AppCompatImageView
    private lateinit var deviceHeaderNotificationTextView: AppCompatTextView
    private lateinit var deviceSettingsButton: AppCompatImageButton

    private var activityWasSuspended = false
    private var buttonRecoveryRequired = false
    private var restoreIndex = -1
    private var deviceImageResourceId = -1

    private val propertyList = ArrayList<DevicePropertyListContentInformation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        // keep screen active if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_KeepScreenActive)){
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        this.deviceImageResourceId = intent.getIntExtra("BondedDeviceImageResourceId", -1)

        // realign the context to the bluetoothManager (NOTE: only on creation - onResume handles this on navigation)
        ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(this@DeviceMainActivity, this)
        ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

        // get UI Elements
        this.deviceTypeHeaderImageView = findViewById(R.id.deviceTypeHeaderImageView)
        this.deviceTypeHeaderTextView = findViewById(R.id.deviceMainAcitivityDeviceTypeHeaderNameTextView)
        this.deviceConnectionStatusTextView = findViewById(R.id.deviceMainActivityDeviceConnectionStatusTextView)
        this.deviceHeaderNotificationImageView = findViewById(R.id.deviceMainActivityDeviceInfoSubHeaderImageView)
        this.deviceHeaderNotificationTextView = findViewById(R.id.deviceMainActivityDeviceInfoSubHeaderTextView)
        this.deviceSettingsButton = findViewById(R.id.deviceMainActivityDeviceSettingsButton)

        // init recycler view!!
        this.devicePropertyListLayoutManager = LinearLayoutManager(this)

        // set empty list placeholder in array-list
/*
        val dc = DevicePropertyListContentInformation()
        dc.elementType = NO_CONTENT_ELEMENT
        this.devicePropertyList.add(dc)
*/


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
            findViewById<RecyclerView>(R.id.devicePropertyListView)
                .apply {
                    //setHasFixedSize(true)// this is not possible since the group and property elements have a different height
                    layoutManager = devicePropertyListLayoutManager
                    adapter = devicePropertyListViewAdapter
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

            // TODO: suspend connection (maybe delayed in background???)
            //ApplicationProperty.bluetoothConnectionManger.close()

            ApplicationProperty.bluetoothConnectionManager.suspendConnection()

            setUIConnectionStatus(false)
            setDeviceInfoHeader(29, getString(R.string.DMA_DeviceConnectionSuspended))

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
                setUIConnectionStatus(false)

                // schedule back-navigation
                Handler(Looper.getMainLooper()).postDelayed({
                    (applicationContext as ApplicationProperty).resetControlParameter()
                    ApplicationProperty.bluetoothConnectionManager.clear()
                    finish()
                }, 2000)

            } else {
                // do a complex state- update if required...
                if ((this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired) {
                    if(verboseLog) {
                        Log.d(
                            "M:onResume",
                            "Complex-State-Update required for ID ${(this.applicationContext as ApplicationProperty).complexUpdateID}"
                        )
                    }

                    (applicationContext as ApplicationProperty).logControl("Resumed Device Main Activity: Complex-State-Update required for ID: ${(this.applicationContext as ApplicationProperty).complexUpdateID}")

                    (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired =
                        false

                    if (ApplicationProperty.bluetoothConnectionManager.isMultiComplexProperty((this.applicationContext as ApplicationProperty).complexUpdateID)) {
                        // delay the complex state update
                        Handler(Looper.getMainLooper()).postDelayed(
                            {
                                ApplicationProperty.bluetoothConnectionManager.doComplexPropertyStateRequestForID(
                                    (this.applicationContext as ApplicationProperty).complexUpdateID
                                )
                                (this.applicationContext as ApplicationProperty).complexUpdateID =
                                    -1
                            },
                            500
                        )
                    } else {
                        // this is not a multicomplex property, do the complex state update immediately
                        ApplicationProperty.bluetoothConnectionManager.doComplexPropertyStateRequestForID(
                            (this.applicationContext as ApplicationProperty).complexUpdateID
                        )
                        (this.applicationContext as ApplicationProperty).complexUpdateID = -1
                    }
                }

                // set property-item to normal background
                resetSelectedItemBackground()

                // reset the parameter
                restoreIndex = -1
                (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage =
                    false

                // realign the context objects to the bluetoothManager
                ApplicationProperty.bluetoothConnectionManager.reAlignContextObjects(
                    this@DeviceMainActivity,
                    this
                )
                ApplicationProperty.bluetoothConnectionManager.setPropertyEventHandler(this)

                // update info-header and states
                if (ApplicationProperty.bluetoothConnectionManager.deviceInfoHeaderData.valid) {
                    setDeviceInfoHeader(
                        ApplicationProperty.bluetoothConnectionManager.deviceInfoHeaderData.imageID,
                        ApplicationProperty.bluetoothConnectionManager.deviceInfoHeaderData.message
                    )
                }
                if ((this.applicationContext as ApplicationProperty).uiAdapterChanged) {
                    (this.applicationContext as ApplicationProperty).uiAdapterChanged = false

                    // TODO: update data in a loop!?

                }

                // notify the device that the user navigated back to the device main page
                ApplicationProperty.bluetoothConnectionManager.notifyBackNavigationToDeviceMainPage()

                // TODO: detect state-changes and update the property-list-items
            }

        } else {
            // creation or resume action:

            // make sure to set the right Name and image for the device
            this.deviceTypeHeaderTextView.text =
                ApplicationProperty.bluetoothConnectionManager.currentDevice?.name
            // TODO: reactivate!:
            //this.deviceTypeHeaderImageView.setImageResource(this.deviceImageResourceId)

            // Update the connection status
            setUIConnectionStatus(ApplicationProperty.bluetoothConnectionManager.isConnected)

            // show the loading circle
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

            // check if this is a call on creation or resume:
            if (this.activityWasSuspended) {

                // realign objects
                //ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceMainActivity, this)
                //ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

                // try to reconnect
                ApplicationProperty.bluetoothConnectionManager.resumeConnection()


                //ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()


                this.activityWasSuspended = false
            } else {
                // must be the creation process -> start property listing
                ApplicationProperty.bluetoothConnectionManager.startDevicePropertyListing()
            }

            // TODO: how to confirm the device-properties
            // maybe make a changed-parameter in the device firmware and call that to upgrade performance and hold the line clear for communication?
        }
        // check if a button needs to be recovered
        if(this.buttonRecoveryRequired){
            this.buttonRecoveryRequired = false
            this.deviceSettingsButton.setImageResource(R.drawable.settings_white_24)
        }
    }

    private fun setItemBackgroundColor(index: Int, colorID: Int){
        val linearLayout = this.devicePropertyListLayoutManager.findViewByPosition(index) as? LinearLayout
        linearLayout?.setBackgroundColor(getColor(colorID))

        // maybe get the sub holder constraintLayout (ID: contentHolderLayout) ????
    }

    private fun setItemSeparatorViewColors(index: Int, colorID: Int){
        val linearLayout = this.devicePropertyListLayoutManager.findViewByPosition(index) as? LinearLayout
        val top = linearLayout?.findViewById<View>(R.id.topSeparator)
        val bottom = linearLayout?.findViewById<View>(R.id.bottomSeparator)
        top?.setBackgroundColor(getColor(colorID))
        bottom?.setBackgroundColor(getColor(colorID))

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
                        "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                        "Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }
        // NOTE: the button has no state the execution command contains always "1"
        ApplicationProperty.bluetoothConnectionManager.sendData("C${a8BitValueToString(devicePropertyListContentInformation.elementID)}1$")
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
                        "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                        "Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }
        // TODO: check if the newState comes with the right terminology

        val c = when(switch.isChecked){
            true -> '1'
            else -> '0'
        }
        ApplicationProperty.bluetoothConnectionManager.sendData("C${a8BitValueToString(devicePropertyListContentInformation.elementID)}$c$")
    }

    override fun onSeekBarPositionChange(
        index: Int,
        newValue: Int,
        changeType: Int
    ) {
        val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(index)

        if(verboseLog) {
            Log.d(
                "M:CB:onSeekBarChange",
                "Property element was clicked. Element-Type is SEEKBAR at index: $index\n\nData is:\n" +
                        "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                        "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                        "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                        "Element-Index: ${devicePropertyListContentInformation.globalIndex}\n\n" +
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
            val bitValue =
                percentTo8Bit(newValue)

            ApplicationProperty.bluetoothConnectionManager.sendData("C${a8BitValueToString(devicePropertyListContentInformation.elementID)}${a8BitValueToString(bitValue)}$")
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
                        "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                        "Element-Index: ${devicePropertyListContentInformation.globalIndex}"
            )
        }

        // set it to selected color
        setItemBackgroundColor(index, R.color.DMA_ItemSelectedColor)
        setItemSeparatorViewColors(index, R.color.selectedSeparatorColor)
        // save the index of the highlighted item to reset it on back-navigation
        restoreIndex = index

        // navigate
        when(devicePropertyListContentInformation.propertyType){
            COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the RGB Page
                val intent = Intent(this@DeviceMainActivity, RGBControlActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)
            }
            COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the extended level selector page
                val intent = Intent(this@DeviceMainActivity, ExtendedLevelSelectorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the time selector page with single-select-mode
                val intent = Intent(this@DeviceMainActivity, SimpleTimeSelectorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)
            }
            COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the unlock control page
                val intent = Intent(this@DeviceMainActivity, UnlockControlActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the time-frame selector page
                val intent = Intent(this@DeviceMainActivity, TimeFrameSelectorActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)
            }
            COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the navigator page
                val intent = Intent(this@DeviceMainActivity, NavigatorControlActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)
            }
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> {
                // prevent the normal "onPause" execution
                (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
                // navigate to the navigator page
                val intent = Intent(this@DeviceMainActivity, BarGraphActivity::class.java)
                intent.putExtra("elementID", devicePropertyListContentInformation.elementID)
                intent.putExtra("globalElementIndex", devicePropertyListContentInformation.globalIndex)
                startActivity(intent)

            }
            else -> {
                // what to do here??
            }
        }
    }

    private fun resetSelectedItemBackground(){
        // reset the selected item background if necessary
        if (restoreIndex >= 0) {
            // check if the property is part of a group and set the appropriate background-color
            if (ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    restoreIndex
                ).isGroupMember
            ) {
                // set group background
                setItemBackgroundColor(restoreIndex, R.color.groupColor)
                setItemSeparatorViewColors(restoreIndex, R.color.transparentViewColor)

            } else {
                // set default background
                setItemBackgroundColor(restoreIndex, R.color.transparentViewColor)
                setItemSeparatorViewColors(restoreIndex, R.color.transparentViewColor)
            }
        }

    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
        // same procedure like onBackPressed!

        ApplicationProperty.bluetoothConnectionManager.clear()

        // start main activity to select a new device!!!

        finish()
    }

    fun onDeviceSettingsButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        if(ApplicationProperty.bluetoothConnectionManager.isConnected) {
            // highlight button
            this.deviceSettingsButton.setImageResource(R.drawable.settings_gold_pushed_24)
            this.buttonRecoveryRequired = true
            // prevent the normal "onPause" execution
            (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
            // navigate to the device settings activity..
            val intent = Intent(this@DeviceMainActivity, DeviceSettingsActivity::class.java)
            startActivity(intent)
        }
    }

    fun onReconnectDevice(@Suppress("UNUSED_PARAMETER")view: View){
        // re-connect
        // confirm device-properties???
        // re-connect and re-new the device-properties???

        if(!ApplicationProperty.bluetoothConnectionManager.isConnected) {
            ApplicationProperty.bluetoothConnectionManager.close()
            ApplicationProperty.bluetoothConnectionManager.connectToLastSuccessfulConnectedDevice()
        }
    }


    private fun setUIConnectionStatus(status :Boolean){

        runOnUiThread {
//            val statusText =
//                findViewById<TextView>(R.id.deviceMainActivityDeviceConnectionStatusTextView)

            if (status) {
                this.deviceConnectionStatusTextView.setTextColor(getColor(R.color.connectedTextColor))
                this.deviceConnectionStatusTextView.text = getString(R.string.DMA_ConnectionStatus_connected)
            } else {
                this.deviceConnectionStatusTextView.setTextColor(getColor(R.color.disconnectedTextColor))
                this.deviceConnectionStatusTextView.text = getString(R.string.DMA_ConnectionStatus_disconnected)
            }
        }
    }

    private fun setDeviceInfoHeader(imageID: Int, message: String){

        // TODO: Problem: the view is outside of the screen if the text is too long -> it must be wrapped

        runOnUiThread {
            this.deviceHeaderNotificationImageView.setImageResource(
                resourceIdForImageId(imageID)
            )
            this.deviceHeaderNotificationTextView.text = message
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        // this callback will only invoked in this activity if this is a re-connect-process (the initial connection attempt will be executed in the loading activity!)

        (applicationContext as ApplicationProperty).logControl("I: Connection State changed in DeviceMainActivity to $state")

        // set the UI State to connected:
        this.setUIConnectionStatus(state)
        // stop the loading circle and set the info-header
        if(state) {
            // send a test command
            ApplicationProperty.bluetoothConnectionManager.testConnection(200)// TODO: this must be tested
            // stop spinner and set info header
            runOnUiThread {
                this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
                this.setDeviceInfoHeader(43, getString(R.string.DMA_Ready))
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

    override fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>) {
        super.onUIAdaptableArrayListGenerationComplete(UIArray)

        // ?? description!


        runOnUiThread {
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
            this.setDeviceInfoHeader(43, getString(R.string.DMA_Ready))

            // TODO: test!
            //this.devicePropertyListViewAdapter.notifyDataSetChanged()
        }
    }

    override fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation) {
        super.onUIAdaptableArrayListItemAdded(item)

        //Log.d("M:UIListItemAdded", "Item added to UIAdapter. Index: ${item.globalIndex} Name: ${item.elementText}")

        this.propertyList.add(item)


        runOnUiThread {

                // maybe there is no need to call this???
                // adapter.submitList(list) to proof



            // TODO: slow this down if necessary!!!!!

            //this.devicePropertyListViewAdapter.notifyItemInserted(ApplicationProperty.bluetoothConnectionManager.uIAdapterList.size - 1)

            this.devicePropertyListViewAdapter.notifyItemInserted(this.propertyList.size - 1)

        }

        //this.handleAddedItem(item.globalIndex)

        //this.maxExecutionIndex =

    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)

        runOnUiThread {
            val uIElement =
                ApplicationProperty.bluetoothConnectionManager.uIAdapterList.elementAt(
                    UIAdapterElementIndex
                )

            val linearLayout =
                this.devicePropertyListLayoutManager.findViewByPosition(UIAdapterElementIndex) as? LinearLayout

            // update the appropriate element
            when (uIElement.propertyType) {
                PROPERTY_TYPE_BUTTON -> {
                    // NOTE: this is not used, because the button has no state (by now)
                }
                PROPERTY_TYPE_SWITCH -> {
                    val switch =
                        linearLayout?.findViewById<SwitchCompat>(R.id.elementSwitch)
                    switch?.isChecked = (newState != 0)
                }
                PROPERTY_TYPE_LEVEL_SELECTOR -> {
                    val seekBar =
                        linearLayout?.findViewById<SeekBar>(R.id.elementSeekBar)
                    seekBar?.progress = get8BitValueAsPercent(newState)
                }
                PROPERTY_TYPE_LEVEL_INDICATOR -> {
                    val textView =
                        linearLayout?.findViewById<TextView>(R.id.levelIndicationTextView)
                    val percentageLevelPropertyGenerator = PercentageLevelPropertyGenerator(
                        get8BitValueAsPercent(newState)
                    )
                    textView?.setTextColor(percentageLevelPropertyGenerator.colorID)
                    textView?.text = percentageLevelPropertyGenerator.percentageString
                }
                else -> {
                    // nothing by now
                }
            }
        }
    }

    override fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData) {
        super.onDeviceHeaderChanged(deviceHeaderData)
        this.setDeviceInfoHeader(deviceHeaderData.imageID, deviceHeaderData.message)
    }

    override fun onUIAdaptableArrayItemChanged(index: Int) {
        super.onUIAdaptableArrayItemChanged(index)

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

        @SuppressLint("CutPasteId")
        override fun onBindViewHolder(holder: DPLViewHolder, position: Int) {

            val elementToRender = devicePropertyAdapter.elementAt(position)

            when(elementToRender.elementType){
                UNDEFINED_ELEMENT -> {
                    // should not happen
                    holder.linearLayout.findViewById<LinearLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                GROUP_ELEMENT -> {

                    // make the top and bottom separator black
                    holder.linearLayout.findViewById<View>(R.id.bottomSeparator).apply {
                        //visibility = View.VISIBLE
                        setBackgroundResource(R.color.groupBottomSeparatorColor)
                    }
                    holder.linearLayout.findViewById<View>(R.id.topSeparator).apply {
                        //visibility = View.VISIBLE
                        setBackgroundResource(R.color.groupTopSeparatorColor)
                    }



                    holder.linearLayout.setBackgroundColor(activityContext.getColor(R.color.groupHeaderColor))

                    // make sure it is visible (TODO: is this really necessary??)
                    //holder.linearLayout.findViewById<LinearLayout>(R.id.contentHolderLayout).visibility = View.VISIBLE

                    // make the element higher by setting the visibility of the group-border-view to: visible
                    holder.linearLayout.findViewById<View>(R.id.startSeparator).visibility = View.VISIBLE

                    // group elements are for subclassing properties, but cannot navigate forward (by now... ;) )
                    //holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE

                    // the button mustn't be visible (and isn't by default)
                    //holder.constraintLayout.findViewById<Button>(R.id.elementButton).visibility = View.GONE

                    // set the image requested by the device (or a placeholder)

                    // test !!!!!!!!!!!!!!!
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).apply {
                        setBackgroundResource(
                            resourceIdForImageId(elementToRender.imageID)
                        )
                    }


                    // set the text for the element and show the textView
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).apply {
                        visibility = View.VISIBLE
                        text = elementToRender.elementText
                        textSize = 16F
                        setTypeface(typeface, Typeface.BOLD)
                    }

                    //.visibility = View.VISIBLE
                    //holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                }
                PROPERTY_ELEMENT -> {

                    // this is the default color
                    //holder.linearLayout.setBackgroundColor(activityContext.getColor(R.color.groupColor))

                    // get the element
                    //val element = devicePropertyAdapter.elementAt(position)
                    // show / hide the navigate-image
                    if(elementToRender.canNavigateForward)
                        holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                    //else  holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE
                    // if the property is part of a group -> make the separator-lines transparent

                    // TODO: test this
                    //holder.constraintLayout.findViewById<View>(R.id.topSeparator).setBackgroundResource(R.color.transparentViewColor)

                    holder.linearLayout.findViewById<View>(R.id.bottomSeparator).apply {
                        visibility = View.VISIBLE
                        setBackgroundResource(R.color.propertyItemBottomSeparatorColor)
                    }

                    // set the appropriate image for the imageID
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).apply {
                        setBackgroundResource(
                            resourceIdForImageId(elementToRender.imageID)
                        )
                    }

                    // TODO: hide the image-view if the imageID is not set???


                    // set the appropriate elements for the type of the property:
                    when(elementToRender.propertyType){
                        -1 -> return // must be error
                        0 -> return // must be error
                        PROPERTY_TYPE_BUTTON -> {
                            // apply to the button
                            holder.linearLayout.findViewById<Button>(R.id.elementButton).apply {
                                // show the button
                                visibility = View.VISIBLE
                                // set the text of the button
                                text = elementToRender.elementText
                                // set the onClick handler
                                setOnClickListener {
                                    itemClickListener.onPropertyElementButtonClick(
                                        position,
                                        elementToRender
                                    )
                                }
                            }
                        }
                        PROPERTY_TYPE_SWITCH -> {
                            // get the switch
                            val switch = holder.linearLayout.findViewById<SwitchCompat>(R.id.elementSwitch)
                            // show the switch
                            switch.visibility = View.VISIBLE
                            // set the onClick handler
                            switch.setOnClickListener{
                                itemClickListener.onPropertyElementSwitchClick(position, elementToRender, switch)
                            }
                            if(elementToRender.simplePropertyState > 0){
                                switch.isChecked = true
                            }
                            // show the text-view
                            val textView = holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = elementToRender.elementText
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {
                            // show seek-bar layout container
                            holder.linearLayout.findViewById<LinearLayout>(R.id.seekBarContainer).visibility = View.VISIBLE
                            // set the handler for the seekBar
                            elementToRender.handler = callingActivity


                            // test!!!!
                            holder.linearLayout.findViewById<SeekBar>(R.id.elementSeekBar).apply {
                                this.setOnSeekBarChangeListener(elementToRender)
                                this.progress =
                                    get8BitValueAsPercent(elementToRender.simplePropertyState)
                            }

                            // show the text-view
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).apply {
                                visibility = View.VISIBLE
                                // set the text
                                text = elementToRender.elementText
                            }
                        }
                        PROPERTY_TYPE_LEVEL_INDICATOR -> {
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).apply {
                                visibility = View.VISIBLE
                                // set the text
                                text = elementToRender.elementText
                            }

                            // show a level indication e.g. "96%"
                            val percentageLevelPropertyGenerator = PercentageLevelPropertyGenerator(elementToRender.simplePropertyState)

                            holder.linearLayout.findViewById<TextView>(R.id.levelIndicationTextView).apply {
                                visibility = View.VISIBLE
                                text = percentageLevelPropertyGenerator.percentageString
                                setTextColor(percentageLevelPropertyGenerator.colorID)
                            }
                        }
                        PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> {

                            // TODO: integrate textcolor???

                            // show the textView
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).apply {
                                visibility = View.VISIBLE
                                // set the text
                                text = elementToRender.elementText
                            }

                            //holder.linearLayout.findViewById<TextView>(R.id.levelIndicationTextView).visibility = View.GONE
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
                            holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).apply {
                                visibility = View.VISIBLE
                                // set the text
                                text = elementToRender.elementText
                            }
                            // show the navigate arrow
                            holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                        }
                    }
                }
                SEPARATOR_ELEMENT -> {
                    // for the separator element, the only necessary elements are the top and/or bottom separator
                    holder.linearLayout.findViewById<View>(R.id.topSeparator).setBackgroundResource(R.color.separatorColor)
                    //holder.linearLayout.findViewById<View>(R.id.bottomSeparator).setBackgroundResource(R.color.separatorColor)

                    holder.linearLayout.setBackgroundColor(activityContext.getColor(R.color.transparentViewColor))
                }
                else -> {
                    // should not happen
                    holder.linearLayout.findViewById<LinearLayout>(R.id.contentHolderLayout).visibility = View.GONE
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
    }

//    private fun handleAddedItem(index: Int) {
//        if (this.timerIsOnline) {
//
//            this.maxExecutionIndex = index
//
//        } else {
//            this.timerIsOnline = true
//
////            // when this is a already
////            if(this.curExecutionIndex == -1) {
////                this.curExecutionIndex = 0
////            }
//             this.curExecutionIndex = 0
//
//            this.maxExecutionIndex = index
//
//            // start timer
//            Timer().scheduleAtFixedRate(
//                object : TimerTask() {
//                    override fun run() {
//
//                        if ((curExecutionIndex != -1) && (maxExecutionIndex != -1)) {
//                            if (curExecutionIndex < maxExecutionIndex) {
//
//                                runOnUiThread {
//                                    devicePropertyListViewAdapter.notifyItemInserted(
//                                        curExecutionIndex
//                                    )
//                                }
//                                curExecutionIndex++
//                            } else {
//
//                                runOnUiThread {
//                                    devicePropertyListViewAdapter.notifyItemInserted(
//                                        curExecutionIndex
//                                    )
//                                }
//
//                                cancel()
//                                timerIsOnline = false
//                                curExecutionIndex = -1
//                                maxExecutionIndex = -1
//                            }
//                        } else {
//                            cancel()
//                            timerIsOnline = false
//                        }
//                    }
//                }, 0, (50).toLong()
//            )
//        }
//    }
}
