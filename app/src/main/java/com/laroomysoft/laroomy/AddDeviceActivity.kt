package com.laroomysoft.laroomy

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView

class AddDeviceActivity : AppCompatActivity(),
    OnAddDeviceBondedListItemClickListener, OnAddDeviceScanResultListItemClickListener, BLEDiscoveryCallback {

    private var bondedDevices = ArrayList<LaRoomyDevicePresentationModel>()
        get() {
            field.clear()
            field = ApplicationProperty.bluetoothConnectionManager.bondedLaRoomyDevices
            return field
        }

    private var scanResultListElements = ArrayList<LaRoomyDevicePresentationModel>()

    // UI Elements
    private lateinit var bottomSeparator: View

    private lateinit var bondedDevicesListView: RecyclerView
    private lateinit var bondedDevicesListViewManager: RecyclerView.LayoutManager
    private lateinit var bondedDevicesListAdapter: RecyclerView.Adapter<*>

    private lateinit var backButton: AppCompatImageButton

    private var listUpdateRequired = false
//    private var imageRestoreRequired = false

    private lateinit var scanResultListView: RecyclerView
    private lateinit var scanResultListViewManager: RecyclerView.LayoutManager
    private lateinit var scanResultListAdapter: RecyclerView.Adapter<*>

    private lateinit var scanWorkingIndicator: SpinKitView

    private lateinit var bleDiscoveryManager: BLEDiscoveryManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        // get UI Elements
        this.bottomSeparator = findViewById(R.id.addDeviceActivityBottomSeparator)
        this.backButton = findViewById(R.id.addDeviceActivityBackButton)

        // add back button functionality
        this.backButton.setOnClickListener {
            this.onBackPressed()
        }

        // get the spin-kit
        this.scanWorkingIndicator = findViewById(R.id.addDeviceActivityScanIndicationSpinKitView)

        // init the discovery manager
        this.bleDiscoveryManager = BLEDiscoveryManager(applicationContext, this)

/*
        // hide the bonding-hint container if requested
        if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_DoNotShowBondingHint)){
            findViewById<ConstraintLayout>(R.id.addDeviceActivityBondingHintContainer).visibility = View.GONE
        }
*/

        // initialize the bonding-list recycler-view
        bondedDevicesListViewManager = LinearLayoutManager(this)
        bondedDevicesListAdapter = AddDevicesListAdapter(this.bondedDevices, this, applicationContext)
        bondedDevicesListView = findViewById<RecyclerView>(R.id.addDeviceActivityRecyclerView).apply{
            setHasFixedSize(true)
            layoutManager = bondedDevicesListViewManager
            adapter = bondedDevicesListAdapter
        }

        // initialize the scanResultList recycler-view
        scanResultListViewManager = LinearLayoutManager(this)
        scanResultListAdapter = AddDevicesListAdapter(this.scanResultListElements, this, applicationContext)
        scanResultListView = findViewById<RecyclerView?>(R.id.addDeviceActivityScanResultRecyclerView).apply {
            setHasFixedSize(true)
            layoutManager = scanResultListViewManager
            adapter = scanResultListAdapter
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

        //  if the activity was suspended - refresh the bondedList + clear the scanResultList
        if(this.listUpdateRequired){
            this.listUpdateRequired = false
            this.bondedDevicesListView.adapter = AddDevicesListAdapter(this.bondedDevices, this, applicationContext)

            // delete all scan results
            this.scanResultListElements.clear()
        }

        // start the scan
        this.bleDiscoveryManager.startScan()


//        if(this.imageRestoreRequired){
//            findViewById<AppCompatImageView>(R.id.addDeviceActivityGotoBluetoothImageView).setImageResource(R.drawable.ic_settings_bluetooth_white_48dp)
//            findViewById<AppCompatTextView>(R.id.addDeviceActivityGotoBluetoothTextView).setTextColor(getColor(R.color.fullWhiteTextColor))
//
//            //findViewById<AppCompatImageView>(R.id.addDeviceActivityDoNotShowAgainImageView).setImageResource(R.drawable.ic_block_white_48dp)
//        }
    }

    override fun onPause() {
        super.onPause()
        this.listUpdateRequired = true
        this.bleDiscoveryManager.stopScan()
    }

    class AddDevicesListAdapter(
        private val laRoomyDevListAdapter : ArrayList<LaRoomyDevicePresentationModel>,
        private val deviceListItemClickListener: OnAddDeviceBondedListItemClickListener,
        private val appContext: Context
    ) : RecyclerView.Adapter<AddDevicesListAdapter.DSLRViewHolder>() {

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
                deviceListItemClickListener.onBondedItemClicked(position)
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

    override fun onBondedItemClicked(index: Int) {

        (applicationContext as ApplicationProperty).addedDevices.add(
            bondedDevices.elementAt(index).address,
            bondedDevices.elementAt(index).name
        )
        (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = true
        finish()
    }

    override fun onScanResultListItemClicked(index: Int) {

        (applicationContext as ApplicationProperty).addedDevices.add(
            scanResultListElements.elementAt(index).address,
            scanResultListElements.elementAt(index).name
        )
        (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = true
        finish()
    }

    override fun scanStarted() {
        this.scanWorkingIndicator.visibility = View.VISIBLE
    }

    override fun scanStopped() {
        this.scanWorkingIndicator.visibility = View.INVISIBLE
    }

    override fun scanFail(errorCode: Int) {
        super.scanFail(errorCode)
    }

    override fun deviceFound(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            this.permissionError()
        } else {
            val element = LaRoomyDevicePresentationModel()
            element.address = device.address
            element.name = device.name
            this.scanResultListElements.add(element)
        }
    }

    override fun permissionError() {
        this.bleDiscoveryManager.stopScan()

        // TODO: do not finish if the BLUETOOTH_SCAN permission is not present - notify user instead and propose what he could do..

        finish()
    }

//    fun onAddDeviceActivityGotoBluetoothButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
//
//        findViewById<AppCompatImageView>(R.id.addDeviceActivityGotoBluetoothImageView).setImageResource(R.drawable.ic_settings_bluetooth_yellow_48dp)
//        findViewById<AppCompatTextView>(R.id.addDeviceActivityGotoBluetoothTextView).setTextColor(getColor(R.color.yellowAccentColor))
//        this.imageRestoreRequired = true
//
//        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
//        startActivity(intent)
//    }


/*
    fun onAddDeviceActivityDoNotShowAgainButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        findViewById<AppCompatImageView>(R.id.addDeviceActivityDoNotShowAgainImageView).setImageResource(R.drawable.ic_block_yellow_48dp)
        (applicationContext as ApplicationProperty).saveBooleanData(true, R.string.FileKey_AppSettings, R.string.DataKey_DoNotShowBondingHint)

        findViewById<ConstraintLayout>(R.id.addDeviceActivityBondingHintContainer).visibility = View.GONE
    }
*/
}