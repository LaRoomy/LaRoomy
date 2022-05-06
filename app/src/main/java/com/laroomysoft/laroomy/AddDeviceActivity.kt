package com.laroomysoft.laroomy

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ybq.android.spinkit.SpinKitView

const val LIST_TYPE_BONDED_LIST = 1
const val LIST_TYPE_SCAN_LIST = 2

class AddDeviceActivity : AppCompatActivity(),
    OnAddDeviceActivityListItemClickListener, BLEDiscoveryCallback {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if(verboseLog){
                Log.d("MA:requestPermissionLauncher", "Permission-Request result is: $isGranted")
            }
            this.bluetoothPermissionGranted = isGranted
            this.grantPermissionRequestDeclined = !isGranted    // prevent a request loop in onResume
            // if the permission dialog is dismissed, the onResume method is invoked again, and bluetooth enabled state is checked
            // when the permission request is reclined, the UI must go in missing-permission-state, and prevent the invocation
            // of the permission request launcher in onResume, otherwise there will be an infinite loop between onResume and permission launcher
        }

    // TODO: refresh button !


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

    var bluetoothPermissionGranted = false
    var grantPermissionRequestDeclined = false


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
        bondedDevicesListAdapter = AddDevicesListAdapter(this.bondedDevices, this, LIST_TYPE_BONDED_LIST, applicationContext)
        bondedDevicesListView = findViewById<RecyclerView>(R.id.addDeviceActivityRecyclerView).apply{
            setHasFixedSize(true)
            layoutManager = bondedDevicesListViewManager
            adapter = bondedDevicesListAdapter
        }

        // initialize the scanResultList recycler-view
        scanResultListViewManager = LinearLayoutManager(this)
        scanResultListAdapter = AddDevicesListAdapter(this.scanResultListElements, this, LIST_TYPE_SCAN_LIST, applicationContext)
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
            this.bondedDevicesListView.adapter = AddDevicesListAdapter(this.bondedDevices, this, LIST_TYPE_BONDED_LIST, applicationContext)

            // delete all scan results
            this.scanResultListElements.clear()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(!this.grantPermissionRequestDeclined) {
                this.bluetoothPermissionGranted = if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // permission not granted - start the permission launcher
                    if (verboseLog) {
                        Log.d(
                            "AddDevAc:onResume",
                            "Permission is not granted. Execution permission request to user."
                        )
                    }
                    this.requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                    false
                } else {
                    // start the scan
                    this.bleDiscoveryManager.startScan()
                    true
                }
            }
        } else {
            // TODO!
        }


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
        private val deviceListItemClickListener: OnAddDeviceActivityListItemClickListener,
        private val type: Int,
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
                deviceListItemClickListener.onItemClicked(position, type)
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

    override fun onItemClicked(index: Int, type: Int) {
        when (type) {
            LIST_TYPE_BONDED_LIST -> {
                (applicationContext as ApplicationProperty).addedDevices.add(
                    bondedDevices.elementAt(index).address,
                    bondedDevices.elementAt(index).name
                )
                (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = true
            }
            LIST_TYPE_SCAN_LIST -> {
                (applicationContext as ApplicationProperty).addedDevices.add(
                    scanResultListElements.elementAt(index).address,
                    scanResultListElements.elementAt(index).name
                )
                (applicationContext as ApplicationProperty).mainActivityListElementWasAdded = true
            }
            else -> {
                return
            }
        }
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
            if(!device.name.isNullOrEmpty() && !device.address.isNullOrEmpty()) {
                var isNew = true

                this.scanResultListElements.forEach {
                    if(it.address == device.address){
                        isNew = false
                        return@forEach
                    }
                }

                if(isNew) {
                    val element = LaRoomyDevicePresentationModel()
                    element.address = device.address
                    element.name = device.name
                    this.scanResultListElements.add(element)
                    this.scanResultListAdapter.notifyItemInserted(this.scanResultListElements.size - 1)
                }
            }
        }
    }

    override fun permissionError() {

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