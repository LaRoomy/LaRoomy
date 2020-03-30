package com.example.laroomy

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_device_main.*


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
        var dc = DevicePropertyListContentInformation()
        dc.elementType = NO_CONTENT_ELEMENT
        this.devicePropertyList.add(dc)

        // bind array to adapter
        this.devicePropertyListViewAdapter = devicePropertyListAdapter(this.devicePropertyList, this, this@DeviceMainActivity)

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

    }

    fun onDiscardDeviceButtonClick(@Suppress("UNUSED_PARAMETER")view: View){
        // finish the activity and navigate back to the start activity (MainActivity)
    }

    private fun adaptUIToPropertyListing(){

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
    class devicePropertyListAdapter(
        private val devicePropertyAdapter: ArrayList<DevicePropertyListContentInformation>,
        private val itemClickListener: OnPropertyClickListener,
        private val activityContext: Context
    ) : RecyclerView.Adapter<devicePropertyListAdapter.DPLViewHolder>() {

        class DPLViewHolder(val linearLayout: LinearLayout)
            : RecyclerView.ViewHolder(linearLayout) {

            fun bind(data: DevicePropertyListContentInformation, itemClick: OnPropertyClickListener, position: Int){
                itemClick.onPropertyClicked(position, data)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DPLViewHolder {
            val linearLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.device_property_list_element, parent, false) as LinearLayout
            return DPLViewHolder(linearLayout)
        }

        override fun onBindViewHolder(holder: DPLViewHolder, position: Int) {

            when(devicePropertyAdapter.elementAt(position).elementType){
                UNDEFINED_ELEMENT -> {
                    // will be treated like a separator!
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(R.drawable.placeholder_blue_white)
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = this.activityContext.getString(R.string.LaRoomyDevicePropertyNamePlaceholder)
                    holder.linearLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                GROUP_ELEMENT -> {
                    // group elements are for subclassing properties, but cannot navigate forward (by now...)
                    holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE
                    // set the image requested by the device (or a placeholder)
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(devicePropertyAdapter.elementAt(position).imageID)
                    )
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                    // make sure it is visible (TODO: is this really necessary??)
                    holder.linearLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.VISIBLE

                }
                PROPERTY_ELEMENT -> {
                    if(devicePropertyAdapter.elementAt(position).canNavigateForward)
                        holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.VISIBLE
                    else  holder.linearLayout.findViewById<ImageView>(R.id.forwardImage).visibility = View.GONE

                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(
                        resourceIdForImageId(devicePropertyAdapter.elementAt(position).imageID)
                    )
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = devicePropertyAdapter.elementAt(position).elementText
                    // make sure it is visible (TODO: is this really necessary??)
                    holder.linearLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.VISIBLE
                }
                SEPARATOR_ELEMENT -> {
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(R.drawable.placeholder_blue_white)
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = this.activityContext.getString(R.string.LaRoomyDevicePropertyNamePlaceholder)
                    holder.linearLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
                else -> {
                    // will be treated like a separator!
                    holder.linearLayout.findViewById<ImageView>(R.id.devicePropertyIdentificationImage).setBackgroundResource(R.drawable.placeholder_blue_white)
                    holder.linearLayout.findViewById<TextView>(R.id.devicePropertyNameTextView).text = this.activityContext.getString(R.string.LaRoomyDevicePropertyNamePlaceholder)
                    holder.linearLayout.findViewById<ConstraintLayout>(R.id.contentHolderLayout).visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return devicePropertyAdapter.size
        }
    }
}
