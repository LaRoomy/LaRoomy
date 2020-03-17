package com.example.laroomy

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), OnItemClickListener, BLEConnectionManager.BleEventCallback {

    private var availableDevices = ArrayList<LaRoomyDevicePresentationModel>()
    get() {
        field.clear()
        field = (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.bondedLaRoomyDevices
        return field
    }

    private lateinit var availableDevicesRecyclerView: RecyclerView
    private lateinit var availableDevicesViewAdapter: RecyclerView.Adapter<*>
    private lateinit var availableDevicesViewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.reAlignContextObjects(this, this@MainActivity, this)

        this.availableDevicesViewManager = LinearLayoutManager(this)

        this.availableDevicesViewAdapter = AvailableDevicesListAdapter(this.availableDevices, this)

        this.availableDevicesRecyclerView =
            findViewById<RecyclerView>(R.id.AvailableDevicesListView)
                .apply {
                    setHasFixedSize(true)
                    layoutManager = availableDevicesViewManager
                    adapter = availableDevicesViewAdapter
            }
    }

    override fun onStart() {
        super.onStart()
        (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.checkBluetoothEnabled()
    }

    override fun onResume() {
        super.onResume()
        // update the device-list
        updateAvailableDevices()
        this.availableDevicesViewAdapter.notifyDataSetChanged()
    }

    override fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel) {
        if(index > 0) {
            var intent = Intent(this@MainActivity, LoadingActivity::class.java)
            intent.putExtra("BondedDeviceIndex", index)
            startActivity(intent)
        }
    }

    private fun notifyUser(message: String, type: Int){
        val notificationView = findViewById<TextView>(R.id.MA_UserNotificationView)
        notificationView.text = message

        when(type){
            ERROR_MESSAGE -> notificationView.setTextColor(getColor(R.color.ErrorColor))
            WARNING_MESSAGE -> notificationView.setTextColor(getColor(R.color.WarningColor))
            INFO_MESSAGE -> notificationView.setTextColor(getColor(R.color.InfoColor))
            else -> notificationView.setTextColor(getColor(R.color.InfoColor))
        }
    }

    private fun updateAvailableDevices(){
        this.availableDevices = (this.applicationContext as ApplicationProperty).bluetoothConnectionManger.bondedLaRoomyDevices
    }

    class AvailableDevicesListAdapter(
        private val laRoomyDevListAdapter : ArrayList<LaRoomyDevicePresentationModel>,
        private val itemClickListener: OnItemClickListener
    ) : RecyclerView.Adapter<AvailableDevicesListAdapter.DSLRViewHolder>() {

        class DSLRViewHolder(val linearLayout: LinearLayout) :
            RecyclerView.ViewHolder(linearLayout){

            fun bind(data: LaRoomyDevicePresentationModel, itemClick: OnItemClickListener, position: Int){
                itemView.setOnClickListener{
                    itemClick.onItemClicked(position, data)
                }
            }
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DSLRViewHolder {

            val linearLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.available_device_list_item, parent, false) as LinearLayout

            return DSLRViewHolder(linearLayout)
        }

        override fun onBindViewHolder(holder: DSLRViewHolder, position: Int) {
            holder.linearLayout.findViewById<TextView>(R.id.deviceNameTextView).text = laRoomyDevListAdapter[position].Name

            // currently there is only one image. In the future there must be implemented multiple images for multiple device-types
            // TODO: set the appropriate image for the device type

            holder.bind(laRoomyDevListAdapter[position], itemClickListener, position)
        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }
    }

//    override fun onAuthenticationSuccessful() {
//    }

    override fun onComponentError(message: String) {
        notifyUser(message, ERROR_MESSAGE)
    }

/*
    override fun onConnectionAttemptFailed(message: String) {
    }

    override fun onConnectionStateChanged(state: Boolean) {
    }

    override fun onDataReceived(data: String?) {
    }

    override fun onDataSent(data: String?) {
    }

    override fun onDeviceReadyForCommunication() {
    }
*/
}
