package com.example.laroomy

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView


class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback, OnPropertyClickListener {

    // device property list elements
    private lateinit var devicePropertyListRecyclerView: RecyclerView
    private lateinit var devicePropertyListViewAdapter: RecyclerView.Adapter<*>
    private lateinit var devicePropertyListLayoutManager: RecyclerView.LayoutManager
    //private var devicePropertyList= ArrayList<DevicePropertyListContentInformation>()

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
        this.devicePropertyListViewAdapter = DevicePropertyListAdapter(ApplicationProperty.bluetoothConnectionManger.UIAdapterList, this, this@DeviceMainActivity)

        // bind the elements to the recycler
        this.devicePropertyListRecyclerView =
            findViewById<RecyclerView>(R.id.devicePropertyListView)
                .apply {
                    setHasFixedSize(true)
                    layoutManager = devicePropertyListLayoutManager
                    adapter = devicePropertyListViewAdapter
                }
    }

    override fun onPause() {
        super.onPause()

        // TODO: suspend connection (maybe delayed in background???)

        ApplicationProperty.bluetoothConnectionManger.clear()
    }

    override fun onBackPressed() {
        super.onBackPressed()

        // TODO: finish activity
    }

    override fun onResume() {
        super.onResume()
        setUIConnectionStatus(ApplicationProperty.bluetoothConnectionManger.isConnected)

        this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.VISIBLE

//        if (this.isLastConnectedDevice && (ApplicationProperty.bluetoothConnectionManger.laRoomyDevicePropertyList.size == 0)) {
//            ApplicationProperty.bluetoothConnectionManger.startDevicePropertyListing()
//        } else {
//            ApplicationProperty.bluetoothConnectionManger.startPropertyConfirmationProcess()
//        }

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
        //if(devicePropertyList.elementAt(index).canNavigateForward && (devicePropertyList.elementAt(index).elementType == PROPERTY_ELEMENT)){

            // navigate to the appropriate property-page

            // make sure the onPause routine does not disconnect the device

            // animate the element to indicate the press
       // }
    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
        // same procedure like onBackPressed!
    }


    private fun setUIConnectionStatus(status :Boolean){

        val statusText =
            findViewById<TextView>(R.id.deviceConnectionStatusTextView)

        if(status){
            statusText.setTextColor(getColor(R.color.connectedTextColor))
            statusText.text = getString(R.string.DMA_ConnectionStatus_connected)
        }
        else{
            statusText.setTextColor(getColor(R.color.disconnectedTextColor))
            statusText.text = getString(R.string.DMA_ConnectionStatus_disconnected)
        }
    }

    override fun onConnectionStateChanged(state: Boolean) {
        super.onConnectionStateChanged(state)
        this.setUIConnectionStatus(state)
    }

    override fun onGroupDataRetrievalCompleted(groups: ArrayList<LaRoomyDevicePropertyGroup>) {
        super.onGroupDataRetrievalCompleted(groups)

        // this could be a race condition
        // what happens when the device has no groups or the groups are retrieved before the activity is loaded

/*
        if(!this.isUpToDate){

        }
*/
    }

    override fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>) {
        super.onUIAdaptableArrayListGenerationComplete(UIArray)
        runOnUiThread {
//            this.devicePropertyList.clear()
//            this.devicePropertyList = UIArray
            this.devicePropertyListViewAdapter.notifyDataSetChanged()
            this.findViewById<SpinKitView>(R.id.devicePageSpinKit).visibility = View.GONE
        }

        // TODO: if there is no DeviceInfoHeader -> Hide it!!!!
    }

    // device property list adapter:
    class DevicePropertyListAdapter(
        private val devicePropertyAdapter: ArrayList<DevicePropertyListContentInformation>,
        private val itemClickListener: OnPropertyClickListener,
        private val activityContext: Context
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
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).visibility = View.VISIBLE
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
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
                            val button = holder.constraintLayout.findViewById<Button>(R.id.elementButton)
                            // show the button
                            button.visibility = View.VISIBLE
                            // set the text of the button
                            button.text = element.elementText
                            // hide the element text
                            holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).visibility = View.GONE
                        }
                        PROPERTY_TYPE_SWITCH -> {
                            // show the switch
                            holder.constraintLayout.findViewById<Switch>(R.id.elementSwitch).visibility = View.VISIBLE
                            // show the text-view
                            val textView = holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView)
                            textView.visibility = View.VISIBLE
                            // set the text
                            textView.text = element.elementText
                        }
                        PROPERTY_TYPE_LEVEL_SELECTOR -> {
                            // show seek-bar layout container
                            holder.constraintLayout.findViewById<ConstraintLayout>(R.id.seekBarContainer).visibility = View.VISIBLE
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
        }

        override fun getItemCount(): Int {
            return devicePropertyAdapter.size
        }
    }
}
