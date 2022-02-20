package com.laroomysoft.laroomy

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
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
    private var imageRestoreRequired = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        // get UI Elements
        this.bottomSeparator = findViewById(R.id.addDeviceActivityBottomSeparator)

/*
        // hide the bonding-hint container if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_DoNotShowBondingHint)){
            findViewById<ConstraintLayout>(R.id.addDeviceActivityBondingHintContainer).visibility = View.GONE
        }
*/

        // initialize the recycler-view
        bondedDevicesListViewManager = LinearLayoutManager(this)
        bondedDevicesListAdapter = BondedDevicesListAdapter(this.bondedDevices, this, applicationContext)
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
            this.bondedDevicesListView.adapter = BondedDevicesListAdapter(this.bondedDevices, this, applicationContext)
        }

        if(this.imageRestoreRequired){
            findViewById<AppCompatImageView>(R.id.addDeviceActivityGotoBluetoothImageView).setImageResource(R.drawable.ic_settings_bluetooth_white_48dp)
            findViewById<AppCompatTextView>(R.id.addDeviceActivityGotoBluetoothTextView).setTextColor(getColor(R.color.fullWhiteTextColor))

            //findViewById<AppCompatImageView>(R.id.addDeviceActivityDoNotShowAgainImageView).setImageResource(R.drawable.ic_block_white_48dp)
        }
    }

    override fun onPause() {
        super.onPause()
        this.listUpdateRequired = true
    }

    class BondedDevicesListAdapter(
        private val laRoomyDevListAdapter : ArrayList<LaRoomyDevicePresentationModel>,
        private val deviceListItemClickListener: OnAddDeviceListItemClickListener,
        private val appContext: Context
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

            if((this.appContext as ApplicationProperty).addedDevices.isAdded(laRoomyDevListAdapter[position].address)){

                holder.constraintLayout.findViewById<AppCompatImageButton>(R.id.addDeviceListElementAddButton).apply {
                    //setBackgroundColor(appContext.getColor(R.color.addDeviceListElementBackgroundColor))
                    setImageResource(R.drawable.ic_done_green_36dp)
                    isEnabled = false
                }
            }
        }

        override fun getItemCount(): Int {
            return laRoomyDevListAdapter.size
        }
    }

    override fun onItemClicked(index: Int) {

        (applicationContext as ApplicationProperty).addedDevices.add(
            bondedDevices.elementAt(index).address,
            bondedDevices.elementAt(index).name
        )
        (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = true
        finish()
    }

    fun onAddDeviceActivityGotoBluetoothButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {

        findViewById<AppCompatImageView>(R.id.addDeviceActivityGotoBluetoothImageView).setImageResource(R.drawable.ic_settings_bluetooth_yellow_48dp)
        findViewById<AppCompatTextView>(R.id.addDeviceActivityGotoBluetoothTextView).setTextColor(getColor(R.color.yellowAccentColor))
        this.imageRestoreRequired = true

        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }

    fun onAddDeviceActivityBackButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        this.onBackPressed()
    }

/*
    fun onAddDeviceActivityDoNotShowAgainButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        findViewById<AppCompatImageView>(R.id.addDeviceActivityDoNotShowAgainImageView).setImageResource(R.drawable.ic_block_yellow_48dp)
        (applicationContext as ApplicationProperty).saveBooleanData(true, R.string.FileKey_AppSettings, R.string.DataKey_DoNotShowBondingHint)

        findViewById<ConstraintLayout>(R.id.addDeviceActivityBondingHintContainer).visibility = View.GONE
    }
*/
}