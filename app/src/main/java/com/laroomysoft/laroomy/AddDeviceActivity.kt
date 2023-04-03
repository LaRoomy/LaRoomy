package com.laroomysoft.laroomy

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
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

    private val requestAndroid12orHigherBluetoothScanPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if(verboseLog){
                Log.d("AddDeviceActivity", "Android 12 or higher: BluetoothScan Permission-Request result is: $isGranted")
            }
            this.bluetoothPermissionGranted = isGranted
            this.grantPermissionRequestDeclined = !isGranted    // prevent a request loop in onResume
            // if the permission dialog is dismissed, the onResume method is invoked again, and bluetooth enabled state is checked
            // when the permission request is reclined, the UI must go in missing-permission-state, and prevent the invocation
            // of the permission request launcher in onResume, otherwise there will be an infinite loop between onResume and permission launcher
        }

    private val requestAndroid11orLowerLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if(verboseLog){
                Log.d("AddDeviceActivity", "Android 11 or lower: LocationPermissionLauncherResult: Location Permission-Request result is: $isGranted")
            }
            this.bluetoothPermissionGranted = isGranted
            this.bluetoothLocationPermissionGranted = isGranted
            this.grantPermissionRequestDeclined = !isGranted    // prevent a request loop in onResume
            // if the permission dialog is dismissed, the onResume method is invoked again, and bluetooth enabled state is checked
            // when the permission request is reclined, the UI must go in missing-permission-state, and prevent the invocation
            // of the permission request launcher in onResume, otherwise there will be an infinite loop between onResume and permission launcher
            this.permissionRationalePending = false
        }

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

    private lateinit var scanResultListView: RecyclerView
    private lateinit var scanResultListViewManager: RecyclerView.LayoutManager
    private lateinit var scanResultListAdapter: RecyclerView.Adapter<*>

    private lateinit var scanWorkingIndicator: SpinKitView
    private lateinit var reloadButton: AppCompatImageButton

    private lateinit var bleDiscoveryManager: BLEDiscoveryManager
    
    private var listUpdateRequired = false
    private var bluetoothPermissionGranted = false
    private var bluetoothLocationPermissionGranted = false
    private var grantPermissionRequestDeclined = false
    
    private var permissionRationalePending = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)
        
        // register on-back-pressed event
        this.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackEvent()
            }
        })
        
        // get UI Elements
        this.bottomSeparator = findViewById(R.id.addDeviceActivityBottomSeparator)
        this.backButton = findViewById(R.id.addDeviceActivityBackButton)

        // add back button functionality
        this.backButton.setOnClickListener {
            handleBackEvent()
        }

        // get the spin-kit
        this.scanWorkingIndicator = findViewById(R.id.addDeviceActivityScanIndicationSpinKitView)
        
        // get the reload-button and add onClick handler
        this.reloadButton = findViewById(R.id.addDeviceActivityRescanButton)
        this.reloadButton.apply {
            setOnClickListener {
                rescan()
            }
        }

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
            if(verboseLog){
                Log.d("AddDeviceActivity", "onResume: This is Android 12 or higher - check for BLUETOOTH_SCAN permission")
            }
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
                    this.requestAndroid12orHigherBluetoothScanPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                    false
                } else {
                    // start the scan
                    try {
                        this.bleDiscoveryManager.startScan()
                        true
                    } catch (e: java.lang.Exception){
                        if(verboseLog){
                            Log.e("AddDeviceActivity", "AddDeviceActivity::onResume::startScan: severe error: $e")
                        }
                        false
                    }
                }
            }
        } else {
            if (verboseLog) {
                Log.d(
                    "AddDeviceActivity",
                    "onResume: This is Android 11 or lower - check for ACCESS_FINE_LOCATION permission"
                )
            }
            if (!this.grantPermissionRequestDeclined) {
                if (!permissionRationalePending) {
                    this.bluetoothLocationPermissionGranted = when {
                        ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            if (verboseLog) {
                                Log.d(
                                    "AddDeviceActivity",
                                    "onResume: Location permission granted > start discovery scan."
                                )
                            }
                            this.setUIToMissingPermissionState(false)
                            this.bleDiscoveryManager.startScan()
                            true
                        }
                        shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                            if (verboseLog) {
                                Log.d(
                                    "AddDeviceActivity",
                                    "onResume: Location permission NOT granted > show request permission rationale."
                                )
                            }
                            permissionRationalePending = true
                            showPermissionRationaleDialog()
                            false
                        }
                        else -> {
                            if((applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_A11orLowerLocationPermissionRequestFirstRequest, true)){
                                (applicationContext as ApplicationProperty).saveBooleanData(false, R.string.FileKey_AppSettings, R.string.DataKey_A11orLowerLocationPermissionRequestFirstRequest)
                                showPermissionRationaleDialog()
                            } else {
                                this.requestAndroid11orLowerLocationPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            }
                            false
                        }
                    }
                }
            } else {
                this.setUIToMissingPermissionState(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        this.listUpdateRequired = true
        this.bleDiscoveryManager.stopScan()
    }
    
    private fun handleBackEvent(){
        this.bleDiscoveryManager.stopScan()
        finish()
    }
    
    private fun rescan(){
        if(!this.bleDiscoveryManager.isScanning) {
            //  refresh the bondedList + clear the scanResultList
            this.bondedDevicesListView.adapter = AddDevicesListAdapter(
                this.bondedDevices,
                this,
                LIST_TYPE_BONDED_LIST,
                applicationContext
            )
            // delete all scan results
            this.scanResultListElements.clear()
            // start scan
            this.bleDiscoveryManager.startScan()
        } else {
            if(verboseLog){
                Log.w("AddDeviceActivity", "UNEXPECTED! Rescan not possible, because the discovery manager reported: already started!")
            }
        }
    }
    
    private fun showPermissionRationaleDialog(){
        runOnUiThread {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage(R.string.AddDeviceActivityPermissionRationaleText)
            dialog.setTitle(R.string.AddDeviceActivityPermissionRationaleTitleText)
            dialog.setPositiveButton(R.string.GeneralString_OK) { dialogInterface: DialogInterface, _: Int ->
                if(verboseLog){
                    Log.d("AddDeviceActivity", "locationPermissionRationaleDialog: User confirmed the location request, so show it.")
                }
                this.requestAndroid11orLowerLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                dialogInterface.dismiss()
            }
            dialog.setNegativeButton(R.string.AddDeviceActivityDeclineButton) { dialogInterface: DialogInterface, _: Int ->
                if(verboseLog){
                    Log.d("AddDeviceActivity", "locationPermissionRationaleDialog: User declined the location usage.")
                }
                // reset params
                this.permissionRationalePending = false
                // prevent all further requests
                this.grantPermissionRequestDeclined = true
                // set UI to missing-permission-state
                this.setUIToMissingPermissionState(true)
                dialogInterface.dismiss()
            }
            dialog.create()
            dialog.show()
        }
    }
    
    private fun setUIToMissingPermissionState(permissionMissing: Boolean){
        runOnUiThread {
            when (permissionMissing) {
                true -> {
                    findViewById<RecyclerView>(R.id.addDeviceActivityScanResultRecyclerView).apply {
                        visibility = View.GONE
                    }
                    findViewById<ConstraintLayout>(R.id.addDeviceActivityMissingPermissionInfoContainer).apply {
                        visibility = View.VISIBLE
                    }
                }
                else -> {
                    findViewById<RecyclerView>(R.id.addDeviceActivityScanResultRecyclerView).apply {
                        visibility = View.VISIBLE
                    }
                    findViewById<ConstraintLayout>(R.id.addDeviceActivityMissingPermissionInfoContainer).apply {
                        visibility = View.GONE
                    }
                }
            }
        }
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

    override fun onScanStarted() {
        this.scanWorkingIndicator.visibility = View.VISIBLE
        this.reloadButton.visibility = View.GONE
    }

    override fun onScanStopped() {
        runOnUiThread {
            this.scanWorkingIndicator.visibility = View.INVISIBLE
            this.reloadButton.visibility = View.VISIBLE
        }
    }

    override fun onScanFail(errorCode: Int) {
        super.onScanFail(errorCode)
        runOnUiThread {
            this.scanWorkingIndicator.visibility = View.INVISIBLE
            this.reloadButton.visibility = View.VISIBLE
        }
    }

    override fun onDeviceFound(device: BluetoothDevice) {
        if(verboseLog){
            Log.d("AddDeviceActivity", "onDeviceFound: DEVICE: Name<${device.name}> Mac<${device.address}>")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                this.onPermissionError()
            } else {
                if (!device.name.isNullOrEmpty() && !device.address.isNullOrEmpty()) {
                    var isNew = true

                    this.scanResultListElements.forEach {
                        if (it.address == device.address) {
                            isNew = false
                            return@forEach
                        }
                    }

                    if (isNew) {
                        val element = LaRoomyDevicePresentationModel()
                        element.address = device.address
                        element.name = device.name
                        this.scanResultListElements.add(element)
                        this.scanResultListAdapter.notifyItemInserted(this.scanResultListElements.size - 1)
                    }
                }
            }
        } else {
            if (!device.name.isNullOrEmpty() && !device.address.isNullOrEmpty()) {
                var isNew = true

                this.scanResultListElements.forEach {
                    if (it.address == device.address) {
                        isNew = false
                        return@forEach
                    }
                }

                if (isNew) {
                    val element = LaRoomyDevicePresentationModel()
                    element.address = device.address
                    element.name = device.name
                    this.scanResultListElements.add(element)
                    this.scanResultListAdapter.notifyItemInserted(this.scanResultListElements.size - 1)
                }
            }
        }
    }

    override fun onPermissionError() {
        finish()
    }
}