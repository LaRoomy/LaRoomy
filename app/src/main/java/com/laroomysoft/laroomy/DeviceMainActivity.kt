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
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView


class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback, OnPropertyClickListener {

    // device property list elements
    private lateinit var devicePropertyListRecyclerView: RecyclerView
    private lateinit var devicePropertyListViewAdapter: RecyclerView.Adapter<*>
    private lateinit var devicePropertyListLayoutManager: RecyclerView.LayoutManager
    //private var devicePropertyList= ArrayList<DevicePropertyListContentInformation>()

    private var activityWasSuspended = false
    private var restoreIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        // realign the context to the bluetoothManager (NOTE: only on creation - onResume handles this on navigation)
        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceMainActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

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
                ApplicationProperty.bluetoothConnectionManger.uIAdapterList,
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

            Log.d("M:CB:onPause", "onPause executed in DeviceMainActivity - User left the app or navigated back - suspend connection")

            // TODO: suspend connection (maybe delayed in background???)

            ApplicationProperty.bluetoothConnectionManger.close()
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

        //ApplicationProperty.bluetoothConnectionManger.uIAdapterList.clear()


        // TODO: finish activity NO!

        // TODO: at this point, this is the only existing activity in the app-lifecycle, so:
        // if the user presses back, he leaves the app, so onPause must be called???

        ApplicationProperty.bluetoothConnectionManger.clear()
        finish()
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:onResume", "Activity resumed. Previous loading done: ${this.activityWasSuspended}")

        // at first check if this callback will be invoked due to a back-navigation from a property sub-page
        // or if it was invoked on creation or a resume from outside of the application
        if((this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage){

            // this is a back navigation from a property sub-page

            // do a complex state- update if required...
            if((this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired){
                Log.d("M:onResume", "Complex-State-Update required for ID ${(this.applicationContext as ApplicationProperty).complexUpdateID}")

                (this.applicationContext as ApplicationProperty).complexPropertyUpdateRequired = false

                ApplicationProperty.bluetoothConnectionManger.doComplexPropertyStateRequestForID(
                    (this.applicationContext as ApplicationProperty).complexUpdateID
                )
                (this.applicationContext as ApplicationProperty).complexUpdateID = -1
            }

            // set property-item to normal background
            if(restoreIndex >= 0) {
                // check if the property is part of a group and set the appropriate background-color
                if(ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(restoreIndex).isGroupMember) {
                    // set group background

                    setItemBackgroundColor(restoreIndex, R.color.groupColor)

                    setItemSeparatorViewColors(restoreIndex, R.color.transparentViewColor)

                } else {
                    // set default background

                    setItemBackgroundColor(restoreIndex, R.color.transparentViewColor)

                    setItemSeparatorViewColors(restoreIndex, R.color.transparentViewColor)
                }
            }
            // reset the parameter
            restoreIndex = -1
            (this.applicationContext as ApplicationProperty).navigatedFromPropertySubPage = false
            // realign the context objects to the bluetoothManager
            ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceMainActivity, this)
            ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

            // update info-header and states
            if(ApplicationProperty.bluetoothConnectionManger.deviceInfoHeaderData.valid) {
                setDeviceInfoHeader(
                    ApplicationProperty.bluetoothConnectionManger.deviceInfoHeaderData.imageID,
                    ApplicationProperty.bluetoothConnectionManger.deviceInfoHeaderData.message
                )
            }
            if((this.applicationContext as ApplicationProperty).uiAdapterChanged){
                (this.applicationContext as ApplicationProperty).uiAdapterChanged = false

                // TODO: update data in a loop!

            }

            // TODO: detect state-changes and update the property-list-items

        } else {
            // creation or resume action:

            // make sure to set the right Name for the device
            findViewById<TextView>(R.id.deviceTypeHeaderName).text =
                ApplicationProperty.bluetoothConnectionManger.currentDevice?.name

            // Update the connection status
            setUIConnectionStatus(ApplicationProperty.bluetoothConnectionManger.isConnected)

            // show the loading circle
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

            // check if this is a call on creation or resume:
            if (this.activityWasSuspended) {

                // realign objects
                //ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this@DeviceMainActivity, this)
                //ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

                // try to reconnect
                ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
                this.activityWasSuspended = false
            } else {
                // must be the creation process -> start property listing
                ApplicationProperty.bluetoothConnectionManger.startDevicePropertyListing()
            }

            // how to confirm the device-properties
            // maybe make a changed-parameter in the device firmware and call that to upgrade performance and hold the line clear for communication?
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
        Log.d("M:CB:onPropBtnClk", "Property element was clicked. Element-Type is BUTTON at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}")
        // NOTE: the button has no state the execution command contains always "1"
        ApplicationProperty.bluetoothConnectionManger.sendData("C${a8BitValueToString(devicePropertyListContentInformation.elementID)}1$")
    }

    override fun onPropertyElementSwitchClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation,
        switch: SwitchCompat
    ) {
        Log.d("M:CB:onPropSwitchClk", "Property element was clicked. Element-Type is SWITCH at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}")

        // TODO: check if the newState comes with the right terminology

        val c = when(switch.isChecked){
            true -> '1'
            else -> '0'
        }
        ApplicationProperty.bluetoothConnectionManger.sendData("C${a8BitValueToString(devicePropertyListContentInformation.elementID)}$c$")
    }

    override fun onSeekBarPositionChange(
        index: Int,
        newValue: Int,
        changeType: Int
    ) {
        val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(index)

        Log.d("M:CB:onSeekBarChange", "Property element was clicked. Element-Type is SEEKBAR at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}\n\n" +
                "SeekBar specific values:\n" +
                "New Value: $newValue\n" +
                "Change-Type: ${when(changeType){
                    SEEK_BAR_START_TRACK -> "Start tracking"
                    SEEK_BAR_PROGRESS_CHANGING -> "Tracking"
                    SEEK_BAR_STOP_TRACK -> "Stop tracking"
                    else -> "error" }}")

        if(changeType == SEEK_BAR_PROGRESS_CHANGING){
            val bitValue =
                percentTo8Bit(newValue)

            ApplicationProperty.bluetoothConnectionManger.sendData("C${a8BitValueToString(devicePropertyListContentInformation.elementID)}${a8BitValueToString(bitValue)}$")
        }
    }

    override fun onNavigatableElementClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        Log.d("M:CB:onNavElementClk", "Property element was clicked. Element-Type is Complex/Navigate forward at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}")

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
            COMPLEX_PROPERTY_TYPE_ID_TIME_ELAPSE_SELECTOR -> {
                // navigate to the time selector page with the countdown-select-mode
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
                // navigate to the navigator page
            }
            else -> {
                // what to do here??
            }
        }
    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
        // same procedure like onBackPressed!

        ApplicationProperty.bluetoothConnectionManger.clear()

        // start main activity to select a new device!!!

        finish()
    }

    fun onDeviceSettingsButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // re-connect
        // confirm device-properties???
        // re-connect and re-new the device-properties???
        //ApplicationProperty.bluetoothConnectionManger.close()
        //ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()


        // prevent the normal "onPause" execution
        (this.applicationContext as ApplicationProperty).noConnectionKillOnPauseExecution = true
        // navigate to the device settings activity..
        val intent = Intent(this@DeviceMainActivity, DeviceSettingsActivity::class.java)
        startActivity(intent)
    }

    fun onReconnectDevice(@Suppress("UNUSED_PARAMETER")view: View){
        // re-connect
        // confirm device-properties???
        // re-connect and re-new the device-properties???


        ApplicationProperty.bluetoothConnectionManger.close()
        ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()

    }


    private fun setUIConnectionStatus(status :Boolean){

        runOnUiThread {
            val statusText =
                findViewById<TextView>(R.id.deviceConnectionStatusTextView)

            if (status) {
                statusText.setTextColor(getColor(R.color.connectedTextColor))
                statusText.text = getString(R.string.DMA_ConnectionStatus_connected)
            } else {
                statusText.setTextColor(getColor(R.color.disconnectedTextColor))
                statusText.text = getString(R.string.DMA_ConnectionStatus_disconnected)
            }
        }
    }

    private fun setDeviceInfoHeader(imageID: Int, message: String){

        // TODO: Problem: the view is outside of the screen if the text is too long -> it must be wrapped

        runOnUiThread {
            findViewById<ImageView>(R.id.deviceInfoSubHeaderImage).setImageResource(
                resourceIdForImageId(imageID)
            )
            findViewById<TextView>(R.id.deviceInfoSubHeaderTextView).text = message
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        // this callback will only invoked in this activity if this is a re-connect-process (the initial connection attempt will be executed in the loading activity!)

        // set the UI State to connected:
        this.setUIConnectionStatus(state)
        // stop the loading circle and set the info-header
        if(state) {
            // send a test command
            ApplicationProperty.bluetoothConnectionManger.testConnection(200)// TODO: this must be tested
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

        // TODO: check if the execution in the UI-Thread is really necessary, maybe this breaks the UI performance


        runOnUiThread{

                // maybe there is no need to call this???
                // adapter.submitList(list) to proof

            this.devicePropertyListViewAdapter.notifyItemInserted(ApplicationProperty.bluetoothConnectionManger.uIAdapterList.size - 1)
        }
    }

    override fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int) {
        super.onSimplePropertyStateChanged(UIAdapterElementIndex, newState)

        runOnUiThread {
            val uIElement =
                ApplicationProperty.bluetoothConnectionManger.uIAdapterList.elementAt(
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

                    // make the bottom separator line transparent
                    // TODO: test this
                    //holder.constraintLayout.findViewById<View>(R.id.bottomSeparator).setBackgroundResource(R.color.transparentViewColor)



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
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(elementToRender.imageID)
                    )



                    // set the text for the element and show the textView
                    val tv = holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)

                    tv.visibility = View.VISIBLE
                    tv.text = elementToRender.elementText
                    tv.textSize = 16F
                    tv.setTypeface(tv.typeface, Typeface.BOLD)
                    //.visibility = View.VISIBLE
                    //holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                }
                PROPERTY_ELEMENT -> {

                    holder.linearLayout.setBackgroundColor(activityContext.getColor(R.color.groupColor))

                    // get the element
                    //val element = devicePropertyAdapter.elementAt(position)
                    // show / hide the navigate-image
                    if(elementToRender.canNavigateForward)
                        holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                    //else  holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE
                    // if the property is part of a group -> make the separator-lines transparent

                    // TODO: test this
                    //holder.constraintLayout.findViewById<View>(R.id.topSeparator).setBackgroundResource(R.color.transparentViewColor)
                    //holder.constraintLayout.findViewById<View>(R.id.bottomSeparator).setBackgroundResource(R.color.transparentViewColor)


                    // set the appropriate image for the imageID
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(elementToRender.imageID))


                    // set the appropriate elements for the type of the property:
                    when(elementToRender.propertyType){
                        -1 -> return // must be error
                        0 -> return // must be error
                        PROPERTY_TYPE_BUTTON -> {
                            // get the button
                            val button = holder.linearLayout.findViewById<Button>(R.id.elementButton)
                            // show the button
                            button.visibility = View.VISIBLE
                            // set the text of the button
                            button.text = elementToRender.elementText
                            // hide the element text
                            //holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).visibility = View.GONE
                            // set the onClick handler
                            button.setOnClickListener{
                                itemClickListener.onPropertyElementButtonClick(position, elementToRender)
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
                            val textView = holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = elementToRender.elementText
                        }
                        PROPERTY_TYPE_LEVEL_INDICATOR -> {
                            val textView = holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = elementToRender.elementText

                            // show a level indication e.g. "96%"
                            val percentageLevelPropertyGenerator = PercentageLevelPropertyGenerator(elementToRender.simplePropertyState)

                            val levelIndication = holder.linearLayout.findViewById<TextView>(R.id.levelIndicationTextView)
                            levelIndication.visibility = View.VISIBLE
                            levelIndication.text = percentageLevelPropertyGenerator.percentageString
                            levelIndication.setTextColor(percentageLevelPropertyGenerator.colorID)
                        }
                        PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> {
                            // show the textView
                            val textView = holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = elementToRender.elementText

                            holder.linearLayout.findViewById<TextView>(R.id.levelIndicationTextView).visibility = View.GONE
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
                            val textView = holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = elementToRender.elementText
                            // show the navigate arrow
                            holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                        }
                    }
                }
                SEPARATOR_ELEMENT -> {
                    // only show the double line to separate elements
                    //holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                    holder.linearLayout.findViewById<View>(R.id.topSeparator).setBackgroundResource(R.color.separatorColor)
                    holder.linearLayout.findViewById<View>(R.id.bottomSeparator).setBackgroundResource(R.color.separatorColor)
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


        // TODO: test this implementation!!!

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }
    }
}
