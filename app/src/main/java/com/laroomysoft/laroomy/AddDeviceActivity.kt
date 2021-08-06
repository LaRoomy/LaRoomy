package com.laroomysoft.laroomy

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AddDeviceActivity : AppCompatActivity(), OnAddDeviceListItemClickListener {

    private var bondedDevices = ArrayList<LaRoomyDevicePresentationModel>()
        get() {
            field.clear()
            field = ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices
            return field
        }

    // UI Elements
    private lateinit var bottomSeparator: View
    private lateinit var bondedDevicesListView: RecyclerView
    private lateinit var bondedDevicesListViewManager: RecyclerView.LayoutManager
    private lateinit var bondedDevicesListAdapter: RecyclerView.Adapter<*>

    private var listUpdateRequired = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        // get UI Elements
        this.bottomSeparator = findViewById(R.id.addDeviceActivityBottomSeparator)

        // initialize the recycler-view
        bondedDevicesListViewManager = LinearLayoutManager(this)
        bondedDevicesListAdapter = BondedDevicesListAdapter(this.bondedDevices, this)
        bondedDevicesListView = findViewById<RecyclerView>(R.id.addDeviceActivityRecyclerView).apply{
            setHasFixedSize(true)
            layoutManager = bondedDevicesListViewManager
            adapter = bondedDevicesListAdapter
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // if this is the landscape mode, hide the bottom separator
        bottomSeparator.visibility = if(this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
            View.GONE
        } else {
            View.VISIBLE
        }

        if(this.listUpdateRequired){
            this.listUpdateRequired = false
            this.bondedDevicesListView.adapter = BondedDevicesListAdapter(this.bondedDevices, this)
        }
    }

    override fun onPause() {
        super.onPause()
        this.listUpdateRequired = true
    }

    class BondedDevicesListAdapter(
        private val laRoomyDevListAdapter : ArrayList<LaRoomyDevicePresentationModel>,
        private val deviceListItemClickListener: OnAddDeviceListItemClickListener
    ) : RecyclerView.Adapter<BondedDevicesListAdapter.DSLRViewHolder>() {

        class DSLRViewHolder(val constraintLayout: ConstraintLayout) :
            RecyclerView.ViewHolder(constraintLayout)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DSLRViewHolder {

            val constraintLayout =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.add_device_list_element, parent, false) as ConstraintLayout

            return DSLRViewHolder(constraintLayout)
        }

        override fun onBindViewHolder(holder: DSLRViewHolder, position: Int) {
            // set the device-name
            holder.constraintLayout.findViewById<AppCompatTextView>(R.id.addDeviceListElementDeviceNameTextView).text = laRoomyDevListAdapter[position].name

            // set the click listener for the add-button
            holder.constraintLayout.findViewById<AppCompatImageButton>(R.id.addDeviceListElementAddButton).setOnClickListener {
                deviceListItemClickListener.onItemClicked(position)
            }

            // TODO: if the device is already in the friendly list, hide the add-button (or change the icon and background)

        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }
    }

    override fun onItemClicked(index: Int) {

        // temp
        val txt = "Element clicked! Index: $index"
        findViewById<AppCompatTextView>(R.id.addDeviceActivityHeaderTextView).text = txt
    }

}