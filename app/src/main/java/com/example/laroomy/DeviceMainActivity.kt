package com.example.laroomy

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class DeviceMainActivity : AppCompatActivity(), BLEConnectionManager.PropertyCallback, BLEConnectionManager.BleEventCallback, OnPropertyClickListener {

    private var isUpToDate = false

    // device property list elements
    private lateinit var devicePropertyListRecyclerView: RecyclerView
    private lateinit var devicePropertyListViewAdapter: RecyclerView.Adapter<*>
    private lateinit var devicePropertyListLayoutManager: RecyclerView.LayoutManager
    private var devicePropertyList= ArrayList<DevicePropertyListContentInformation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_main)

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@DeviceMainActivity, this)
        ApplicationProperty.bluetoothConnectionManger.setPropertyEventHandler(this)

        // init recycler view!!
        this.devicePropertyListLayoutManager = LinearLayoutManager(this)

        // set empty list placeholder in array-list
        val dc = DevicePropertyListContentInformation()
        dc.elementType = NO_CONTENT_ELEMENT
        this.devicePropertyList.add(dc)

        // bind array to adapter
        this.devicePropertyListViewAdapter = DevicePropertyListAdapter(this.devicePropertyList, this, this@DeviceMainActivity)

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
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        setUIConnectionStatus(ApplicationProperty.bluetoothConnectionManger.isConnected)
        this.isUpToDate =
            ApplicationProperty.bluetoothConnectionManger.isPropertyUpToDate
        if(this.isUpToDate){
            this.adaptUIToPropertyListing()
        }
    }

    override fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation) {
        if(devicePropertyList.elementAt(index).canNavigateForward && (devicePropertyList.elementAt(index).elementType == PROPERTY_ELEMENT)){

            // navigate to the appropriate property-page

            // make sure the onPause routine does not disconnect the device

            // animate the element to indicate the press
        }
    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
    }

    private fun adaptUIToPropertyListing(){

//        Looper.prepare()
//        Handler().postDelayed({
//            // TODO: error!

        Thread(
            Runnable {

            // erase the recycler list
            this.devicePropertyList.clear()
            // create the new list -> start with the groups
            if (ApplicationProperty.bluetoothConnectionManger.laRoomyPropertyGroupList.size > 0) {
                for (laRoomyDevicePropertyGroup in ApplicationProperty.bluetoothConnectionManger.laRoomyPropertyGroupList) {
                    // create the group entry
                    val dpl = DevicePropertyListContentInformation()
                    dpl.elementType = GROUP_ELEMENT
                    dpl.canNavigateForward = false
                    dpl.elementID = laRoomyDevicePropertyGroup.groupID
                    dpl.elementText = laRoomyDevicePropertyGroup.groupName
                    dpl.imageID = laRoomyDevicePropertyGroup.imageID
                    // add the group to the list
                    this.devicePropertyList.add(dpl)
                    // add the device properties to the group by their IDs
                    for (ID in laRoomyDevicePropertyGroup.memberIDs) {
                        ApplicationProperty.bluetoothConnectionManger.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
                            if (laRoomyDeviceProperty.propertyID == ID) {
                                // ID found -> add property to list
                                val propertyEntry = DevicePropertyListContentInformation()
                                propertyEntry.elementType = PROPERTY_ELEMENT
                                propertyEntry.canNavigateForward =
                                    laRoomyDeviceProperty.needNavigation()
                                propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
                                propertyEntry.imageID = laRoomyDeviceProperty.imageID
                                propertyEntry.elementID = laRoomyDeviceProperty.propertyID
                                propertyEntry.elementIndex = index
                                // add it to the list
                                this.devicePropertyList.add(propertyEntry)
                                // ID found -> further processing not necessary -> break the loop
                                return@forEachIndexed
                            }
                        }
                    }
                    val dpl2 = DevicePropertyListContentInformation()
                    dpl2.elementType = SEPARATOR_ELEMENT
                    dpl2.canNavigateForward = false
                    this.devicePropertyList.add(dpl2)
                }
            }
            // now add the properties which are not part of a group
            //for(laRoomyDeviceProperty in ApplicationProperty.bluetoothConnectionManger.laRoomyDevicePropertyList){
            ApplicationProperty.bluetoothConnectionManger.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
                // only add the non-group properties
                if (!laRoomyDeviceProperty.isGroupMember) {
                    // create the entry
                    val propertyEntry = DevicePropertyListContentInformation()
                    propertyEntry.elementType = PROPERTY_ELEMENT
                    propertyEntry.canNavigateForward = laRoomyDeviceProperty.needNavigation()
                    propertyEntry.elementID = laRoomyDeviceProperty.propertyID
                    propertyEntry.imageID = laRoomyDeviceProperty.imageID
                    propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
                    propertyEntry.elementIndex = index
                    // add it to the list
                    this.devicePropertyList.add(propertyEntry)
                }
            }
            // update the recycler
            this.devicePropertyListViewAdapter.notifyDataSetChanged()
        }).start()
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

        if(!this.isUpToDate){
            this.adaptUIToPropertyListing()
        }
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
                    // will be treated like a separator!
                    //holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(R.drawable.placeholder_blue_white)
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = this.activityContext.getString(R.string.LaRoomyDevicePropertyNamePlaceholder)
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                GROUP_ELEMENT -> {
                    // make the element higher than the properties
                    //holder.constraintLayout.minHeight = 120
                    // TODO: make it higher

//                    val lParams = holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).layoutParams as ConstraintLayout.LayoutParams
//                    lParams.setMargins(0,20,0,20)


                    // group elements are for subclassing properties, but cannot navigate forward (by now...)
                    holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE
                    // set the image requested by the device (or a placeholder)
                    holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(devicePropertyAdapter.elementAt(position).imageID)
                    )
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                    // make sure it is visible (TODO: is this really necessary??)
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.VISIBLE

                }
                PROPERTY_ELEMENT -> {
                    if(devicePropertyAdapter.elementAt(position).canNavigateForward)
                        holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                    else  holder.constraintLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE

                    holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(devicePropertyAdapter.elementAt(position).imageID)
                    )
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                    // make sure it is visible (TODO: is this really necessary??)
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.VISIBLE
                }
                SEPARATOR_ELEMENT -> {

                    //holder.constraintLayout.findViewById<View>(R.id.topSeparator).bottom = 10
                    //holder.constraintLayout.findViewById<View>(R.id.bottomSeparator).top = 10

                    // TODO: make it better!

                    //holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(R.drawable.placeholder_blue_white)
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = this.activityContext.getString(R.string.LaRoomyDevicePropertyNamePlaceholder)
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                else -> {
                    // will be treated like a separator!
                    //holder.constraintLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(R.drawable.placeholder_blue_white)
                    holder.constraintLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = this.activityContext.getString(R.string.LaRoomyDevicePropertyNamePlaceholder)
                    holder.constraintLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return devicePropertyAdapter.size
        }
    }
}
