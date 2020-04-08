package com.example.laroomy

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), OnItemClickListener, BLEConnectionManager.BleEventCallback {

    private var availableDevices = ArrayList<LaRoomyDevicePresentationModel>()
    get() {
        field.clear()
        field = ApplicationProperty.bluetoothConnectionManger.bondedLaRoomyDevices
        return field
    }

    private lateinit var availableDevicesRecyclerView: RecyclerView
    private lateinit var availableDevicesViewAdapter: RecyclerView.Adapter<*>
    private lateinit var availableDevicesViewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApplicationProperty.bluetoothConnectionManger.reAlignContextObjects(this, this@MainActivity, this)

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

//    override fun onStart() {
//        super.onStart()
//
//        //ApplicationProperty.bluetoothConnectionManger.checkBluetoothEnabled()
//    }

    override fun onResume() {
        super.onResume()

        ApplicationProperty.bluetoothConnectionManger.checkBluetoothEnabled()

        // ! realign context objects in the bluetooth manager, if this is called after a back-navigation from the Loading-Activity or so...

        // update the device-list
        updateAvailableDevices()


        //reset the selected color
    }

    override fun onPause() {
        super.onPause()

        // TODO: if the loading activity fails, the main activity must be re-initiated

        //finish()// questionable!!!!!!!!!!!!!!!
    }

    override fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel) {

//        val ll = availableDevicesViewManager.findViewByPosition(index) as? LinearLayout
//        val leftView = ll?.findViewById<View>(R.id.leftBorderView)
//        val rightView = ll?.findViewById<View>(R.id.rightBorderView)
//
//        leftView?.setBackgroundColor(getColor(R.color.selectedSeparatorColor))
//        rightView?.setBackgroundColor(getColor(R.color.selectedSeparatorColor))

        setItemColor(index, R.color.selectedSeparatorColor)

        //ll?.setBackgroundColor(getColor(R.color.colorPrimary))
        //ll?.setBackgroundColor()

        val intent = Intent(this@MainActivity, LoadingActivity::class.java)
        intent.putExtra("BondedDeviceIndex", index)
        startActivity(intent)
    }

    fun onHelpImageButtonClick(@Suppress("UNUSED_PARAMETER") view: View){
        val openUrl = Intent(ACTION_VIEW)
        openUrl.data = Uri.parse("https://www.laroomy.de")
        startActivity(openUrl)
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

    private fun setItemColor(index: Int, colorID: Int){
        val ll = availableDevicesViewManager.findViewByPosition(index) as? LinearLayout
        val leftView = ll?.findViewById<View>(R.id.leftBorderView)
        val rightView = ll?.findViewById<View>(R.id.rightBorderView)

        leftView?.setBackgroundColor(getColor(colorID))
        rightView?.setBackgroundColor(getColor(colorID))

    }

    private fun updateAvailableDevices(){
        this.availableDevices = ApplicationProperty.bluetoothConnectionManger.bondedLaRoomyDevices
        if(this.availableDevices.size == 0){
            findViewById<TextView>(R.id.AvailableDevicesTextView).text = getString(R.string.MA_NoAvailableDevices)
        }
        else {
            findViewById<TextView>(R.id.AvailableDevicesTextView).text = getString(R.string.MA_AvailableDevicesPresentationTextViewText)
        }
        this.availableDevicesViewAdapter.notifyDataSetChanged()
        this.resetSelectionInDeviceListView()
    }

    private fun resetSelectionInDeviceListView(){
        for(x in 0 until this.availableDevices.size){
            setItemColor(x, R.color.separatorColor)
        }
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
            holder.linearLayout.findViewById<TextView>(R.id.deviceNameTextView).text = laRoomyDevListAdapter[position].name

            // currently there is only one image. In the future there must be implemented multiple images for multiple device-types
            // TODO: set the appropriate image for the device type

            holder.bind(laRoomyDevListAdapter[position], itemClickListener, position)
        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }
    }

    // Interface methods:
    override fun onComponentError(message: String) {
        notifyUser(message, ERROR_MESSAGE)
    }
}
