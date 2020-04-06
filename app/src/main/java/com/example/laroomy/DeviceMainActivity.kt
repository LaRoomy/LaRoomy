package com.example.laroomy

import android.content.Context
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@DeviceMainActivity, this)
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
                ApplicationProperty.bluetoothConnectionManger.UIAdapterList,
                this,
                this@DeviceMainActivity,
                this)

        // bind the elements to the recycler
        this.devicePropertyListRecyclerView =
            findViewById<RecyclerView>(R.id.devicePropertyListView)
                .apply {
                    //setHasFixedSize(true)
                    layoutManager = devicePropertyListLayoutManager
                    adapter = devicePropertyListViewAdapter
                }
    }

    override fun onPause() {
        super.onPause()

        // TODO: suspend connection (maybe delayed in background???)

        ApplicationProperty.bluetoothConnectionManger.close()

        this.activityWasSuspended = true
    }

    override fun onBackPressed() {
        super.onBackPressed()

        // TODO: finish activity

        ApplicationProperty.bluetoothConnectionManger.clear()
        finish()
    }

    override fun onResume() {
        super.onResume()
        Log.d("M:onResume", "Activity resumed. Previous loading done: ${this.activityWasSuspended}")

        setUIConnectionStatus(ApplicationProperty.bluetoothConnectionManger.isConnected)

        this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

//        if (this.isLastConnectedDevice && (ApplicationProperty.bluetoothConnectionManger.laRoomyDevicePropertyList.size == 0)) {
//            ApplicationProperty.bluetoothConnectionManger.startDevicePropertyListing()
//        } else {
//            ApplicationProperty.bluetoothConnectionManger.startPropertyConfirmationProcess()
//        }

        if(this.activityWasSuspended) {
            ApplicationProperty.bluetoothConnectionManger.connectToLastSuccessfulConnectedDevice()
            this.activityWasSuspended = false
        } else
            ApplicationProperty.bluetoothConnectionManger.startDevicePropertyListing()


/*
        if(ApplicationProperty.bluetoothConnectionManger.isUIDataReady){
            //this.devicePropertyList.clear()
            //this.devicePropertyList = ApplicationProperty.bluetoothConnectionManger.UIAdapterList
            this.devicePropertyListViewAdapter.notifyDataSetChanged()
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
        }
        else {
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE
        }
*/
    }

    override fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation) {

        Log.d("M:onPropEntryClk", "Property list entry was clicked at index: $index")

        //if(devicePropertyList.elementAt(index).canNavigateForward && (devicePropertyList.elementAt(index).elementType == PROPERTY_ELEMENT)){

            // navigate to the appropriate property-page

            // make sure the onPause routine does not disconnect the device

            // animate the element to indicate the press
       // }
    }

    override fun onPropertyElementButtonClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        Log.d("M:CB:onPropBtnClk", "Property element was clicked. Element-Type is BUTTON at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}")
    }

    override fun onPropertyElementSwitchClick(
        index: Int,
        devicePropertyListContentInformation: DevicePropertyListContentInformation
    ) {
        Log.d("M:CB:onPropSwitchClk", "Property element was clicked. Element-Type is SWITCH at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}")

    }

    override fun onSeekBarPositionChange(
        index: Int,
        newValue: Int,
        changeType: Int
    ) {
        val devicePropertyListContentInformation = ApplicationProperty.bluetoothConnectionManger.UIAdapterList.elementAt(index)

        Log.d("M:CB:onSeekBarChange", "Property element was clicked. Element-Type is SEEKBAR at index: $index\n\nData is:\n" +
                "Type: ${devicePropertyListContentInformation.propertyType}\n" +
                "Element-Text: ${devicePropertyListContentInformation.elementText}\n" +
                "Element-ID: ${devicePropertyListContentInformation.elementID}\n" +
                "Element-Index: ${devicePropertyListContentInformation.globalIndex}\n\n" +
                "SeekBar specific values:\n" +
                "New Value: $newValue\n" +
                "Change-Type: ${when(changeType){
                    SEEKBAR_START_TRACK -> "Start tracking"
                    SEEKBAR_PROGRESS_CHANGING -> "Tracking"
                    SEEKBAR_STOP_TRACK -> "Stop tracking"
                    else -> "error" }}")

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
    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
        // same procedure like onBackPressed!

        ApplicationProperty.bluetoothConnectionManger.clear()
        finish()
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

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        this.setUIConnectionStatus(state)

        if(state) {
            runOnUiThread {
                this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
            }
        }
    }

    override fun onDeviceReadyForCommunication() {
        super.onDeviceReadyForCommunication()



        //if(this.activityWasSuspended)
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
    }

//    override fun onGroupDataRetrievalCompleted(groups: ArrayList<LaRoomyDevicePropertyGroup>) {
//        super.onGroupDataRetrievalCompleted(groups)
//
//        // this could be a race condition
//        // what happens when the device has no groups or the groups are retrieved before the activity is loaded
//
///*
//        if(!this.isUpToDate){
//
//        }
//*/
//    }

    override fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>) {
        super.onUIAdaptableArrayListGenerationComplete(UIArray)
        runOnUiThread {
//            this.devicePropertyList.clear()
//            this.devicePropertyList = UIArray
            //this.devicePropertyListViewAdapter.notifyDataSetChanged()
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
        }

        // TODO: if there is no DeviceInfoHeader -> Hide it!!!!
    }

    override fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation) {
        super.onUIAdaptableArrayListItemAdded(item)
        runOnUiThread{
            this.devicePropertyListViewAdapter.notifyItemInserted(ApplicationProperty.bluetoothConnectionManger.UIAdapterList.size - 1)
        }
    }

    // device property list adapter:
    class DevicePropertyListAdapter(
        private val devicePropertyAdapter: ArrayList<DevicePropertyListContentInformation>,
        private val itemClickListener: OnPropertyClickListener,
        private val activityContext: Context,
        private val callingActivity: DeviceMainActivity
    ) : RecyclerView.Adapter<DevicePropertyListAdapter.DPLViewHolder>() {

        class DPLViewHolder(val constraintLayout: ConstraintLayout)
            : RecyclerView.ViewHolder(constraintLayout) {

            fun bind(data: DevicePropertyListContentInformation, itemClick: OnPropertyClickListener, position: Int){
                itemClick.onPropertyClicked(position, data)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DPLViewHolder {
            val constraintLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.device_property_list_element, parent, false) as ConstraintLayout
            return DPLViewHolder(constraintLayout)
        }

        override fun onBindViewHolder(holder: DPLViewHolder, position: Int) {

            when(devicePropertyAdapter.elementAt(position).elementType){
                UNDEFINED_ELEMENT -> {
                    // should not happen
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                GROUP_ELEMENT -> {

                    holder.constraintLayout.setBackgroundColor(activityContext.getColor(R.color.groupColor))

                    // make sure it is visible (TODO: is this really necessary??)
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.VISIBLE

                    // make the element higher by setting the visibility of the group-border-view to: visible
                    holder.constraintLayout.findViewById<View>(R.id.startSeparator).visibility = View.VISIBLE

                    // group elements are for subclassing properties, but cannot navigate forward (by now... ;) )
                    holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE

                    // the button mustn't be visible (and isn't by default)
                    //holder.constraintLayout.findViewById<Button>(R.id.elementButton).visibility = View.GONE

                    // set the image requested by the device (or a placeholder)
                    holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(devicePropertyAdapter.elementAt(position).imageID)
                    )
                    // set the text for the element and show the textView
                    val tV = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                    tV.visibility = View.VISIBLE
                    tV.text = devicePropertyAdapter.elementAt(position).elementText
                    tV.textSize = 16F
                    tV.setTypeface(tV.typeface, Typeface.BOLD)
                    //.visibility = View.VISIBLE
                    //holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                }
                PROPERTY_ELEMENT -> {

                    holder.constraintLayout.setBackgroundColor(activityContext.getColor(R.color.groupColor))

                    // get the element
                    val element = devicePropertyAdapter.elementAt(position)
                    // show / hide the navigate-image
                    if(element.canNavigateForward)
                        holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                    else  holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE
                    // set the appropriate image for the imageID
                    holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(element.imageID))
                    // set the appropriate elements for the type of the property:
                    when(element.propertyType){
                        -1 -> return // must be error
                        0 -> return // must be error
                        PROPERTY_TYPE_BUTTON -> {
                            // get the button
                            val button = holder.constraintLayout.findViewById<Button>(R.id.elementButton)
                            // show the button
                            button.visibility = View.VISIBLE
                            // set the text of the button
                            button.text = element.elementText
                            // hide the element text
                            holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).visibility = View.GONE
                            // set the onClick handler
                            button.setOnClickListener{
                                itemClickListener.onPropertyElementButtonClick(position, devicePropertyAdapter.elementAt(position))
                            }
                        }
                        PROPERTY_TYPE_SWITCH -> {
                            // get the switch
                            val switch = holder.constraintLayout.findViewById<Switch>(R.id.elementSwitch)
                            // show the switch
                            switch.visibility = View.VISIBLE
                            // set the onClick handler
                            switch.setOnClickListener{
                                itemClickListener.onPropertyElementSwitchClick(position, devicePropertyAdapter.elementAt(position))
                            }
                            // show the text-view
                            val textView = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = element.elementText
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {
                            // show seek-bar layout container
                            holder.constraintLayout.findViewById<ConstraintLayout>(R.id.seekBarContainer).visibility = View.VISIBLE
                            // set the handler for the seekBar
                            devicePropertyAdapter.elementAt(position).handler = callingActivity
                            holder.constraintLayout.findViewById<SeekBar>(R.id.elementSeekBar).setOnSeekBarChangeListener(devicePropertyAdapter.elementAt(position))
                            // show the text-view
                            val textView = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = element.elementText
                        }
                        PROPERTY_TYPE_LEVEL_INDICATOR -> {
                            val textView = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = element.elementText

                            // show a level indication !!!!!!!!!!
                        }
                        PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> {
                            // show the textView
                            val textView = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = element.elementText
                        }
                        else -> {
                            // must be complex type!

                            // set handler
                            holder.constraintLayout.setOnClickListener {
                                itemClickListener.onNavigatableElementClick(
                                    position,
                                    devicePropertyAdapter.elementAt(position)
                                )
                            }

                            // show the textView
                            val textView = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = element.elementText
                            // show the navigate arrow
                            holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                        }
                    }
                }
                SEPARATOR_ELEMENT -> {
                    // only show the double line to separate elements
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                else -> {
                    // should not happen
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
            }

            // bind holder???
            holder.bind(devicePropertyAdapter.elementAt(position), itemClickListener, position)
        }

        override fun getItemCount(): Int {
            return devicePropertyAdapter.size
        }
    }
}
