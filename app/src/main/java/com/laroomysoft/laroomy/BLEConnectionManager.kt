package com.laroomysoft.laroomy

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context.BLUETOOTH_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.Serializable
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


const val BINDING_RESPONSE_BINDING_NOT_SUPPORTED = 1
const val BINDING_RESPONSE_BINDING_SUCCESS = 2
const val BINDING_RESPONSE_BINDING_ERROR = 3
const val BINDING_RESPONSE_RELEASE_BINDING_FAILED_WRONG_PASSKEY = 4
const val BINDING_RESPONSE_RELEASE_BINDING_FAILED_UNKNOWN_ERROR = 5
const val BINDING_RESPONSE_RELEASE_BINDING_SUCCESS = 6

const val BLE_IS_DISABLED = 0
const val BLE_IS_ENABLED = 1

const val BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE = 1
const val BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE = 2
const val BLE_UNEXPECTED_BLUETOOTH_DEVICE_NOT_CONNECTED = 3
const val BLE_UNEXPECTED_CRITICAL_BINDING_KEY_MISSING = 4
const val BLE_CONNECTION_MANAGER_CRITICAL_DEVICE_NOT_RESPONDING = 5
const val BLE_INIT_NO_PROPERTIES = 6
const val BLE_BLUETOOTH_PERMISSION_MISSING = 7
const val BLE_NO_MATCHING_SERVICES = 8
const val BLE_NO_MATCHING_CHARACTERISTICS = 9

const val BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED = 101

class BLEConnectionManager(private val applicationProperty: ApplicationProperty) {

    private val clientCharacteristicConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // static notifications
    private val propertyLoadingCompleteNotification = "5000030010\r"         // sent when the retrieving of the properties is complete (raw loading - not from cache)
    private val propertyLoadedFromCacheCompleteNotification = "5000030011\r" // sent when the properties and groups are loaded from cache and the operation is complete
    private val deviceReconnectedNotificationEntry = "500002007"           // sent when the connection was suspended (user has left the app) and the user re-invoked the app (the current opened property ID must be appended to this notification)
    private val userNavigatedBackToDeviceMainNotification = "500002004\r"    // sent when the user has opened a complex property page and navigated back to device-main-page
    private val factoryResetCommand = "500002006\r"                          // sent when the user executes the factory reset on the device-settings page

    // data holder objects
    private val bleDeviceData = BLEDeviceData()
    private val timeoutWatcherData = LoopTimeoutWatcherData()
    private val discoveryWatcherData = ServCharDiscoveryWatcherData()

    // timer
    private lateinit var timeoutTimer: Timer
    private lateinit var rssiReadTimer: Timer

    var isConnected:Boolean = false
        private set

    val initializationSuccess : Boolean
        get() = this.bleDeviceData.authenticationSuccess

    val isBindingRequired
    get() = this.bleDeviceData.isBindingRequired
    
    val isStandAlonePropertyMode
    get() = this.bleDeviceData.isStandAlonePropertyMode

    private var connectionSuspended = false

    private var propertyLoopActive = false
    private var groupLoopActive = false
    private var isResumeConnectionAttempt = false
    private var invokeCallbackLoopAfterUIDataGeneration = false
    private var reloadInitRequestPending = false
    private var reloadAttemptCounter = 0

    private var suspendedDeviceAddress = ""
    private var echoPreventerDataHolder = ""

    // parameter regarding the simple state loop
    private var currentSimpleStateRetrievingIndex = -1 // initialize with invalid marker
    private var simpleStateLoopActive = false
    private var simpleStatePropertyIndexes = ArrayList<Int>()

    // parameter regarding the complex state loop
    private var currentComplexStateRetrievingIndex = -1 // initialize with invalid marker
    private var complexStateLoopActive = false
    private var complexStatePropertyIndexes = ArrayList<Int>()

    // parameter regarding the fragmented transmission?
    private val openFragmentedTransmissionData = ArrayList<FragmentTransmissionData>()


    // Callbacks
    private lateinit var callback: BleEventCallback
    private lateinit var propertyCallback: PropertyCallback

    // bluetooth system objects
    var currentDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var rxGattCharacteristic: BluetoothGattCharacteristic
    private lateinit var txGattCharacteristic: BluetoothGattCharacteristic
    // --
//    val bondedList: Set<BluetoothDevice>? by lazy(LazyThreadSafetyMode.NONE){
//        this.bleAdapter?.bondedDevices
//    }
    // --
    private val bleAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE){
        val bluetoothManager =
            applicationProperty.applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // device-property objects
    private var laRoomyDevicePropertyList = ArrayList<LaRoomyDeviceProperty>()
    private var laRoomyPropertyGroupList = ArrayList<LaRoomyDevicePropertyGroup>()
    var uIAdapterList = ArrayList<DevicePropertyListContentInformation>()

    var currentUsedServiceUUID = ""
        // used to cache the current service uuid
        private set

    val currentUsedRXCharacteristicUUID: String
        get() = this.rxGattCharacteristic.uuid.toString()
    
    val currentUsedTXCharacteristicUUID: String
    get() {
        return if(this.txGattCharacteristic.uuid.toString() == this.rxGattCharacteristic.uuid.toString()){
            ""
        } else {
            this.txGattCharacteristic.uuid.toString()
        }
    }

    val isLastAddressValid: Boolean
        get() = (this.getLastConnectedDeviceAddress().isNotEmpty())

    val isConnectionDoneWithSharedKey: Boolean
    get() = (this.bleDeviceData.passKeyTypeUsed == PASSKEY_TYPE_SHARED)


    var bondedLaRoomyDevices = ArrayList<LaRoomyDevicePresentationModel>()
        get() {
            // clear the list:
            field.clear()

            // add the data
            try {
                this.bleAdapter?.bondedDevices?.forEach {
                    val device = LaRoomyDevicePresentationModel()
                    device.name = it.name
                    device.address = it.address
                    device.image = 0
                    field.add(device)
                }
            } catch (e: SecurityException){
                Log.e("GetBondedDevices", "Security Exception: $e")
                applicationProperty.logControl("E: Security Exception: $e")
            }
            return field
        }

/*
    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val requestEnableBT: Int = 13
*/

    private var uIItemAddCounter = -1

    /*
     TODO: the following double implementation of the gatt callback is necessary, because on systems
           with api level 33 or higher the callback method 'onCharacteristicChanged(gatt, characteristic)
           is deprecated. So the replacement 'onCharacteristicChanged(gatt, characteristic, value)' should
           be used. But on systems running api level lower than 33 the new method is not called. To guarantee
           functionality on all system both are implemented.
           In the future the min-sdk version of the app could be changed to api level 33 and the old
           implementation can be removed!
    */
    // NEW callback implementation for BluetoothGatt for API LEVEL 33+
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            processOnConnectionStateChange(gatt, newState)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            processOnServicesDiscovered(gatt, status)
        }
    
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            val dataAsString: String = value.decodeToString()
            processOnCharacteristicChanged(dataAsString)
        }
    
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLEConnectionManager", "M:GattCallback:onReadRemoteRssi: Value read: $rssi")
                callback.onRssiValueRead(rssi)
            } else {
                Log.e("BLEConnectionManager", "M:GattCallback:onReadRemoteRssi: reading rssi value failed!")
            }
        }
    }
    
    // callback implementation for BluetoothGatt for older versions (API LEVEL LOW THAN 33)
    private val oldGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            processOnConnectionStateChange(gatt, newState)
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            processOnServicesDiscovered(gatt, status)
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            @Suppress("DEPRECATION")
            super.onCharacteristicChanged(gatt, characteristic)
            
            @Suppress("DEPRECATION") val dataAsString = characteristic?.getStringValue(0)
            processOnCharacteristicChanged(dataAsString ?: "")
        }
    
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLEConnectionManager", "M:GattCallback:onReadRemoteRssi: Value read: $rssi")
                callback.onRssiValueRead(rssi)
            } else {
                Log.e("BLEConnectionManager", "M:GattCallback:onReadRemoteRssi: reading rssi value failed!")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun processOnConnectionStateChange(gatt: BluetoothGatt?, newState: Int){
        // NOTE: Suppressing the missing permission warning here is absolutely ok, because the warning is a paradox:
        //       when the permission is not granted, the bluetooth connection cannot be established - Thus the
        //       callback cannot be invoked.
        when(newState){
            BluetoothProfile.STATE_CONNECTED -> {
            
                // if successful connected reset the device-data??
                //bleDeviceData.clear()
            
                isConnected = true
            
                if(verboseLog) {
                    Log.d("M:CB:ConStateChanged", "Connection-state changed to: CONNECTED")
                }
                callback.onConnectionStateChanged(true)
                
                startRssiReadTimer()
            
                if(!isResumeConnectionAttempt) {
                    if(verboseLog) {
                        Log.d(
                            "M:CB:ConStateChanged",
                            "This is no resume action, so discover services"
                        )
                        Log.d("M:CB:ConStateChanged", "Invoking discoverServices()")
                    }
                    applicationProperty.logControl("I: Starting to discover Services")
                
                    // set indication param
                    discoveryWatcherData.isServiceDiscovery = true
                
                    // start to discover the services of the device
                    gatt?.discoverServices()
                } else {
                    isResumeConnectionAttempt = false
                    suspendedDeviceAddress = ""
                
                    if(verboseLog) {
                        Log.d(
                            "M:CB:ConStateChanged",
                            "This is a resume action -> DO NOT DISCOVER SERVICES!"
                        )
                    }
                    applicationProperty.logControl("I: Connection restored")
                
                    // re-init the descriptor to establish a stream!
                
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val descriptor =
                            rxGattCharacteristic.getDescriptor(clientCharacteristicConfig)
                        gatt?.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )
                    } else {
                    
                        // deprecated:
                        val descriptor = rxGattCharacteristic.getDescriptor(clientCharacteristicConfig)
                            .apply {
                                @Suppress("DEPRECATION")
                                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            }
                        @Suppress("DEPRECATION")
                        gatt?.writeDescriptor(descriptor)
                    }
                
                    // notify the remote device
                    Executors.newSingleThreadScheduledExecutor().schedule({
                        sendDeviceReconnectedNotification()
                    }, 800, TimeUnit.MILLISECONDS)
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
            
                isConnected = false
                
                stopRssiReadTimer()
            
                if(verboseLog) {
                    Log.d("M:CB:ConStateChanged", "Connection-state changed to: DISCONNECTED")
                }
                callback.onConnectionStateChanged(false)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun processOnServicesDiscovered(gatt: BluetoothGatt?, status: Int){
        // NOTE: Suppressing the missing permission warning here is absolutely ok, because the warning is a paradox:
        //       when the permission is not granted, the bluetooth connection cannot be established - Thus the
        //       callback cannot be invoked.
        try {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    // iterate throughout the services and display it in the debug-log
                    gatt?.services?.forEachIndexed { index, bluetoothGattService ->
                    
                        if (verboseLog) {
                            Log.d(
                                "M:CB:onServicesDisc",
                                "Service discovered. Index: $index ServiceUUID: ${bluetoothGattService.uuid} Type: ${bluetoothGattService.type}"
                            )
                        }
                        applicationProperty.logControl("I: Service discovered. UUID: ${bluetoothGattService.uuid}")
                    
                        // look if the service matches an uuid profile
                        val profileIndex =
                            applicationProperty.uuidManager.profileIndexFromServiceUUID(
                                bluetoothGattService.uuid
                            )
                    
                        if (profileIndex != -1) {
                            // log
                            if (verboseLog) {
                                Log.d(
                                    "M:CB:onServicesDisc",
                                    "Correct service found - retrieving characteristics for the service"
                                )
                            }
                            applicationProperty.logControl("I: This service is found in UUID Profile")
                            applicationProperty.logControl("I: Perform lookup for Characteristic UUID")
                        
                            // set/reset indication params
                            discoveryWatcherData.serviceDiscovered = true
                            discoveryWatcherData.isServiceDiscovery = false
                        
                            // cache the service uuid
                            currentUsedServiceUUID = bluetoothGattService.uuid.toString()
                        
                            // local param
                            var firstCharacteristicAdded = false
                        
                            // setup an indicator if characteristics were found
                            discoveryWatcherData.isCharDiscovery = true
                        
                            // iterate through the characteristics in the service
                            bluetoothGattService.characteristics.forEach {
                            
                                if (verboseLog) {
                                    Log.d(
                                        "M:CB:onServicesDisc",
                                        "Characteristic found: UUID: ${it.uuid}  InstanceID: ${it.instanceId}"
                                    )
                                }
                                applicationProperty.logControl("I: Characteristic in Service found. UUID: ${it.uuid}")
                            
                                // set the characteristic notification for the desired characteristic in the service
                                // and set tx if permitted
                                when(it.uuid) {
                                
                                    applicationProperty.uuidManager.uUIDProfileList.elementAt(profileIndex).rxCharacteristicUUID -> {
                                    
                                        if (verboseLog) {
                                            Log.d(
                                                "M:CB:onServicesDisc",
                                                "Correct characteristic found (rx) - enable notifications"
                                            )
                                        }
                                        applicationProperty.logControl("I: RX Characteristic match: enable notifications")
                                    
                                        // save characteristic
                                        rxGattCharacteristic = it
                                    
                                        // if the usage of only one characteristic is desired, copy the reference (so that both characteristics equals each other)
                                        if(applicationProperty.uuidManager.uUIDProfileList.elementAt(profileIndex).useSingleCharacteristic) {
                                            txGattCharacteristic = rxGattCharacteristic
                                        
                                            // make sure the callback is triggered in any case
                                            firstCharacteristicAdded = true
                                        }
                                    
                                        // enable notification on the device
                                        gatt.setCharacteristicNotification(
                                            rxGattCharacteristic,
                                            true
                                        )
                                    
                                        if (verboseLog) {
                                            Log.d("M:CB:onServicesDisc", "Set Descriptor")
                                        }
                                    
                                        // set the correct descriptor for this characteristic
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val descriptor = rxGattCharacteristic.getDescriptor(
                                                clientCharacteristicConfig
                                            )
                                            gatt.writeDescriptor(
                                                descriptor,
                                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                            )
                                        } else {
                                            // deprecated since API-Level 33
                                            val descriptor = rxGattCharacteristic.getDescriptor(clientCharacteristicConfig).apply {
                                                @Suppress("DEPRECATION")
                                                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                            }
                                            @Suppress("DEPRECATION")
                                            gatt.writeDescriptor(descriptor)
                                        }
                                    
                                        if(firstCharacteristicAdded) {
                                            // report ready for communication
                                            callback.onDeviceReadyForCommunication()
                                            discoveryWatcherData.characteristicDiscovered = true
                                            return@forEach
                                        } else {
                                            // flag it
                                            firstCharacteristicAdded = true
                                        }
                                    }
                                    applicationProperty.uuidManager.uUIDProfileList.elementAt(profileIndex).txCharacteristicUUID -> {
                                    
                                        // only save the tx characteristic if the usage is permitted
                                        if(!applicationProperty.uuidManager.uUIDProfileList.elementAt(profileIndex).useSingleCharacteristic) {
                                        
                                            if (verboseLog) {
                                                Log.d(
                                                    "M:CB:onServicesDisc",
                                                    "Correct characteristic found (tx)"
                                                )
                                            }
                                            applicationProperty.logControl("I: TX Characteristic match!")
                                        
                                            // save characteristic
                                            txGattCharacteristic = it
                                        
                                            if(firstCharacteristicAdded){
                                                // report ready to communicate
                                                callback.onDeviceReadyForCommunication()
                                                discoveryWatcherData.characteristicDiscovered = true
                                                return@forEach
                                            } else {
                                                // flag it
                                                firstCharacteristicAdded = true
                                            }
                                        }
                                    }
                                }
                            }
                            if(discoveryWatcherData.isCharDiscovery && !discoveryWatcherData.characteristicDiscovered){
                                // no matching characteristic(s) were found, report error
                                callback.onConnectionError(BLE_NO_MATCHING_CHARACTERISTICS)
                            }
                        }
                    }
                    if(discoveryWatcherData.isServiceDiscovery && !discoveryWatcherData.serviceDiscovered){
                        // no matching service was found, report error
                        callback.onConnectionError(BLE_NO_MATCHING_SERVICES)
                    }
                }
                else -> {
                    Log.e("M:CB:onServicesDisc", "Gatt-Status: $status")
                    applicationProperty.logControl("E: Unexpected Gatt-Status: $status")
                }
            }
        } catch(e: Exception){
            Log.e("M:CB:onServicesDisc", "Exception occurred while discovering services: $e")
            applicationProperty.logControl("E: $e")
        }
    }
    
    private fun processOnCharacteristicChanged(data: String){
        // check if this was an unexpected echo notification
        if (data == echoPreventerDataHolder) {
            // clear holder at first !
            echoPreventerDataHolder = ""
            // this was an echo
            if(verboseLog) {
                Log.e("M:CB:onCharacteristicChanged", "This was an echo. Why does this happen?!")
            }
        } else {
            // clear holder at first !
            echoPreventerDataHolder = ""
            // log (string)
            if (verboseLog) {
                Log.d("M:CB:CharChanged", "Characteristic changed. String-Value: $data")
            }
            applicationProperty.logControl("I: Characteristic changed: $data")
        
            try {
                if (!dispatchTransmission(data)) {
                    callback.onDataReceived(data)
                }
            } catch (e: Exception) {
                if (verboseLog) {
                    Log.e(
                        "onCharacteristicChanged",
                        "Exception while dispatching the incoming transmission. Exception: $e Transmission-Data: $data"
                    )
                }
                applicationProperty.logControl("E: Exception while dispatching the incoming transmission. Exception: $e / Message: ${e.message}")
                
                // clean broken transmission data
                cleanUpOnDispatchError()
            }
        }
    
    }

    private fun dispatchTransmission(data: String): Boolean {
        // validate transmission length
        if(data.length < 8){
            //invalid transmission length
            if(verboseLog){
                Log.e("M:CB:Dispatcher", "Invalid transmission length. Transmission-length was: ${data.length} > minimum header-length is 8!")
            }
            applicationProperty.logControl("E: Invalid transmission length. Transmission-length was: ${data.length} > minimum header-length is 8!")
            return false
        } else {
            // first read data size
            val payLoadDataSize =
                a2CharHexValueToIntValue(data[4], data[5])

            // check error flag
            if(data[6] != '0'){
                this.onErrorFlag(data[6])
                return false
            }

            var dataProcessed = false

            // evaluate transmission type
            when(data[0]){
                '1' -> {
                    val id =
                        a2CharHexValueToIntValue(data[2], data[3])
                    // property definition data
                    when (data[1]) {
                        '6' -> {
                            // is remove transmission, so remove
                            dataProcessed = true
                            this.removePropertyElement(id)
                        }
                        '7' -> {
                            // is enable transmission, so enable
                            dataProcessed = true
                            this.enablePropertyElement(id, true)
                        }
                        '8' -> {
                            // is disable transmission, so disable
                            dataProcessed = true
                            this.enablePropertyElement(id, false)
                        }
                        else -> {
                            // must be response, update or insert transmission
                            dataProcessed = readPropertyString(data, payLoadDataSize)
                        }
                    }
                }
                '2' -> {
                    // group definition data
                    if(data[1] == '6'){
                        // is remove transmission, so remove
                        dataProcessed = true
                        this.removeGroupElement(
                            a2CharHexValueToIntValue(data[2], data[3]),
                            true
                        )
                    } else {
                        // must be a response, update or insert transmission
                        dataProcessed = readGroupString(data, payLoadDataSize)
                    }
                }
                '3' -> {
                    // property state data
                    dataProcessed = dispatchPropertyStateTransmission(data, payLoadDataSize)
                }
                '4' -> {
                    // property execution command (only response)
                    dataProcessed = readPropertyExecutionResponse(data, payLoadDataSize)
                }
                '5' -> {
                    // device to app notification
                    dataProcessed = readDeviceNotification(data, payLoadDataSize)
                }
                '6' -> {
                    // binding response
                    dataProcessed = readBindingResponse(data, payLoadDataSize)
                }
                '7' -> {
                    // init transmission string
                    dataProcessed = readInitTransmission(data, payLoadDataSize)
                }
                '8' -> {
                    // fast data setter
                    dataProcessed = readFastDataSetterTransmission(data, payLoadDataSize)
                }
                '9' -> {
                    // fragmented fast data setter
                    dataProcessed = readFragmentedTransmission(data)
                }
                'a' -> {
                    // fragmented property definition data
                    dataProcessed = readFragmentedTransmission(data)
                }
                'b' -> {
                    // fragmented group definition data
                    dataProcessed = readFragmentedTransmission(data)
                }
                'c' -> {
                    // fragmented state data
                    dataProcessed = readFragmentedTransmission(data)
                }
                'd' -> {
                    // fragmented execution command
                    dataProcessed = readFragmentedTransmission(data)
                }
                'e' -> {
                    // fragmented notification/command
                    dataProcessed = readFragmentedTransmission(data)
                }
                'f' -> {
                    // fragmented binding transmission
                    dataProcessed = readFragmentedTransmission(data)
                }
                else -> {
                    if(verboseLog){
                        Log.d("M:CB:Dispatcher", "Invalid Transmission Identification Character: ${data[0]} in data-string: $data")
                    }
                    applicationProperty.logControl("E: Invalid Transmission Identification Character: ${data[0]} in data-string: $data")
                }
            }
            // return true if the data is fully handled, otherwise it will be forwarded to the callback
            return dataProcessed
        }
    }

    private fun readFragmentedTransmission(data: String) : Boolean {

        notifyLoopIsWorking(this.timeoutWatcherData.currentIndex)

        if(verboseLog){
            Log.d("readFragmentTransMsn", "Transmission fragment received. Data was: $data")
        }
        applicationProperty.logControl("I: Transmission fragment received. Data was: $data")

        val header = data.removeRange(8, data.length)
        var handled = false
        var indexToRemove = -1

        // first check if there are preliminary fragments
        this.openFragmentedTransmissionData.forEachIndexed { index, fragmentTransmissionData ->

            if(fragmentTransmissionData.transmissionString.startsWith(header)){
                // extract user-data
                var tData = data.removeRange(0, 8)
                tData = tData.removeSuffix("\r")

                // add the user-data
                fragmentTransmissionData.transmissionString += tData

                // check if the fragmentation is complete
                val dataSize =
                    a2CharHexValueToIntValue(fragmentTransmissionData.transmissionString[4], fragmentTransmissionData.transmissionString[5])

                if(verboseLog){
                    Log.d("readFragmentTransMsn", "New fragment added. Data is now: ${fragmentTransmissionData.transmissionString}")
                    Log.d("readFragmentTransMsn", "New fragment added. DataSize is: ${dataSize - 1} | User-data length is: ${fragmentTransmissionData.transmissionString.length - 8}")
                }
                applicationProperty.logControl("I: New fragment added. Data is now: ${fragmentTransmissionData.transmissionString}")

                // NOTE: the carriage-return delimiter is missing in the fragment assembly, but is calculated in the transmission size, so the dataSize must be decreased by 1 for comparison
                if ((fragmentTransmissionData.transmissionString.length - 8) >= (dataSize - 1)) {
                    // data size reached -> must be complete
                    fragmentTransmissionData.transmissionString += '\r'
                    dispatchFragmentedTransmission(fragmentTransmissionData.transmissionString)
                    indexToRemove = index
                }
                handled = true
            }
        }

        if(indexToRemove != -1){
            if(verboseLog){
                Log.d("readFragmentTransMsn", "Fragment processed. Removing open fragmented transmission with index: $indexToRemove")
            }
            this.openFragmentedTransmissionData.removeAt(indexToRemove)
        }

        if(!handled){
            val stringToAdd = data.removeSuffix("\r")

            if(verboseLog){
                Log.d("readFragmentTransMsn", "Transmission fragment not added -> must be a new start-fragment. Adding data to list: $stringToAdd")
            }
            applicationProperty.logControl("I: Transmission fragment not added -> must be a new start-fragment. Adding data to list: $stringToAdd")

            this.openFragmentedTransmissionData.add(
                FragmentTransmissionData(stringToAdd)
            )
        }

        // TODO: error handling??

        return true
    }

    private fun dispatchFragmentedTransmission(transmission: String){

        if(verboseLog){
            Log.d("dispatchFragmentTransMsn", "Dispatching the assembled fragmented transmission. Transmission data: $transmission")
        }

        when(transmission[0]){
            'a' -> {
                this.readPropertyString(transmission, transmission.length - 8)
            }
            'b' -> {
                this.readGroupString(transmission, transmission.length - 8)
            }
            'c' -> {
                this.dispatchPropertyStateTransmission(transmission, transmission.length - 8)
            }
            'd' -> {
                this.readPropertyExecutionResponse(transmission, transmission.length - 8)
            }
            'e' -> {
                this.readDeviceNotification(transmission, transmission.length - 8)
            }
            'f' -> {
                this.readBindingResponse(transmission, transmission.length - 8)
            }
            '9' -> {
                this.readFastDataSetterTransmission(transmission, transmission.length - 8)
            }
            else -> {
                Log.e("dispatchFragmentedTransMsn", "Error: Unknown header format character. Character was: ${transmission[0]}")
                applicationProperty.logControl("E: Unknown header format character. Character was: ${transmission[0]}")
            }
        }
    }

    private fun readDeviceNotification(data: String, payLoadDataSize: Int) : Boolean {

        if((payLoadDataSize < 2)||(data.length < 10)){
            //invalid transmission length
            if(verboseLog){
                Log.e("readDevNotification", "Invalid transmission length. PayLoadData-size was: $payLoadDataSize > minimum is 2!\n" +
                        "String-length was ${data.length}")
            }
            applicationProperty.logControl("E: Invalid transmission length of device-notification transmission. Transmission-length was: $payLoadDataSize > minimum transmission-length is 2! String-length was ${data.length}")
            return false
        } else {
            // get ID

//            var hexString = "0x"
//            hexString += data[2]
//            hexString += data[3]
//            val elementIndex =
//                Integer.decode(hexString)

            when(data[8]){
                '1' -> {
                    // user-message notification
                    this.handleUserMessage(data, payLoadDataSize)
                }
                '2' -> {
                    // time request notification from device
                    this.handleTimeRequest()
                }
                '3' -> {
                    // property invalidated notification
                    handlePropertyInvalidationRequest()
                }
                '4' -> {
                    // save properties to cache notification
                    this.savePropertyDataToCacheIfPermitted()
                }
                '5' -> {
                    // language request notification
                    this.handleLanguageRequest()
                }
                '6' -> {
                    // refresh all property states notification
                    this.updatePropertyStates()
                }
                '7' -> {
                    // handle the remote back navigation request
                    this.handleForcedBackNavigation()
                }
                else -> {
                    if(verboseLog){
                        Log.e("readDeviceNotification", "Unknown device notification type")
                    }
                    applicationProperty.logControl("E: Unknown device notification type")
                }
            }
            return true
        }
    }

    private fun handleUserMessage(data: String, payLoadDataSize: Int){

        // check transmission length
        if(payLoadDataSize > 1) {
            val deviceInfoHeaderData = DeviceInfoHeaderData()

            // get image id (transferred in the id fields)
            var hexString = "0x"
            hexString += data[2]
            hexString += data[3]

            deviceInfoHeaderData.imageID = Integer.decode(hexString)
            deviceInfoHeaderData.type = data[9]
            deviceInfoHeaderData.displayTime = data[10].toString().toLong()
            deviceInfoHeaderData.message = unescapeUTF8String(data.removeRange(0, 11))

            this.propertyCallback.onRemoteUserMessage(deviceInfoHeaderData)
        } else {
            // no notification data
            if(verboseLog){
                Log.w("handleUserMessage", "User-Message transmission payLoadData size invalid")
            }
            applicationProperty.logControl("W: User-Message transmission payLoadData size invalid")
        }
    }

    private fun handleTimeRequest(){
        // get the time and send the client command to the device
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)// 24 hour format!
        val min = calendar.get(Calendar.MINUTE)
        val sec = calendar.get(Calendar.SECOND)

        var timeRequestResponse = "520008002"
        timeRequestResponse += a8bitValueTo2CharHexValue(hour)
        timeRequestResponse += a8bitValueTo2CharHexValue(min)
        timeRequestResponse += a8bitValueTo2CharHexValue(sec)
        timeRequestResponse += '\r'
        this.sendData(timeRequestResponse)
    }
    
    private fun handlePropertyInvalidationRequest(){
        if(this.bleDeviceData.isStandAlonePropertyMode) {
            if (verboseLog) {
                Log.e("PropertyInvalidation", "Unexpected: Property was invalidated during a stand-alone-property session. Notification will be forwarded, but should have no effect.")
            }
            applicationProperty.logControl("W: Unexpected: Property was invalidated during a stand-alone-property session. Notification will be forwarded, but should have no effect. To navigate back in stand-alone mode: close the device.")
        }
        this.propertyCallback.onPropertyInvalidated()
    }

    private fun handleLanguageRequest(){
        var languageRequestResponse = "5200"
        languageRequestResponse += a8bitValueTo2CharHexValue(applicationProperty.systemLanguage.length + 2)
        languageRequestResponse += "005${applicationProperty.systemLanguage}\r"
        sendData(languageRequestResponse)
    }

    private fun readPropertyExecutionResponse(data: String, payLoadDataSize: Int) : Boolean {
        if(verboseLog){
            Log.d("BLEConnectionManager", "readPropertyExecutionResponse: exec:data: $data / exec:payLoadDataSize: $payLoadDataSize")
        }
        // FIXME: what to do here?
        // if a property was executed and no response is received, send a state-update to verify the user-interface??
        return true
    }

    private fun dispatchPropertyStateTransmission(data: String, dataSize: Int) : Boolean {

        // at first retrieve the property-index
        var propIndex = "0x"
        propIndex += data[2]
        propIndex += data[3]
        val pIndex = Integer.decode(propIndex)

        // get property type
        val pType =
            this.propertyTypeFromIndex(pIndex)

        return if(pType != -1){
            if(pType < COMPLEX_PROPERTY_START_INDEX){
                // must be simple state data
                this.readSimpleStateData(pIndex, data, dataSize)
            } else {
                // must be complex state data
                this.readComplexStateData(pIndex, pType, data)
            }
            true
        } else {
            false
        }
    }

    private fun readSimpleStateData(propertyIndex: Int, data: String, payLoadDataSize: Int){
        if((payLoadDataSize < 2)||(data.length < 10)){
            // invalid data size
            if(verboseLog){
                Log.e("readSimpleStateData", "Invalid payLoadData-size. Data-size was: $payLoadDataSize. Minimum is: 2\n" +
                        "String-length was: ${data.length}")
            }
            applicationProperty.logControl("E: Invalid simple-state payLoadData-size. Data-size was: $payLoadDataSize. Minimum is: 2 / String-length was: ${data.length}")
        } else {
            var uiChangeIndex = -1// the index for the callback, the callback needs the UI-Adapter-Index

            // read state
            var hexState = "0x"
            hexState += data[8]
            hexState += data[9]
            val newState = Integer.decode(hexState)

            // update internal property list
            this.laRoomyDevicePropertyList[propertyIndex].propertyState = newState

            // update UI-Adapter-list
            this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                if ((devicePropertyListContentInformation.internalElementIndex == propertyIndex)
                    &&(devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)) {
                        // make sure the element is a property element,
                            // -> otherwise the internal element index of a group could be used, and this is an invalid operation
                    devicePropertyListContentInformation.simplePropertyState = newState
                    devicePropertyListContentInformation.update(applicationProperty.cViewContext)
                    uiChangeIndex = index
                    return@forEachIndexed
                }
            }

            // log
            if(verboseLog){
                Log.d("readSimpleStateData", "Simple State Data read: PropertyIndex: $propertyIndex  newState: $newState")
            }
            applicationProperty.logControl("I: Simple State Data read: PropertyIndex: $propertyIndex  newState: $newState")

            // launch property changed event
            if(uiChangeIndex != -1) {
                this.propertyCallback.onSimplePropertyStateChanged(
                    uiChangeIndex,
                    newState
                )
            } else {
                Log.e("readSimpleStateData", "UI-Element was not found, PropertyIndex: $propertyIndex")
            }

            // check if the loop is active and send the next request if necessary
            if(this.simpleStateLoopActive){

                // check if this is a valid transmission or if the property index defers from the expected one
                if (this.simpleStatePropertyIndexes.elementAt(this.currentSimpleStateRetrievingIndex) == propertyIndex) {

                    // increase retrieving index
                    this.currentSimpleStateRetrievingIndex++

                    // check if the loop is finished
                    if (this.currentSimpleStateRetrievingIndex >= this.simpleStatePropertyIndexes.size) {
                        val finalCount = this.currentSimpleStateRetrievingIndex

                        // simple state data loop is finished
                        this.currentSimpleStateRetrievingIndex = -1
                        this.simpleStatePropertyIndexes.clear()
                        this.simpleStateLoopActive = false
                        this.stopLoopTimeoutWatcher()

                        if (verboseLog) {
                            Log.d(
                                "readSimpleStateData",
                                "Simple property state loop finished: Count is: $finalCount"
                            )
                        }
                        applicationProperty.logControl("I: Simple property state loop finished: Count is: $finalCount")

                        // start complex state loop
                        this.startComplexStateDataLoop()

                    } else {
                        // request next simple property state
                        if (verboseLog) {
                            Log.d(
                                "readSimpleStateData",
                                "Simple Property Loop active. Requesting next state"
                            )
                        }
                        this.sendPropertyStateRequest(
                            this.simpleStatePropertyIndexes.elementAt(this.currentSimpleStateRetrievingIndex)
                        )
                    }
                } else {
                    if(verboseLog){
                        Log.w("readSimpleStateData", "Inconsistent data: Simple property state loop is active, but the received index is not the requested one.")
                    }
                }
            }
        }
    }

    private fun readComplexStateData(propertyIndex: Int, propertyType: Int, data: String){
        // forward the data regarding to the type
        when(propertyType){
            COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR -> {
                this.processRGBSelectorData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR -> {
                this.processEXLevelSelectorData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR -> {
                this.processTimeSelectorData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR -> {
                this.processTimeFrameSelectorData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL -> {
                this.processUnlockControlData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR -> {
                this.processNavigatorData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPH -> {
                this.processBarGraphData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_LINEGRAPH -> {
                this.processLineGraphData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_STRING_INTERROGATOR -> {
                this.processStringInterrogatorData(propertyIndex, data)
            }
            COMPLEX_PROPERTY_TYPE_ID_TEXT_LIST_PRESENTER -> {
                this.processTextListPresenterData(propertyIndex, data)
            }
        }

        // check if the loop is active and send the next request if necessary
        if(this.complexStateLoopActive){

            // check if this is a valid transmission or if the property index defers from the expected one
            if(this.complexStatePropertyIndexes.elementAt(this.currentComplexStateRetrievingIndex) == propertyIndex) {

                // increase retrieving index
                this.currentComplexStateRetrievingIndex++

                // check if the loop is finished
                if (this.currentComplexStateRetrievingIndex >= this.complexStatePropertyIndexes.size) {
                    val finalCount = this.currentComplexStateRetrievingIndex

                    // the complex state data loop is finished
                    this.currentComplexStateRetrievingIndex = -1
                    this.complexStatePropertyIndexes.clear()
                    this.complexStateLoopActive = false
                    this.stopLoopTimeoutWatcher()

                    if (verboseLog) {
                        Log.d(
                            "readComplexStateData",
                            "Complex property state loop finished: Count is: $finalCount"
                        )
                    }
                    applicationProperty.logControl("I: Complex property state loop finished: Count is: $finalCount")
                    
                    // report to subscriber (if this is the single property mode)
                    if(this.bleDeviceData.isStandAlonePropertyMode) {
                        this.propertyCallback.onStandAlonePropertyModePreparationComplete()
                    }
                } else {
                    // request next complex property state
                    if (verboseLog) {
                        Log.d(
                            "readComplexStateData",
                            "Complex Property Loop active. Requesting next state"
                        )
                    }
                    this.sendComplexPropertyStateRequest(
                        this.complexStatePropertyIndexes.elementAt(this.currentComplexStateRetrievingIndex)
                    )
                }
            } else {
                if(verboseLog){
                    Log.w("readComplexStateData", "Complex property state loop is active, but the received index is not the requested one.")
                }
            }
        }
    }

    private fun readGroupString(data: String, dataSize: Int) : Boolean {
        // check if the length of the transmission is valid
        if((dataSize < 5)||(data.length < 13)){
            // invalid data size
            if(verboseLog){
                Log.e("readGroupString", "Invalid payLoadData size. Data-size was: $dataSize - minimum is 5!\n" +
                        "String-length was ${data.length}")
            }
            applicationProperty.logControl("E: Invalid payLoadData size. Data-size was: $dataSize - minimum is 5! String-length was ${data.length}")
            return false
        } else {
            // decode group string to class
            val laRoomyDevicePropertyGroup = LaRoomyDevicePropertyGroup()
            laRoomyDevicePropertyGroup.fromString(data)

            // add group and request next group if the loop is active
            if(this.groupLoopActive){
                // check transmission sub-type (only response is valid)
                if(data[1] == '2') {
                    this.laRoomyPropertyGroupList.add(laRoomyDevicePropertyGroup)
                    this.sendNextGroupRequest(laRoomyDevicePropertyGroup.groupIndex)
                } else {
                    // wrong transmission sub-type (response was expected)
                    if(verboseLog){
                        Log.e("readGroupString", "Invalid transmission sub-type. Type was: ${data[1]} | 2 was expected (response)")
                    }
                    applicationProperty.logControl("W: Invalid transmission sub-type on group transmission. Type was: ${data[1]} | 2 was expected (response)")
                }
            } else {

                // TODO: this could be a update, insert or remove transmission. So update, insert or remove data and launch event respectively

                    when(data[1]) {
                        '4' -> {    // update
                            if (verboseLog) {
                                Log.d(
                                    "readGroupString",
                                    "Group-Transmission received. Loop not active. UPDATE element with index: ${laRoomyDevicePropertyGroup.groupIndex}"
                                )
                            }
                            applicationProperty.logControl("I: Group-Transmission received. Loop not active. UPDATE element with index: ${laRoomyDevicePropertyGroup.groupIndex}")

                            // if the loop is not active this must be an update-transmission, so replace the group
                            if (this.laRoomyPropertyGroupList.size > laRoomyDevicePropertyGroup.groupIndex) {
                                this.laRoomyPropertyGroupList[laRoomyDevicePropertyGroup.groupIndex] =
                                    laRoomyDevicePropertyGroup
                            }
                            // update UI
                            this.updateGroupElementInUIList(laRoomyDevicePropertyGroup)
                        }
                        '5' -> {    // insert
                            if (verboseLog) {
                                Log.d(
                                    "readGroupString",
                                    "Group-Transmission received. Loop not active. INSERT element with index: ${laRoomyDevicePropertyGroup.groupIndex}"
                                )
                            }
                            applicationProperty.logControl("I: Group-Transmission received. Loop not active. INSERT element with index: ${laRoomyDevicePropertyGroup.groupIndex}")
                        }
                        else -> {
                            // invalid transmission sub-type (at this point)
                            if(verboseLog){
                                Log.e("readGroupString", "Invalid transmission sub-type on group transmission. Type was: ${data[1]}")
                            }
                            applicationProperty.logControl("W: Invalid transmission sub-type on group transmission. Type was: ${data[1]}")
                        }
                    }
            }
            return true
        }
    }

    private fun readPropertyString(data: String, dataSize: Int) : Boolean {

        // validate the string length and payLoadDataSize
        if((dataSize < 10)||(data.length < 18)){
            //invalid data size
            if(verboseLog){
                Log.e("readPropertyString", "Invalid data size. PayLoadData-Size was: $dataSize > minimum is 10!\n" +
                        "String-length was ${data.length} > minimum is 18")
            }
            applicationProperty.logControl("E: Invalid payLoadData size of property transmission. Data-Size was: $dataSize > minimum is 10! String-length was ${data.length}")
            return false
        } else {
            // decode property string to class
            val laRoomyDeviceProperty = LaRoomyDeviceProperty()
            laRoomyDeviceProperty.fromString(data)
            
            // TODO: what if the sub-type of the transmission is not correct, e.g. this is a request, not a response ????

            // add property and request next property if the loop is active
            if(this.propertyLoopActive){
                // check transmission sub-type (only response is valid)
                if(data[1] == '2') {
    
                    // TODO: question: when the property-index member of the decoded object does not equal the index which must be follow in the collection, is this an error?
    
                    this.laRoomyDevicePropertyList.add(laRoomyDeviceProperty)
                    this.sendNextPropertyRequest(laRoomyDeviceProperty.propertyIndex)
                } else {
                    // wrong transmission sub-type (response was expected)
                    if(verboseLog){
                        Log.e("readPropertyString", "Invalid transmission sub-type. Type was: ${data[1]} | 2 was expected (response)")
                    }
                    applicationProperty.logControl("W: Invalid transmission sub-type on property-transmission. Type was: ${data[1]} | 2 was expected (response)")
                }
            } else {
                // this could be an update or insert transmission. So update or insert data and launch event respectively
                when(data[1]) {
                    '4' -> {    // update
                        if (verboseLog) {
                            Log.d(
                                "readPropertyString",
                                "Property-Transmission received. Loop not active. UPDATE element with index: ${laRoomyDeviceProperty.propertyIndex}"
                            )
                        }
                        applicationProperty.logControl("I: Property-Transmission received. Loop not active. UPDATE element with index: ${laRoomyDeviceProperty.propertyIndex}")

                        // the loop is not active and the sub-type is update, so replace the property
                        if (this.laRoomyDevicePropertyList.size > laRoomyDeviceProperty.propertyIndex) {
                            // update internal array-list
                            this.laRoomyDevicePropertyList[laRoomyDeviceProperty.propertyIndex] =
                                laRoomyDeviceProperty
                            // update ui-adapter-list
                            this.updatePropertyElementInUIList(laRoomyDeviceProperty)
                        }
                    }
                    '5' -> {    // insert
                        if (verboseLog) {
                            Log.d(
                                "readPropertyString",
                                "Property-Transmission received. Loop not active. INSERT element with index: ${laRoomyDeviceProperty.propertyIndex}"
                            )
                        }
                        applicationProperty.logControl("I: Property-Transmission received. Loop not active. INSERT element with index: ${laRoomyDeviceProperty.propertyIndex}")

                        // the loop is not active and the sub-type is insert, so insert at the specified index
                        this.insertPropertyElement(laRoomyDeviceProperty)
                    }
                    else -> {
                        // invalid transmission sub-type (at this point)
                        if(verboseLog){
                            Log.e("readPropertyString", "Invalid transmission sub-type. Type was: ${data[1]}")
                        }
                        applicationProperty.logControl("W: Invalid transmission sub-type on property transmission. Type was: ${data[1]}")
                    }
                }
            }
            return true
        }
    }

    private fun readInitTransmission(data: String, payLoadDataSize: Int) : Boolean {
        // only accept init transmissions when loops not in progress
        if(this.propertyLoopActive || this.groupLoopActive || this.complexStateLoopActive || this.simpleStateLoopActive){
            // if this is a reload process, there is a possibility that a init request response was missed by
            // the remote device, if another request is sent and the device does respond to the first request there are two requests pending
            // when the first was processed, then the second will be treated normally like in the initial connection process
            // this must be prohibited
            return false
        }

        // check if the length of the transmission is valid (the payload size for the init response is 7 bytes)
        if((payLoadDataSize < 7)||(data.length < 14)){
            //invalid data size
            if(verboseLog){
                Log.e("readInitTransmission", "Invalid payLoadData size. Data-Size was: $payLoadDataSize > minimum is 7!\n" +
                        "String-length was: ${data.length}")
            }
            applicationProperty.logControl("E: Invalid payLoadData size of init-transmission. Data-Size was: $payLoadDataSize > minimum is 7! String-length was: ${data.length}")
            return false
        } else {
            // save device data:
            var hexString = "0x"
            hexString += data[8]
            hexString += data[9]

            bleDeviceData.propertyCount = Integer.decode(hexString)

            if(bleDeviceData.propertyCount <= 0){
                // this makes no sense
                this.callback.onConnectionError(BLE_INIT_NO_PROPERTIES)
            } else {
                // go ahead
                hexString = "0x"
                hexString += data[10]
                hexString += data[11]

                bleDeviceData.groupCount = Integer.decode(hexString)

                if (data[12] == '1') {
                    bleDeviceData.hasCachingPermission = true
                }

                // check if binding is required (except this is a reload action!)
                if(!this.reloadInitRequestPending) {
                    when {
                        (data[13] == '1') -> {
                            this.bleDeviceData.isBindingRequired = true

                            if (verboseLog) {
                                Log.d(
                                    "onInitTransmission",
                                    "Binding is required. Searching the appropriate key for the mac address."
                                )
                            }
                            applicationProperty.logControl("I: Binding is required. Searching the appropriate key for the mac address")

                            // binding is required, so send the binding request
                            sendBindingRequest()
                        }
                        else -> {
                            // no binding is required, so notify the subscriber of the callback
                            saveLastSuccessfulConnectedDeviceAddress(currentDevice?.address ?: "")
                            bleDeviceData.authenticationSuccess = true
                            callback.onInitializationSuccessful()
                        }
                    }
                }
                
                // check if single property mode is requested
                if(data.length >= 15){
                    // the length checkup is required since older implementations of the protocol end by index 13
                    if(data[14] == '1'){
                        // single property mode requested
                        if(bleDeviceData.propertyCount == 1 && bleDeviceData.groupCount == 0){
                            // only apply the request if the property count is one and the group count is zero!
                            bleDeviceData.isStandAlonePropertyMode = true
                        }
                    }
                }
                
                // position 15 to 17 reserved for future usage !!!

                // check if this is a reload process
                if(this.reloadInitRequestPending){
                    // reset control params
                    this.reloadInitRequestPending = false
                    this.reloadAttemptCounter = 0

                    // start the property loop
                    this.invokeCallbackLoopAfterUIDataGeneration = true
                    this.propertyLoopActive = true
                    this.startLoopTimeoutWatcher(LOOPTYPE_PROPERTY)
                    this.sendNextPropertyRequest(-1)
                }
            }
        }
        return true
    }

    private fun readBindingResponse(data: String, payLoadDataSize: Int) : Boolean {

        if((payLoadDataSize < 1)||(data.length < 9)){
            if(verboseLog){
                Log.e("BindingResponse", "Invalid data size of binding response. PayLoadData size was $payLoadDataSize - min is 1")
            }
            applicationProperty.logControl("W: Invalid payLoadData size of binding transmission. Data size was $payLoadDataSize - min is 1")
            return false
        } else {
            when {
                (data[8] == '2') -> {
                    // this is an authentication response
                    // check binding rejected flag
                    return if (data[9] != '0') {
                        // binding was rejected
                            when(data[9]){
                                '1' -> {
                                    if (verboseLog) {
                                        Log.e(
                                            "BindingResponse",
                                            "Binding passkey was rejected from device"
                                        )
                                    }
                                    applicationProperty.logControl("E: Binding passkey was rejected from device")
                                    callback.onBindingPasskeyRejected()
                                }
                                '2' -> {
                                    // TODO: binding not supported
                                }
                                else -> {
                                    // TODO: unknown error
                                }
                            }
                        true
                    } else {
                        // binding authentication success
                        if(verboseLog){
                            Log.d("Binding Response", "Binding Authentication successful!")
                        }
                        applicationProperty.logControl("I: Binding Authentication successful!")
                        
                        if(bleDeviceData.passKeyTypeUsed == PASSKEY_TYPE_SHARED){
                            // try to update a missing device name
                            val bManager = BindingDataManager(applicationProperty.applicationContext)
                            val bData = bManager.lookUpForBindingData(currentDevice?.address ?: "")
                            
                            if(bData.macAddress.isNotEmpty()){
                                if (ActivityCompat.checkSelfPermission(
                                        applicationProperty.applicationContext,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    bData.deviceName = currentDevice?.name ?: ""
                                    bManager.updateElement(bData)
                                }
                            }
                        }

                        saveLastSuccessfulConnectedDeviceAddress(currentDevice?.address ?: "")
                        bleDeviceData.authenticationSuccess = true
                        callback.onInitializationSuccessful()
                        true
                    }
                }
                (data[8] == '1') -> {
                    // this is an enable binding response
                    if (data[9] != '0') {
                        // enable binding failed
                        when (data[9]) {
                            '2' -> {
                                if (verboseLog) {
                                    Log.e("EnableBinding", "Enable-Binding Response: FAILED. Binding not supported!")
                                }
                                applicationProperty.logControl("E: Received Binding-Failed response from the device. -Binding not supported")

                                this.propertyCallback.onBindingResponse(
                                    BINDING_RESPONSE_BINDING_NOT_SUPPORTED
                                )
                            }
                            else -> {
                                if (verboseLog) {
                                    Log.e("EnableBinding", "Enable-Binding Response: FAILED. -Unknown error")
                                }
                                applicationProperty.logControl("E: Received Binding-Failed response from the device. -Unknown error")

                                this.propertyCallback.onBindingResponse(
                                    BINDING_RESPONSE_BINDING_ERROR
                                )
                            }
                        }
                    } else {
                        // enable binding success
                        if(verboseLog){
                            Log.d("EnableBinding", "Enable-Binding Response: SUCCESS.")
                        }
                        applicationProperty.logControl("I: Received Enable-Binding-Success response from the device.")

                        this.propertyCallback.onBindingResponse(
                            BINDING_RESPONSE_BINDING_SUCCESS
                        )
                    }
                    return true
                }
                (data[8] == '0') -> {
                    // this is an release binding response
                    if (data[9] != '0') {
                        // release binding failed
                        when (data[9]) {
                            '3' -> {
                                if (verboseLog) {
                                    Log.e("ReleaseBinding", "Release-Binding Response: FAILED. -wrong passkey")
                                }
                                applicationProperty.logControl("E: Received Release-Failed response from the device. -wrong passkey")

                                this.propertyCallback.onBindingResponse(
                                    BINDING_RESPONSE_RELEASE_BINDING_FAILED_WRONG_PASSKEY
                                )
                            }
                            else -> {
                                if (verboseLog) {
                                    Log.e("ReleaseBinding", "Release-Binding Response: FAILED. -Unknown error")
                                }
                                applicationProperty.logControl("E: Received Release-Failed response from the device. -Unknown error")

                                this.propertyCallback.onBindingResponse(
                                    BINDING_RESPONSE_RELEASE_BINDING_FAILED_UNKNOWN_ERROR
                                )
                            }
                        }
                    } else {
                        // release binding success
                        if(verboseLog){
                            Log.d("ReleaseBinding", "Release-Binding Response: SUCCESS.")
                        }
                        applicationProperty.logControl("I: Received Release-Binding-Success response from the device.")

                        this.propertyCallback.onBindingResponse(
                            BINDING_RESPONSE_RELEASE_BINDING_SUCCESS
                        )
                    }
                    return true
                }
                (data[8] == '3') -> {
                    // the param is unused -> must be a general not-implemented response
                    if (verboseLog) {
                        Log.d("BindingResponse", "Binding Response: FAILED. Binding not supported!")
                    }
                    applicationProperty.logControl("W: Binding-Failed response from the device. -Binding not supported")
    
                    this.propertyCallback.onBindingResponse(
                        BINDING_RESPONSE_BINDING_NOT_SUPPORTED
                    )
                    return true
                }
                else -> {
                    // invalid data
                    if(verboseLog){
                        Log.e("BindingData", "Invalid Binding Transmission Data!")
                    }
                    applicationProperty.logControl("E: Invalid Binding Transmission Data!")
                    return true
                }
            }
        }
    }

    private fun readFastDataSetterTransmission(data: String, payLoadDataSize: Int) : Boolean {
        return if((payLoadDataSize < 1)||(data.length < 9)){
            if(verboseLog){
                Log.e("readFastDataSetter", "DataSize too short. DataSize was $payLoadDataSize. String-length was: ${data.length}")
            }
            false
        } else {
            this.propertyCallback.onFastDataPipeInvoked(
                a2CharHexValueToIntValue(data.elementAt(2), data.elementAt(3)),
                data.removeRange(0, 8)
            )
            true
        }
    }

    fun startPropertyListing(addInALoopWhenReady: Boolean){

        this.invokeCallbackLoopAfterUIDataGeneration = addInALoopWhenReady

        if(verboseLog){
            Log.d("startPropList", "Property Listing requested -> Lookup if loading from cache is permitted.")
        }
        applicationProperty.logControl("I: Property Listing requested -> Lookup if loading from cache is permitted.")

        if(this.bleDeviceData.hasCachingPermission){
            if(applicationProperty.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_SaveProperties, true)){

                val propertyData =
                    PropertyCacheManager(applicationProperty.applicationContext)
                        .loadPCacheData(this.currentDevice?.address ?: "")

                if(propertyData.isValid){
                    if(verboseLog){
                        Log.d("startPropList", "Property and Group data loaded from cache.")
                    }
                    applicationProperty.logControl("I: Property and Group data loaded from cache.")

                    this.laRoomyDevicePropertyList = propertyData.deviceProperties
                    this.laRoomyPropertyGroupList = propertyData.devicePropertyGroups
                    this.generateUIAdaptableArrayListFromDeviceProperties(addInALoopWhenReady, true)
                    // NOTE:
                    // the properties were loaded from cache, so the states couldn't be considered as valid
                    // so the simple state data loop must be started, the preceding generation function will do that!

//
//                    if(!addInALoopWhenReady){
//
//                        // the properties were loaded from cache, so the states couldn't be considered as valid
//                        // so start the simple state data loop. If the loop is finished, the complex state loop will be started automatically
//                        this.startSimpleStateDataLoop()
//                    }
                    return
                } else {
                    if(verboseLog){
                        Log.d("startPropList", "Property data not valid. The reason is most likely that no property data is saved for the macAddress")
                    }
                    applicationProperty.logControl("I: No property data found. Loading from cache failed. Start requesting from device..")
                }
            } else {
                if(verboseLog){
                    Log.w("startPropList", "The user has not set up property caching. Requesting properties from device.")
                }
                applicationProperty.logControl("W: The user has not set up property caching. Start requesting properties from device.")
            }
        } else {
            if(verboseLog){
                Log.w("startPropList", "The remote device does not support loading from cache. Requesting properties from device.")
            }
            applicationProperty.logControl("W: The remote device does not support loading from cache. Start requesting properties from device.")
        }

        this.propertyLoopActive = true
        this.startLoopTimeoutWatcher(LOOPTYPE_PROPERTY)
        this.sendNextPropertyRequest(-1)
    }

    private fun startReloadProperties(){

        this.reloadInitRequestPending = true
        this.initDeviceTransmission()

        Executors.newSingleThreadScheduledExecutor().schedule({
            // check if the init transmission was received
            if(reloadInitRequestPending){
                // try again (and count the attempts)
                if(reloadAttemptCounter < 5) {
                    // log:
                    if(verboseLog){
                        Log.d("startReloadProperties", "Remote device missed response to init request(s). Attempts: $reloadAttemptCounter")
                    }
                    applicationProperty.logControl("W: Remote device missed response to init request(s). Attempts: $reloadAttemptCounter")
                    // increase counter and start again
                    this.reloadAttemptCounter++
                    this.initDeviceTransmission()
                } else {
                    // 5 sec and the remote device does not respond to init requests > stop process
                    Log.e("startReloadProperties", "Timeout for the reload process. Remote device does not respond to init requests!")
                    applicationProperty.logControl("E: Timeout for the reload process. Remote device does not respond to init requests!")
                }
            }
        }, 1000, TimeUnit.MILLISECONDS)

//        this.invokeCallbackLoopAfterUIDataGeneration = true
//        this.propertyLoopActive = true
//        this.startLoopTimeoutWatcher(LOOPTYPE_PROPERTY)
//        this.sendNextPropertyRequest(-1)
    }

    private fun sendNextPropertyRequest(currentIndex: Int){

        // increase index
        val newIndex = currentIndex + 1

        // check for the end of the property count
        if(newIndex < this.bleDeviceData.propertyCount){

            // notify timeout watcher
            this.notifyLoopIsWorking(newIndex)

            // build request string
            var rqString = "11"

            // add property-index as hex string
            val hexStr = Integer.toHexString(newIndex)
            if(hexStr.length <= 1){
                rqString += '0'
                rqString += hexStr
            } else {
                rqString += hexStr
            }

            //add data-size + flag values
            rqString += "0300"

            // add language identifier
            rqString += "${applicationProperty.systemLanguage}\r"

            sendData(rqString)
        } else {

            this.propertyLoopActive = false
            this.stopLoopTimeoutWatcher()

            if(verboseLog){
                Log.d("sendNextPropRQ", "Final property count reached. Property count is: ${this.bleDeviceData.propertyCount}")
            }
            applicationProperty.logControl("I: Final property count reached. Property-count is: ${this.bleDeviceData.propertyCount}")

            // the property loop is complete, check if there are groups and request them
            if(this.bleDeviceData.groupCount > 0){
                // there must be groups, proceed with the group loop
                this.startGroupListing()
            } else {
                // no groups, finalize the retrieval!

                // save data to cache if permitted
                if(this.bleDeviceData.hasCachingPermission && applicationProperty.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_SaveProperties, true)){

                    if(verboseLog){
                        Log.d("sendNextPropRQ", "Data caching is permitted. Saving property-data to cache.")
                    }
                    applicationProperty.logControl("I: Data caching is permitted. Saving property-data to cache.")

                    val devicePropertyCacheData = DevicePropertyCacheData()
                    devicePropertyCacheData.generate(this.laRoomyDevicePropertyList, this.laRoomyPropertyGroupList)

                    PropertyCacheManager(applicationProperty.applicationContext)
                        .savePCacheData(
                            devicePropertyCacheData,
                            this.currentDevice?.address ?: ""
                        )
                }

                // generate UI-data
                this.generateUIAdaptableArrayListFromDeviceProperties(this.invokeCallbackLoopAfterUIDataGeneration, false)

                // start retrieving the complex property states
//                Handler(Looper.getMainLooper()).postDelayed({
//                    this.startComplexStateDataLoop()
//                }, 1000)
            }
        }
    }

    private fun startGroupListing(){
        if(verboseLog){
            Log.d("startGroupList", "Starting group listing...")
        }
        applicationProperty.logControl("I: Starting group listing...")

        this.groupLoopActive = true
        this.startLoopTimeoutWatcher(LOOPTYPE_GROUP)
        this.sendNextGroupRequest(-1)
    }

    private fun sendNextGroupRequest(currentIndex: Int){

        // increase index
        val newIndex = currentIndex + 1

        // check for the end of the group count
        if(newIndex < this.bleDeviceData.groupCount){

            // notify timeout-watcher
            this.notifyLoopIsWorking(newIndex)

            // build request string
            var rqString = "21"

            // add property-index as hex string
            val hexStr = Integer.toHexString(newIndex)
            if(hexStr.length <= 1){
                rqString += '0'
                rqString += hexStr
            } else {
                rqString += hexStr
            }

            //add data-size + flag values
            rqString += "0300"

            // add language identifier + deliminator
            rqString += "${applicationProperty.systemLanguage}\r"

            sendData(rqString)
        } else {
            // end of loop
            this.groupLoopActive = false
            this.stopLoopTimeoutWatcher()

            if(verboseLog){
                Log.d("sendNextGroupRQ", "Final group count reached. Group-count is: ${this.bleDeviceData.groupCount}")
            }
            applicationProperty.logControl("I: Final group count reached. Group-count is: ${this.bleDeviceData.groupCount}")

            // save data to cache if permitted
            if(this.bleDeviceData.hasCachingPermission && applicationProperty.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_SaveProperties, true)){

                val devicePropertyCacheData = DevicePropertyCacheData()
                devicePropertyCacheData.generate(this.laRoomyDevicePropertyList, this.laRoomyPropertyGroupList)

                PropertyCacheManager(applicationProperty.applicationContext)
                    .savePCacheData(
                        devicePropertyCacheData,
                        this.currentDevice?.address ?: ""
                    )
            }

            // generate UI-data
            this.generateUIAdaptableArrayListFromDeviceProperties(this.invokeCallbackLoopAfterUIDataGeneration, false)

            // start retrieving the complex property states
//            Handler(Looper.getMainLooper()).postDelayed({
//                this.startComplexStateDataLoop()
//            }, 1000)
        }
    }

    private fun processRGBSelectorData(propertyIndex: Int, data: String){

        val rgbSelectorState = RGBSelectorState()

        // extract the rgb element data
        if(!rgbSelectorState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from RGB Selector data transmission.")
            return
        } else {
            val cState =
                rgbSelectorState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processEXLevelSelectorData(propertyIndex: Int, data: String){

        val exLevelState = ExtendedLevelSelectorState()

        // extract the ex level selector data
        if(!exLevelState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from Extended Level Selector data transmission.")
            return
        } else {
            val cState =
                exLevelState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processTimeSelectorData(propertyIndex: Int, data: String){

        val timeSelectorState = TimeSelectorState()

        // extract the time selector data
        if(!timeSelectorState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from Time Selector data transmission.")
            return
        } else {
            val cState =
                timeSelectorState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }

    }

    private fun processTimeFrameSelectorData(propertyIndex: Int, data: String){

        val timeFrameSelectorState = TimeFrameSelectorState()

        // extract the time-frame selector data
        if(!timeFrameSelectorState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from Time-Frame Selector data transmission.")
            return
        } else {
            val cState =
                timeFrameSelectorState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processUnlockControlData(propertyIndex: Int, data: String){

        val unlockControlState = UnlockControlState()

        // extract the data
        if(!unlockControlState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from UnLockControlState data transmission.")
            return
        } else {
            // check error flags
            if(unlockControlState.flags != 0){
                // this must be an error transmission, so notify user if the appropriate activity is open (and do not update internal state)
                if(this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() == propertyIndex) {
                    // search for UIAdapter-Element-Index
                    this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                        if((devicePropertyListContentInformation.internalElementIndex == propertyIndex)&&(devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)){
                            this.propertyCallback.onComplexPropertyStateChanged(index, unlockControlState.toComplexPropertyState())
                            return@forEachIndexed
                        }
                    }
                }
            } else {
                // this is a normal transmission so update internal state
                val cState =
                    unlockControlState.toComplexPropertyState()
    
                this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
            }
        }
    }

    private fun processNavigatorData(propertyIndex: Int, data: String){

        val navigatorStateData = NavigatorState()

        // extract the navigator data
        if(!navigatorStateData.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from NavigatorState data transmission.")
            return
        } else {
            val cState =
                navigatorStateData.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processBarGraphData(propertyIndex: Int, data: String){

        val barGraphState = BarGraphState()

        // get the existing state
        if(this.uIAdapterList.size > 0){
            this.uIAdapterList.forEach {
                if((it.internalElementIndex == propertyIndex)&&(it.elementType == PROPERTY_ELEMENT)){
                    barGraphState.fromComplexPropertyState(it.complexPropertyState)
                    return@forEach
                }
            }
        }

        // validate the existing state and apply the appropriate method
        var res: Boolean
        if(barGraphState.isValid()){
            // if the old state is valid, try to update the data in it
            res = barGraphState.updateFromString(data)
            if(!res){
                // the state does not conform to the update regularities (the bar to update is outside of range, or something)
                // so try to reset the whole state
                res = barGraphState.fromString(data)
            }
        } else {
            res = barGraphState.fromString(data)
        }
        
        // TODO: remove !
//        val res = if(barGraphState.isValid()){
//            if(!barGraphState.updateFromString(data)){
//                barGraphState.fromString(data)
//            } else {
//                true
//            }
//        } else {
//            barGraphState.fromString(data)
//        }

        // check the result and update the data (or handle error)
        if(!res){
            // handle error (log + return)
            if(verboseLog){
                Log.e("BLEConnectionManager", "processBarGraphData: Error reading data from BarGraphState data transmission.")
            }
            applicationProperty.logControl("E: Error reading data from BarGraphState data transmission.")
            return
        } else {
            val cState =
                barGraphState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processLineGraphData(propertyIndex: Int, data: String){

        val lineGraphState = LineGraphState()

        // get the existing state
        if(this.uIAdapterList.size > 0){
            this.uIAdapterList.forEach {
                if((it.internalElementIndex == propertyIndex)&&(it.elementType == PROPERTY_ELEMENT)){
                    lineGraphState.fromComplexPropertyState(it.complexPropertyState)
                    return@forEach
                }
            }
        }

        // validate the existing state and apply the appropriate method
        val res = if(lineGraphState.isValid() && data[10] != '0'){              // field 10 indicates the transmission-type (override|update)
            lineGraphState.updateFromString(data)
        } else {
            lineGraphState.fromString(data)
        }

        // check the result and update the data (or handle error)
        if(!res){
            // handle error
            applicationProperty.logControl("E: Error reading data from LineGraphState data transmission.")
            return
        } else {
            val cState =
                lineGraphState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processStringInterrogatorData(propertyIndex: Int, data: String){

        val stringInterrogatorState = StringInterrogatorState()

        // extract the data
        if(!stringInterrogatorState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from StringInterrogatorState data transmission.")
            return
        } else {
            val cState =
                stringInterrogatorState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun processTextListPresenterData(propertyIndex: Int, data: String){

        // NOTE: this complex state is a bit different, in the "fromString(.." method data could be added
        //          -> so the fromString method must be executed on the existing object
        // the internal stack and the data-set used for binding in the textListPresenter activity must be different sources, but both must be updated on transmission

        // first get the existing state object
        val textListPresenterState = TextListPresenterState()

        textListPresenterState.fromComplexPropertyState(
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).complexPropertyState
        )

        // extract the data
        if(!textListPresenterState.fromString(data)){
            // handle error
            applicationProperty.logControl("E: Error reading data from textListPresenter data transmission.")
            return
        } else {
            var uiIndex = -1
            val cState =
                textListPresenterState.toComplexPropertyState()

            // update internal array
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).complexPropertyState = cState

            // update ui-array
            this.uIAdapterList.forEachIndexed { index, dlc ->
                if((dlc.elementType == PROPERTY_ELEMENT)&&(dlc.internalElementIndex == propertyIndex)){
                    dlc.complexPropertyState = cState
                    uiIndex = index
                    return@forEachIndexed
                }
            }

            // check if the respective property page is open
            if(this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() == propertyIndex){
                // the page is open, so add the new data over the complex property state update, but only put the new string in the object
                // this is a temporary object to forward to the property callback
                val eventObject = ComplexPropertyState()

                if(textListPresenterState.textListBackgroundStack.isEmpty()){
                    // the array is empty so this must have been a clear stack transmission
                    eventObject.valueOne = 0
                } else {
                    eventObject.valueOne = 2
                    eventObject.strValue = textListPresenterState.textListBackgroundStack.last()
                }

                this.propertyCallback.onComplexPropertyStateChanged(uiIndex, eventObject)
            }
        }
    }

    private fun sendPropertyStateRequest(propertyIndex: Int){
        // notify the watcher
        this.notifyLoopIsWorking(propertyIndex)

        // build request string
        var rqString = "31"
        val hexString = Integer.toHexString(propertyIndex)
        if(hexString.length < 2){
            rqString += '0'
            rqString += hexString
        } else {
            rqString += hexString
        }
        rqString += "0000\r"

        // send request
        this.sendData(rqString)
    }

    fun setBleEventHandler(eventHandlerObject: BleEventCallback){
        this.callback = eventHandlerObject
    }

    fun setPropertyEventHandler(pEvents: PropertyCallback){
        this.propertyCallback = pEvents
    }

    fun initDeviceTransmission(){
        if(verboseLog) {
            Log.d("initDeviceTransmsn", "Sending initialization string")
        }
        applicationProperty.logControl("I: Sending initialization string...")
        if(this.isConnected){

            val initString = "71000000\r"
            sendData(initString)

        } else {
            if(verboseLog){
                try {
                    Log.e(
                        "initDeviceTransmsn",
                        "Init failed - no connection/ dev-info: address: ${this.currentDevice?.address} dev-type: ${this.currentDevice?.type} ?: ${this.currentDevice}"
                    )
                } catch (e: SecurityException){
                    Log.e("initDeviceTransmission", "Security Exception: $e")
                }
            }
            applicationProperty.logControl("E: Init failed - no connection")
        }
    }


    fun clear(){
        try {
            this.stopRssiReadTimer()
            this.stopLoopTimeoutWatcher()
        } catch (e: Exception){
            Log.e("BLEConnectionManager", "M:onClear exception - $e")
        }
        try {
            this.bluetoothGatt?.close()
        } catch(e: SecurityException){
            Log.e("BleManager:Clear()", "BleManager:onClear - $e")
            applicationProperty.logControl("E: Missing permission - $e")
        }
        this.bluetoothGatt = null

        this.currentDevice = null
        this.currentUsedServiceUUID = ""
        this.isConnected = false

        this.connectionSuspended = false
        this.isResumeConnectionAttempt = false
        this.suspendedDeviceAddress = ""

        this.invokeCallbackLoopAfterUIDataGeneration = false
        this.reloadAttemptCounter = 0
        this.reloadInitRequestPending = false

        // loops
        this.propertyLoopActive = false
        this.groupLoopActive = false
        this.complexStateLoopActive = false
        this.simpleStateLoopActive = false

        // simple & complex state params
        this.currentSimpleStateRetrievingIndex = -1
        this.currentComplexStateRetrievingIndex = -1
        this.simpleStatePropertyIndexes.clear()
        this.complexStatePropertyIndexes.clear()

        // property holder
        this.uIAdapterList.clear()
        this.laRoomyDevicePropertyList.clear()
        this.laRoomyPropertyGroupList.clear()

        // data holder
        this.bleDeviceData.clear()
        this.timeoutWatcherData.clear()
        this.discoveryWatcherData.clear()
        this.openFragmentedTransmissionData.clear()
        
        
    }

    private fun clearPropertyRelatedParameterAndStopAllLoops(){

        // stop loops
        this.propertyLoopActive = false
        this.groupLoopActive = false
        this.simpleStateLoopActive = false
        this.complexStateLoopActive = false
        this.invokeCallbackLoopAfterUIDataGeneration = false
        this.reloadInitRequestPending = false

        // clear arrays
        this.uIAdapterList.clear()
        this.laRoomyDevicePropertyList.clear()
        this.laRoomyPropertyGroupList.clear()
        this.simpleStatePropertyIndexes.clear()
        this.complexStatePropertyIndexes.clear()
        this.bleDeviceData.clear()

        // reset other params
        this.currentSimpleStateRetrievingIndex = -1
        this.currentComplexStateRetrievingIndex = -1
        this.reloadAttemptCounter = 0

        // this should be empty at this point, but clear it anyway
        this.openFragmentedTransmissionData.clear()
    }

    fun clearInternalPropertyStateStringValue(propertyIndex: Int){
        // this function is only a workaround, not the best approach :/
        if(propertyIndex < this.laRoomyDevicePropertyList.size){
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).complexPropertyState.strValue = ""
        }
    }

    fun getLastConnectedDeviceAddress() : String {
        val address =
            this.applicationProperty.loadSavedStringData(R.string.FileKey_BLEManagerData, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)

        return if(address == ERROR_NOTFOUND) ""
        else address
    }

    fun connectToLastSuccessfulConnectedDevice() {
        val address =
            this.applicationProperty.loadSavedStringData(R.string.FileKey_BLEManagerData, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)

        if(address == ERROR_NOTFOUND) {
            //this.callback.onConnectionAttemptFailed("Error: no device address found")

            // TODO: error callback!

        } else {
            this.connectToRemoteDevice(address)
        }
    }

    private fun connectToRemoteDevice(macAddress: String?){
        try {
            this.currentDevice =
                (applicationProperty.applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.getRemoteDevice(
                    macAddress
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.bluetoothGatt = this.currentDevice?.connectGatt(
                    applicationProperty.applicationContext,
                    false,
                    this.gattCallback
                )
            } else {
                this.bluetoothGatt = this.currentDevice?.connectGatt(
                    applicationProperty.applicationContext,
                    false,
                    this.oldGattCallback
                )
            }
        } catch (e: SecurityException){
            Log.e("connectToRemoteDevice", "Security Exception occurred while trying to connect to remote device: ${e.message}")
            this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
        }
    }

    fun connectToBondedDeviceWithMacAddress(macAddress: String?){
        try {
            var deviceFound = false

            this.bleAdapter?.bondedDevices?.forEach {
                if (it.address == macAddress) {
                    this.currentDevice = it
                    deviceFound = true
                }
            }

            if(!deviceFound){
                // device not found, the requested device is maybe not bonded anymore, but remains in the myDeviceList !
                this.currentDevice =
                    (applicationProperty.applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.getRemoteDevice(
                        macAddress
                    )
            }
    
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.bluetoothGatt = this.currentDevice?.connectGatt(
                    applicationProperty.applicationContext,
                    false,
                    this.gattCallback
                )
            } else {
                this.bluetoothGatt = this.currentDevice?.connectGatt(
                    applicationProperty.applicationContext,
                    false,
                    this.oldGattCallback
                )
            }
        } catch (e: SecurityException){
            Log.e("conToBondedDevWithMacAddress", "Security Exception occurred while trying to connect to bonded device with mac-address: ${e.message}")
            this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
        }
    }

/*
    private fun connect(){
        if(this.currentDevice != null) {
            this.bluetoothGatt =
                this.currentDevice?.connectGatt(this.activityContext, false, this.gattCallback)
        }
        else
            this.callback.onConnectionAttemptFailed(activityContext.getString(R.string.Error_ConnectionFailed_NoDevice))
    }
*/

    fun sendData(data: String){

        try {
            if (!this.isConnected) {
                Log.e("M:sendData", "Unexpected error, bluetooth device not connected")
                applicationProperty.logControl("E: Unexpected: sendData invoked, but device not connected")

                this.callback.onConnectionError(BLE_UNEXPECTED_BLUETOOTH_DEVICE_NOT_CONNECTED)
            } else {
    
                // save the data for echo prevention
                this.echoPreventerDataHolder = data
    
    
                if (verboseLog) {
                    Log.d("M:sendData", "writing characteristic: data: $data")
                }

                // make sure the passkey is hidden in the connection log
                if (data.elementAt(0) == '6') {
                    // must be a binding transmission -> hide passkey (if there is one)
                    var logData = data
                    if (logData.length > 9) {
                        logData = logData.removeRange(9, logData.length - 1)
                        logData += "<passkey hidden!>"
                    }
                    applicationProperty.logControl("I: Send Data: $logData")
                } else {
                    applicationProperty.logControl("I: Send Data: $data")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationProperty.applicationContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        this.bluetoothGatt?.writeCharacteristic(
                            this.txGattCharacteristic,
                            data.toByteArray(),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                    }
                } else {
                    // deprecated since api level 33
                    @Suppress("DEPRECATION")
                    this.txGattCharacteristic.setValue(data)
    
                    if (this.bluetoothGatt == null) {
                        Log.e("M:sendData", "Member bluetoothGatt was null!")
                    }

                    try {
                        @Suppress("DEPRECATION")
                        this.bluetoothGatt?.writeCharacteristic(this.txGattCharacteristic)
                    } catch (e: SecurityException){
                        Log.e("BLEManager:sendData", "Security Exception: ${e.message}")
                        this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
                    }
                }
    
                // save the data for echo prevention
                //this.echoPreventerDataHolder = data
                // set the characteristic value
                //this.rxGattCharacteristic.setValue(data)
//                this.txGattCharacteristic.setValue(data)
//
//
//
//                if (this.bluetoothGatt == null) {
//                    Log.e("M:sendData", "Member bluetoothGatt was null!")
//                }
//
//                // write the characteristic
//                try {
//                    this.bluetoothGatt?.writeCharacteristic(this.txGattCharacteristic)
//                } catch (e: SecurityException) {
//                    Log.e("BLEManager:sendData", "Security Exception: ${e.message}")
//                    this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
//                }
            }
        } catch (e: Exception) {
            Log.e("BLEManager:sendData", "Unexpected Error: $e")
        }
    }

    fun checkBluetoothEnabled() : Int{
        if(verboseLog) {
            Log.d("M:bluetoothEnabled?", "Check if bluetooth is enabled")
        }
        try {

            return if(bleAdapter?.isEnabled == true){
                BLE_IS_ENABLED
            } else {
                BLE_IS_DISABLED
            }

//            bleAdapter?.takeIf { it.isDisabled }?.apply {
//                // this lambda expression will be applied to the object(bleAdapter) (but only if "isDisabled" == true)
//                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                caller.startActivityForResult(enableBtIntent, requestEnableBT)
//            }
        } catch (e: SecurityException){
            Log.e("checkBluetoothEnabled", "Error, while checking if bluetooth is enabled: $e")
            return BLE_BLUETOOTH_PERMISSION_MISSING
        }
    }

    fun close(){
        try {
            this.bluetoothGatt?.close()
        } catch (e: SecurityException){
            Log.e("BLEManager:onClose()", "Security Exception: ${e.message}")
            this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
        }
        this.bluetoothGatt = null
        this.isConnected = false
    }

    fun disconnect(){
        try {
            this.bluetoothGatt?.disconnect()
        } catch (e: SecurityException){
            Log.e("BLEManager:disconnect()", "Security Exception: ${e.message}")
            this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
        }
        this.isConnected = false
    }

    fun suspendConnection(){
        if(verboseLog) {
            Log.d("M:Bmngr:suspendC", "BluetoothManager: suspendConnection invoked")
        }
        this.isResumeConnectionAttempt = false
        this.connectionSuspended = true
        this.suspendedDeviceAddress = this.currentDevice?.address ?: ""
        this.disconnect()
    }

    fun resumeConnection(){
        Log.d("M:Bmngr:resumeC", "BluetoothManager: resumeConnection invoked")
        this.connectionSuspended = false
        this.isResumeConnectionAttempt = true

        if(this.suspendedDeviceAddress.isNotEmpty()){
            if(verboseLog) {
                Log.d(
                    "M:Bmngr:resumeC",
                    "BluetoothManager: resumeConnection: Internal Device Address: ${this.suspendedDeviceAddress}"
                )
            }
            applicationProperty.logControl("I: Connection was suspended: trying to resume connection")

            try {
                // trigger event
                this.callback.onConnectionEvent(BLE_MSC_EVENT_ID_RESUME_CONNECTION_STARTED)
    
                // start connection process
                this.bluetoothGatt?.connect()

                // set up a handler to check if the connection works
                Executors.newSingleThreadScheduledExecutor().schedule({
                    if (!isConnected) {
                        callback.onConnectionError(
                            BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE
                        )
                    }
                }, 10000, TimeUnit.MILLISECONDS)

            } catch (e: SecurityException){
                Log.e("resumeConnection", "Error while trying to resume connection")
                this.callback.onConnectionError(BLE_BLUETOOTH_PERMISSION_MISSING)
            }
        } else {
            Log.e("M:Bmngr:resumeC", "BluetoothManager: Internal Device Address invalid- trying to connect to saved address")
            this.callback.onConnectionError(
                BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE)
        }
    }

    private fun saveLastSuccessfulConnectedDeviceAddress(address: String){
        if(verboseLog) {
            Log.d(
                "M:SaveAddress",
                "Saving address of successful connected device - address: $address"
            )
        }
        this.applicationProperty.saveStringData(address, R.string.FileKey_BLEManagerData, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)
    }

    fun reloadProperties(){
        this.clearPropertyRelatedParameterAndStopAllLoops()
        this.startReloadProperties()
    }

    private fun savePropertyDataToCacheIfPermitted(){
        // save data to cache if permitted
        if(this.bleDeviceData.hasCachingPermission && applicationProperty.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_SaveProperties, true)){

            if(verboseLog){
                Log.d("SavePDataToCache", "Caching is permitted: Saving property and group data to cache.")
            }
            applicationProperty.logControl("I: Caching is permitted - Saving property and group data to cache.")

            val devicePropertyCacheData = DevicePropertyCacheData()
            devicePropertyCacheData.generate(this.laRoomyDevicePropertyList, this.laRoomyPropertyGroupList)

            PropertyCacheManager(applicationProperty.applicationContext)
                .savePCacheData(
                    devicePropertyCacheData,
                    this.currentDevice?.address ?: ""
                )
        }
    }

    private fun generateUIAdaptableArrayListFromDeviceProperties(addInALoop: Boolean, isCacheLoadingOperation: Boolean){

        this.uIAdapterList.clear()
        val tempList = ArrayList<DevicePropertyListContentInformation>()

        try {
            if (this.laRoomyDevicePropertyList.size > 0) {

                var globalIndex = 0
                var currentGroup = -1
                var expectedGroupIndex = 0

                this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->

                    // check if the property-element is part of a group
                    if (laRoomyDeviceProperty.isGroupMember) {
                        // element is part of a group, add the group first (if it isn't already)

                        // perform error checkup
                        if ((laRoomyDeviceProperty.groupIndex != expectedGroupIndex)&&(currentGroup == -1)) {
                            // the group-index has not the expected value, there must be a missing or duplicate index
                            if (verboseLog) {
                                Log.w(
                                    "generateUIArray",
                                    "Inconsistency detected: Group-Index has not the expected value on Property Index: $index\nGroup Index was: ${laRoomyDeviceProperty.groupIndex} - Expected index: $expectedGroupIndex"
                                )
                            }
                            applicationProperty.logControl(
                                "W: Inconsistency detected: Group-Index has not the expected value on Property Index: $index " +
                                        "Group Index was: ${laRoomyDeviceProperty.groupIndex} - Expected index: $expectedGroupIndex - " +
                                        "The visual state may not be as expected!"
                            )
                            // TODO: maybe fix the inconsistency automatically?? laRoomyDeviceProperty.groupIndex = expectedGroupIndex ???
                        }

                        // check if this element is part of the next group
                        if((laRoomyDeviceProperty.groupIndex > expectedGroupIndex)&&(currentGroup != -1)){
                            currentGroup = -1
                            expectedGroupIndex++
                            // the last element must have been the last in group
                            if(addInALoop){
                                tempList.elementAt(globalIndex - 1).apply {
                                    isLastInGroup = true
                                    update(applicationProperty.cViewContext)
                                }
                            } else {
                                this.uIAdapterList.elementAt(globalIndex - 1).apply {
                                    isLastInGroup = true
                                    update(applicationProperty.cViewContext)
                                }
//                                this.uIAdapterList.elementAt(globalIndex - 1).isLastInGroup = true
//                                this.uIAdapterList.elementAt(globalIndex - 1)
//                                    .update(applicationProperty.applicationContext)
                            }
                        }

                        // add the group element
                        if (currentGroup == -1) {
                            currentGroup = laRoomyDeviceProperty.groupIndex

                            // make sure the group exists
                            if (expectedGroupIndex < laRoomyPropertyGroupList.size) {
                                // get the element
                                val laRoomyDevicePropertyGroup =
                                    this.laRoomyPropertyGroupList.elementAt(expectedGroupIndex)

                                // create the group entry
                                val dpl = DevicePropertyListContentInformation(GROUP_ELEMENT)
                                dpl.internalElementIndex = laRoomyDevicePropertyGroup.groupIndex
                                dpl.elementDescriptorText = laRoomyDevicePropertyGroup.groupName
                                dpl.imageID = laRoomyDevicePropertyGroup.imageID
                                // add the global index of the position in the array
                                dpl.globalIndex = globalIndex
                                globalIndex++
                                // generate internal data and resources
                                dpl.update(applicationProperty.cViewContext)
                                // add the group to the list
                                if(addInALoop){
                                    tempList.add(dpl)
                                } else {
                                    this.uIAdapterList.add(dpl)
                                }
                            } else {
                                if(verboseLog){
                                    Log.e("generateUIArray", "Error: group index out of range")
                                }
                                applicationProperty.logControl("E: Group index out of range!")
                            }
                        }

                        // add the property element(s) to the group
                        val propertyEntry = DevicePropertyListContentInformation(PROPERTY_ELEMENT)
                        propertyEntry.isGroupMember = true
                        propertyEntry.isEnabled = laRoomyDeviceProperty.isEnabled
                        propertyEntry.elementDescriptorText = laRoomyDeviceProperty.propertyDescriptor
                        propertyEntry.imageID = laRoomyDeviceProperty.imageID
                        propertyEntry.internalElementIndex = laRoomyDeviceProperty.propertyIndex
                        propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
                        propertyEntry.simplePropertyState = laRoomyDeviceProperty.propertyState
                        // set global index
                        propertyEntry.globalIndex = globalIndex
                        globalIndex++
                        // generate internal data and resources
                        propertyEntry.update(applicationProperty.cViewContext)
                        // add it to the list
                        if(addInALoop){
                            tempList.add(propertyEntry)
                        } else {
                            this.uIAdapterList.add(propertyEntry)
                        }

                        // check if this is the last property element
                        if(laRoomyDevicePropertyList.size == index + 1){
                            currentGroup = -1
                            expectedGroupIndex++
                            // the last element must have been the last in group
                            if(addInALoop){
                                tempList.elementAt(globalIndex - 1)
                                    .apply {
                                        this.isLastInGroup = true
                                        this.update(applicationProperty.cViewContext)
                                    }
                            } else {
                                this.uIAdapterList.elementAt(globalIndex - 1)
                                    .apply {
                                        this.isLastInGroup = true
                                        this.update(applicationProperty.cViewContext)
                                    }
                            }
                            return@forEachIndexed // TODO: check if this will work!!!
                        }

                    } else {
                        // element is not part of a group, add it raw

                        // but before reset the group params (if they are set)
                        if (currentGroup != -1) {
                            currentGroup = -1
                            expectedGroupIndex++
                            // the last element must have been the last in group
                            if(addInALoop){
                                tempList.elementAt(globalIndex - 1)
                                    .apply {
                                        this.isLastInGroup = true
                                        this.update(applicationProperty.cViewContext)
                                    }
                            } else {
                                this.uIAdapterList.elementAt(globalIndex - 1)
                                    .apply {
                                        this.isLastInGroup = true
                                        this.update(applicationProperty.cViewContext)
                                    }
                            }
                        }

                        // create the entry
                        val propertyEntry = DevicePropertyListContentInformation(PROPERTY_ELEMENT)
                        propertyEntry.internalElementIndex = laRoomyDeviceProperty.propertyIndex
                        propertyEntry.isEnabled = laRoomyDeviceProperty.isEnabled
                        propertyEntry.imageID = laRoomyDeviceProperty.imageID
                        propertyEntry.elementDescriptorText = laRoomyDeviceProperty.propertyDescriptor
                        propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
                        propertyEntry.simplePropertyState = laRoomyDeviceProperty.propertyState
                        // set global index
                        propertyEntry.globalIndex = globalIndex
                        globalIndex++
                        // generate internal data and resources
                        propertyEntry.update(applicationProperty.cViewContext)
                        // add it to the list
                        if(addInALoop){
                            tempList.add(propertyEntry)
                        } else {
                            this.uIAdapterList.add(propertyEntry)
                        }
                    }
                }
            } else {
                if (verboseLog) {
                    Log.e(
                        "generateUIArray",
                        "Generation of UI-Array not possible, property list is empty."
                    )
                }
                return
            }
        } catch (e: Exception) {
            Log.e("generateUIArray", "Fatal error while generating the UI-Adapter Array. Exception: $e")
            applicationProperty.logControl("E: Fatal error while generating the UIAdapter data. Exception: $e")
            return
        }

        // add the elements with a timer-delay
        if(addInALoop) {
            uIItemAddCounter = 0

            Timer().scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        try {

                            uIAdapterList.add(tempList.elementAt(uIItemAddCounter))

                            propertyCallback.onUIAdaptableArrayListItemAdded(
                                tempList.elementAt(
                                    uIItemAddCounter
                                )
                            )

                            uIItemAddCounter++

                            if (uIItemAddCounter == tempList.size) {
                                // cancel timer
                                cancel()
                                uIItemAddCounter = -1
                                propertyCallback.onUIAdaptableArrayListGenerationComplete(
                                    uIAdapterList
                                )
                                // notify the finalization of the process
                                if(isCacheLoadingOperation){
                                    sendData(propertyLoadedFromCacheCompleteNotification)
                                } else {
                                    sendData(propertyLoadingCompleteNotification)
                                }
                                // start complex property state retrieving loop
                                Executors.newSingleThreadScheduledExecutor().schedule({
                                    startComplexStateDataLoop()
                                }, 300, TimeUnit.MILLISECONDS)
                            }
                        } catch (e: IndexOutOfBoundsException) {
                            if (verboseLog) {
                                Log.e(
                                    "generateUIArray",
                                    "Error while adding the Elements to the view. Exception: $e"
                                )
                            }
                            applicationProperty.logControl("E: Error while adding the elements to the view. Exception: $e")
                            // FIXME: reset the whole???
                            cancel()
                        }
                    }
                },
                (0).toLong(),
                (100).toLong()
            )// 300 or higher is the best (frame-skipping problem) // but 210 does not show any skipped frame with the parameter 5 frames set!
        } else {
            propertyCallback.onUIAdaptableArrayListGenerationComplete(
                uIAdapterList
            )

            // notify finalization of the process
            Executors.newSingleThreadScheduledExecutor().schedule({
                if(isCacheLoadingOperation){
                    sendData(propertyLoadedFromCacheCompleteNotification)
                } else {
                    sendData(propertyLoadingCompleteNotification)
                }

            }, 200, TimeUnit.MILLISECONDS)

            // start complex property state retrieving loop
            Executors.newSingleThreadScheduledExecutor().schedule({
                if(isCacheLoadingOperation){
                    // the properties were loaded from cache, so the states couldn't be considered as valid
                    // so start the simple state data loop. If the loop is finished, the complex state loop will be started automatically
                    startSimpleStateDataLoop()
                } else {
                    startComplexStateDataLoop()
                }
            }, 500, TimeUnit.MILLISECONDS)
        }
    }

    fun updatePropertyStates(){
        // start the simple-state loop, the complex-state loop will start automatically after this loop
        this.startSimpleStateDataLoop()
    }

    private fun startSimpleStateDataLoop(){
        if(verboseLog) {
            Log.d(
                "M:startSimpleDataLoop",
                "SimpleStateDataLoop started - At first: Indexing the Property-IDs with: simpleStateData"
            )
        }
        // clear the Index-Array
        this.simpleStatePropertyIndexes.clear()
        // loop the properties and save the indexes of the properties with simple state data
        this.laRoomyDevicePropertyList.forEach {
            // check if this is a property with simple state data (NOTE: not all simple properties have state data)
            if(this.checkPropertyForSimpleStateDataFromPropertyType(it.propertyType)){
                this.simpleStatePropertyIndexes.add(it.propertyIndex)
            }
        }

        // if there are properties with simple state -> start the loop
        if(this.simpleStatePropertyIndexes.isNotEmpty()){
            if(verboseLog) {
                Log.d(
                    "M:startSimpleDataLoop",
                    "Found ${this.simpleStatePropertyIndexes.size} Elements with SimpleStateData -> start collecting from device"
                )
            }
            applicationProperty.logControl("I: Starting simple state data loop. Elements with simple state: ${this.simpleStatePropertyIndexes.size}")

            this.currentSimpleStateRetrievingIndex = 0
            this.simpleStateLoopActive = true

            // start the watcher, to prohibit a blocking loop if the device is not responding
            this.startLoopTimeoutWatcher(LOOPTYPE_SIMPLESTATE)

            // start requesting the simple states
            this.sendPropertyStateRequest(
                this.simpleStatePropertyIndexes.elementAt(currentSimpleStateRetrievingIndex)
            )
        } else {
            // there are no properties with simple-state, so start the complex loop here
            this.startComplexStateDataLoop()
        }
    }

    private fun startComplexStateDataLoop(){
        if(verboseLog) {
            Log.d(
                "M:startCompDataLoop",
                "ComplexStateDataLoop started - At first: Indexing the Property-IDs with: complexStateData"
            )
        }
        // clear the Index-Array
        this.complexStatePropertyIndexes.clear()
        // loop the properties and save the indexes of the properties with complex state data
        this.laRoomyDevicePropertyList.forEach {
            // start with the first complex state data element
            if(it.propertyType >= COMPLEX_PROPERTY_START_INDEX){
                this.complexStatePropertyIndexes.add(it.propertyIndex)
            }
        }

        // if there are properties with complex state -> start the loop
        if(this.complexStatePropertyIndexes.isNotEmpty()) {
            if(verboseLog) {
                Log.d(
                    "M:startCompDataLoop",
                    "Found ${this.complexStatePropertyIndexes.size} Elements with ComplexStateData -> start collecting from device"
                )
            }
            applicationProperty.logControl("I: Starting complex state data loop. Elements with complex state: ${this.complexStatePropertyIndexes.size}")

            this.currentComplexStateRetrievingIndex = 0
            this.complexStateLoopActive = true

            // start the watcher, to prohibit a blocking loop if the device is not responding
            this.startLoopTimeoutWatcher(LOOPTYPE_COMPLEXSTATE)

            this.sendComplexPropertyStateRequest(
                this.complexStatePropertyIndexes.elementAt(this.currentComplexStateRetrievingIndex)
            )
        } else {
            if(verboseLog) {
                Log.d(
                    "M:startCompDataLoop",
                    "No elements found - exit process"
                )
            }
            applicationProperty.logControl("I: No elements found. Skip operation.")
        }
    }

    private fun sendComplexPropertyStateRequest(propertyIndex: Int){

        // notify the timeout-watcher
        this.notifyLoopIsWorking(propertyIndex)

        val hexIndex = Integer.toHexString(propertyIndex)
        var requestString = "31"

        if(hexIndex.length > 1){
            requestString += hexIndex
        } else {
            requestString += '0'
            requestString += hexIndex
        }

        requestString += "0000\r"

        this.sendData(requestString)
    }

    private fun sendBindingRequest(){

        val passKey: String

        // at first look for a shared key for the device
        val bindingPairManager = BindingDataManager(applicationProperty.applicationContext)
        val bindingData = bindingPairManager.lookUpForBindingData(this.currentDevice?.address ?: "")

        // if a shared binding key for the mac address exists, the key is preferred,
        // because a key from a sharing link will only be saved if it defers from the main key

        passKey = if (bindingData.passKey.isNotEmpty() && (bindingData.passKey != ERROR_NOTFOUND)) {

            bleDeviceData.passKeyTypeUsed = if(bindingData.generatedAsOriginator){
                PASSKEY_TYPE_NORM
            } else {
                PASSKEY_TYPE_SHARED
            }
            bindingData.passKey
        } else {
            bleDeviceData.passKeyTypeUsed = PASSKEY_TYPE_NORM
            (applicationProperty.loadSavedStringData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_DefaultRandomBindingPasskey
            ))
        }
        if(passKey == ERROR_NOTFOUND){
            // critical error (should not happen)
            this.callback.onConnectionError(BLE_UNEXPECTED_CRITICAL_BINDING_KEY_MISSING)
        } else {
            // at first, build the request-string
            var bindingRequestString = "6100"
            val dataSize = 2 + passKey.length
            val hexString = Integer.toHexString(dataSize)

            if(hexString.length == 1){
                bindingRequestString += '0'
                bindingRequestString += hexString
            } else {
                bindingRequestString += hexString
            }
            bindingRequestString += "002$passKey\r"

            // send the request
            sendData(bindingRequestString)
        }
    }

    fun notifyBackNavigationToDeviceMainPage() {
        sendData(this.userNavigatedBackToDeviceMainNotification)
    }

    fun notifyComplexPropertyPageInvoked(propertyID: Int) {

        if(verboseLog) {
            Log.d(
                "M:NotifyMCPPI",
                "Multi-Complex-Property-Page invoked - sending notification - property-ID: $propertyID"
            )
        }
        var notification = "50"
        notification += a8bitValueTo2CharHexValue(propertyID)
        notification += "02003\r"

        sendData(notification)
    }

    fun sendFactoryResetCommand(){
        this.sendData(this.factoryResetCommand)
    }

    fun enableDeviceBinding(passKey: String){
        // build enable binding string
        var bindingString = "6100"
        bindingString += a8bitValueTo2CharHexValue(passKey.length + 2)
        bindingString += "001$passKey\r"
        // send it
        this.sendData(bindingString)
        this.bleDeviceData.isBindingRequired = true
    }

    fun releaseDeviceBinding(passKey: String){
        // build release binding string
        var bindingString = "6000"
        bindingString += a8bitValueTo2CharHexValue(passKey.length + 2)
        bindingString += "000$passKey\r"
        // send it
        this.sendData(bindingString)
        this.bleDeviceData.isBindingRequired = false
    }
    
    fun updateInternalSimplePropertyState(internalIndex: Int, state: Int){
        if(internalIndex < this.laRoomyDevicePropertyList.size && internalIndex >= 0){
            this.laRoomyDevicePropertyList[internalIndex].propertyState = state
        }
    }

    private fun propertyTypeFromIndex(propertyIndex: Int) : Int {
        return if(propertyIndex < this.laRoomyDevicePropertyList.size){
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).propertyType
        } else {
            Log.e("propTypeFromIndex", "Invalid index. Size of array is: ${this.laRoomyDevicePropertyList.size}. Index was: $propertyIndex")
            -1
        }
    }

    private fun checkPropertyForSimpleStateDataFromPropertyType(pType: Int) : Boolean {
        return if(pType >= COMPLEX_PROPERTY_START_INDEX){
            false
        } else {
            when(pType){
                PROPERTY_TYPE_BUTTON -> false
                PROPERTY_TYPE_SWITCH -> true
                PROPERTY_TYPE_LEVEL_SELECTOR -> true
                PROPERTY_TYPE_LEVEL_INDICATOR -> true
                PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> false
                PROPERTY_TYPE_OPTION_SELECTOR -> true
                else -> false
            }
        }
    }

    private fun onErrorFlag(c: Char){
        if(verboseLog){
            Log.e("onErrorFlag", "Transmission-error flag was set!")
        }
        applicationProperty.logControl("E: Transmission-error flag was set!")

        when(c){
            '1' -> {
                Log.e("onErrorFlag", "Error was: unknown")
                applicationProperty.logControl("E: Error was: unknown")
            }
            '2' -> {
                Log.e("onErrorFlag", "Error was: Transmission undeliverable")
                applicationProperty.logControl("E: Error was: Transmission undeliverable")
            }
            '3' -> {
                Log.e("onErrorFlag", "Error was: Index out of bounds")
                applicationProperty.logControl("E: Error was Index out of bounds")
            }
            else -> {
                Log.e("onErrorFlag", "Error was: unknown flag value")
                applicationProperty.logControl("E: Error was: Unknown flag value")
            }
        }
    }

    private fun updatePropertyElementInUIList(laRoomyDeviceProperty: LaRoomyDeviceProperty){

        this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->

            if((laRoomyDeviceProperty.propertyIndex == devicePropertyListContentInformation.internalElementIndex) && devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT){
                // NOTE: the complex state is not included in the transmission so it remains 'unset' at this point
                devicePropertyListContentInformation.elementDescriptorText = laRoomyDeviceProperty.propertyDescriptor
                devicePropertyListContentInformation.isEnabled = laRoomyDeviceProperty.isEnabled
                devicePropertyListContentInformation.isGroupMember = laRoomyDeviceProperty.isGroupMember
                devicePropertyListContentInformation.imageID = laRoomyDeviceProperty.imageID
                devicePropertyListContentInformation.internalElementIndex = laRoomyDeviceProperty.propertyIndex
                devicePropertyListContentInformation.propertyType = laRoomyDeviceProperty.propertyType
                devicePropertyListContentInformation.simplePropertyState = laRoomyDeviceProperty.propertyState

                // update the internal generated parameter and resources
                devicePropertyListContentInformation.update(applicationProperty.cViewContext)

                if(this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() != -1){
                    applicationProperty.uiAdapterChanged = true
                    devicePropertyListContentInformation.hasChanged = true
                } else {
                    this.propertyCallback.onUIAdaptableArrayItemChanged(index)
                }
                return@forEachIndexed
            }
        }
    }

    private fun updateGroupElementInUIList(laRoomyDevicePropertyGroup: LaRoomyDevicePropertyGroup){

        this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->

            if((laRoomyDevicePropertyGroup.groupIndex == devicePropertyListContentInformation.internalElementIndex) && devicePropertyListContentInformation.elementType == GROUP_ELEMENT){

                devicePropertyListContentInformation.elementDescriptorText = laRoomyDevicePropertyGroup.groupName
                devicePropertyListContentInformation.imageID = laRoomyDevicePropertyGroup.imageID

                // update the internal generated parameter and resources
                devicePropertyListContentInformation.update(applicationProperty.cViewContext)

                if(propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() != -1){
                    applicationProperty.uiAdapterChanged = true
                    devicePropertyListContentInformation.hasChanged = true
                } else {
                    this.propertyCallback.onUIAdaptableArrayItemChanged(index)
                }
                return@forEachIndexed
            }
        }
    }

    private fun updateInternalComplexPropertyStateDataAndTriggerEvent(cState: ComplexPropertyState, propertyIndex: Int){
        // update the internal property array
        if(propertyIndex < this.laRoomyDevicePropertyList.size){
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).complexPropertyState = cState
        }

        var uIElementIndex = -1

        // update the UI-Array
        this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
            if((devicePropertyListContentInformation.internalElementIndex == propertyIndex)&&(devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)){
                uIElementIndex = index
                devicePropertyListContentInformation.complexPropertyState = cState
                return@forEachIndexed
            }
        }

        // if the current open page index is equivalent to the property index, the callback must be invoked to update the UI
        if(uIElementIndex != -1){
            val complexPagePropIndex =
                propertyCallback.getCurrentOpenComplexPropPagePropertyIndex()

            if(complexPagePropIndex == propertyIndex){
                propertyCallback.onComplexPropertyStateChanged(uIElementIndex, cState)
            }
        }
    }

    fun updatePropertyStateDataNoEvent(cState: ComplexPropertyState, propertyIndex: Int){
        // update the internal property array
        if(propertyIndex < this.laRoomyDevicePropertyList.size){
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).complexPropertyState = cState
        }
        // update the UI-Array
        this.uIAdapterList.forEachIndexed { _, devicePropertyListContentInformation ->
            if((devicePropertyListContentInformation.internalElementIndex == propertyIndex)&&(devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)){
                devicePropertyListContentInformation.complexPropertyState = cState
                return@forEachIndexed
            }
        }
    }

    private fun insertPropertyElement(laRoomyDeviceProperty: LaRoomyDeviceProperty){
        if(laRoomyDeviceProperty.propertyIndex > this.laRoomyDevicePropertyList.size){
            // error out of bounds
            Log.e("insertPropertyElement", "Error: invalid index for insert operation. Index was: ${laRoomyDeviceProperty.propertyIndex}. Property-Count is ${this.laRoomyDevicePropertyList.size}")
            applicationProperty.logControl("E: Invalid index for insert operation. Index was: ${laRoomyDeviceProperty.propertyIndex}. Property-Count is ${this.laRoomyDevicePropertyList.size}")
        } else {
            if (verboseLog) {
                Log.d(
                    "insertPropertyElement",
                    "INSERT element at index: ${laRoomyDeviceProperty.propertyIndex}"
                )
            }
            applicationProperty.logControl("I: Property-Insert. INSERT element with index: ${laRoomyDeviceProperty.propertyIndex}")

            // at first check if the property must be a group member
            val precedingElementGroupIndex = when{
                (laRoomyDeviceProperty.propertyIndex <= 0) -> -1
                this.laRoomyDevicePropertyList.elementAt(laRoomyDeviceProperty.propertyIndex - 1).isGroupMember -> laRoomyDevicePropertyList.elementAt(laRoomyDeviceProperty.propertyIndex - 1).groupIndex
                else -> -1
            }
            val tailingElementGroupIndex = when{
                    //
                (laRoomyDeviceProperty.propertyIndex < 0) -> -1
                    //
                (laRoomyDeviceProperty.propertyIndex < this.laRoomyDevicePropertyList.size && this.laRoomyDevicePropertyList.elementAt(laRoomyDeviceProperty.propertyIndex).isGroupMember) -> this.laRoomyDevicePropertyList.elementAt(laRoomyDeviceProperty.propertyIndex).groupIndex
                    //
                else -> -1
            }
            if(precedingElementGroupIndex == tailingElementGroupIndex){
                // both can be -1, so make sure this is not the case
                if(precedingElementGroupIndex > -1) {
                    // the new element must be a member of that group otherwise this would be an error
                    if (laRoomyDeviceProperty.groupIndex != precedingElementGroupIndex) {
                        Log.e(
                            "insertPropertyElement",
                            "Error: The element to insert at index ${laRoomyDeviceProperty.propertyIndex} must be a member of the group with index $tailingElementGroupIndex since preceding and tailing elements are part of that group. Auto-correction fixed this!"
                        )

                        applicationProperty.logControl(
                            "E: The element to insert at index ${laRoomyDeviceProperty.propertyIndex} must be a member of the group with index $tailingElementGroupIndex since preceding and tailing elements are part of that group. Auto-correction fixed this!"
                        )
                        // auto correct the issue
                        laRoomyDeviceProperty.isGroupMember = true
                        laRoomyDeviceProperty.groupIndex = tailingElementGroupIndex
                    }
                }
            }

            // check if to be a group member makes sense, if not remove the group status!
            if((precedingElementGroupIndex == -1) && (tailingElementGroupIndex == -1) && laRoomyDeviceProperty.isGroupMember){
                Log.e("insertPropertyElement", "Inconsistency detected while inserting property: Element defined as group-member of group with index: ${laRoomyDeviceProperty.groupIndex}. This makes no sense at index: ${laRoomyDeviceProperty.propertyIndex}. Auto correction fixed this!")
                applicationProperty.logControl("W: Inconsistency detected while inserting property: Element defined as group-member of group with index: ${laRoomyDeviceProperty.groupIndex}. This makes no sense at index: ${laRoomyDeviceProperty.propertyIndex}. Auto correction fixed this!")
                laRoomyDeviceProperty.isGroupMember = false
                laRoomyDeviceProperty.groupIndex = -1
            }

            // insert in internal property list
            val insertAsLast = if(this.laRoomyDevicePropertyList.size == laRoomyDeviceProperty.propertyIndex){
                this.laRoomyDevicePropertyList.add(laRoomyDeviceProperty)

                // TODO: if the element is inserted at the end the property index cannot be found in the UIList, fix that!

                true

            } else {
                this.laRoomyDevicePropertyList.add(
                    laRoomyDeviceProperty.propertyIndex,
                    laRoomyDeviceProperty
                )
                false
            }

            // update the internal property-indexes
            this.laRoomyDevicePropertyList.forEachIndexed { index, p ->
                p.propertyIndex = index
            }

            // update device info (property-count)
            this.bleDeviceData.propertyCount++

            // define element to insert in UI-List
            val uIElementContentInformation = DevicePropertyListContentInformation(PROPERTY_ELEMENT)
            uIElementContentInformation.imageID = laRoomyDeviceProperty.imageID
            uIElementContentInformation.isEnabled = laRoomyDeviceProperty.isEnabled
            uIElementContentInformation.isGroupMember = laRoomyDeviceProperty.isGroupMember
            uIElementContentInformation.internalElementIndex = laRoomyDeviceProperty.propertyIndex// obsolete
            uIElementContentInformation.elementDescriptorText = laRoomyDeviceProperty.propertyDescriptor
            uIElementContentInformation.propertyType = laRoomyDeviceProperty.propertyType
            uIElementContentInformation.simplePropertyState = laRoomyDeviceProperty.propertyState
            // check for last element inside group
            if(laRoomyDeviceProperty.isGroupMember && (laRoomyDeviceProperty.groupIndex != tailingElementGroupIndex)){
                // the next item is not part of the group so this is the last in group
                uIElementContentInformation.isLastInGroup = true
            }
            // generate internal object resources
            uIElementContentInformation.update(applicationProperty.cViewContext)

            // find the index to insert
            var uIIndexToInsert = -1
            if(insertAsLast){
                // if the element must be inserted on the end, it cannot be found by iterating through the array, so set it here
                uIIndexToInsert = this.uIAdapterList.size
            } else {
                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                    if ((devicePropertyListContentInformation.internalElementIndex == laRoomyDeviceProperty.propertyIndex) && (devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)) {
                        uIIndexToInsert =
                            if ((index > 0) && (this.uIAdapterList.elementAt(index - 1).elementType == GROUP_ELEMENT)) {
                                // if the shifted item is inside group, insert it before the group-header, but only if the new item is NOT part of this group
                                if (uIElementContentInformation.isGroupMember && devicePropertyListContentInformation.isGroupMember
                                    && (laRoomyDeviceProperty.groupIndex == this.laRoomyDevicePropertyList.elementAt(
                                        devicePropertyListContentInformation.internalElementIndex
                                    ).groupIndex)
                                ) {
                                    // insert inside the group as a member
                                    index
                                } else {
                                    // insert before group header
                                    index - 1
                                }
                            } else {
                                index
                            }
                        return@forEachIndexed
                    }
                }
            }

            // insert it if applicable
            if(uIIndexToInsert != -1){
                this.uIAdapterList.add(uIIndexToInsert, uIElementContentInformation)

                // reorder internal and global indexes in UI-List
                var newInternalIndex = 0
                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                    devicePropertyListContentInformation.globalIndex = index
                    if(devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT){
                        devicePropertyListContentInformation.internalElementIndex = newInternalIndex
                        newInternalIndex++
                    }
                }

                // launch event or invalidate adapter
                if(this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() != -1){
                    applicationProperty.uiAdapterInvalidatedOnPropertySubPage = true
                } else {
                    // if this is a group member, and if it is the last in the group, the previous item is no longer the last in group, so it must be changed
                    if (uIIndexToInsert > 0) {
                        val previousElement =
                            this.uIAdapterList.elementAt(uIIndexToInsert - 1)
                        if (previousElement.isGroupMember && previousElement.isLastInGroup && (previousElement.elementType == PROPERTY_ELEMENT)) {
                            this.uIAdapterList[uIIndexToInsert - 1].isLastInGroup = false
                            this.propertyCallback.onUIAdaptableArrayItemChanged(uIIndexToInsert - 1)
                        }
                    }
                    // notify the insertion
                    this.propertyCallback.onUIAdaptableArrayItemInserted(uIIndexToInsert)
                }
                // if this was a complex property, send a state request
                if(uIElementContentInformation.propertyType >= COMPLEX_PROPERTY_START_INDEX){
                    this.sendComplexPropertyStateRequest(uIElementContentInformation.internalElementIndex)
                }
            } else {
                Log.e("insertPropertyElement", "Critical error: index to insert not found in UI-List!")
            }
        }
    }

    private fun removePropertyElement(pIndex: Int){
        if(pIndex >= this.laRoomyDevicePropertyList.size){
            // error out of bounds
            Log.e("removePropertyElement", "Error: invalid index for remove operation. Index was $pIndex. Property-Count is ${this.laRoomyDevicePropertyList.size}")
            applicationProperty.logControl("E: Invalid index for remove operation. Index was $pIndex. Property-Count is ${this.laRoomyDevicePropertyList.size}")
        } else {
            if (verboseLog) {
                Log.d(
                    "removePropertyElement",
                    "REMOVE element with index: $pIndex"
                )
            }
            applicationProperty.logControl("I: Property-Remove. REMOVE element with index: $pIndex")

            try {
                // necessary information
                val isGroupMember = this.laRoomyDevicePropertyList.elementAt(pIndex).isGroupMember
                var isLastInGroup = false

                // delete property element
                this.laRoomyDevicePropertyList.removeAt(pIndex)
                // reorder the indexes of the property list
                this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
                    laRoomyDeviceProperty.propertyIndex = index
                }

                // update device info (prop-count)
                this.bleDeviceData.propertyCount--

                // delete element in UI-List
                var uIIndexToDelete = -1
                var newPropertyIndex = 0

                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                    // search for the real index in the UI-Array
                    if (devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT) {
                        if (devicePropertyListContentInformation.internalElementIndex == pIndex) {
                            isLastInGroup = devicePropertyListContentInformation.isLastInGroup
                            uIIndexToDelete = index
                        } else {
                            // reorder the internal index of the property elements
                            devicePropertyListContentInformation.internalElementIndex =
                                newPropertyIndex
                            newPropertyIndex++
                        }
                    }
                }
                // if the element exists, remove it
                if (uIIndexToDelete != -1) {
                    // get indication if this occurred on deviceMainActivity or not
                    val isOtherThanDeviceMain =
                        this.isOtherThanDeviceMainActivity()

                    // remove the UI-Item
                    this.uIAdapterList.removeAt(uIIndexToDelete)

                    // notify the deviceMainActivity or if not open, invalidate the adapter
                    if (isOtherThanDeviceMain) {
                        applicationProperty.uiAdapterInvalidatedOnPropertySubPage = true
                    } else {
                        this.propertyCallback.onUIAdaptableArrayItemRemoved(uIIndexToDelete)
                    }

                    if (isGroupMember && isLastInGroup) {
                        // - when it is not the last in group, the other items can remain unchanged
                        // - when it is the last in group, then the previous item must be changed >>
                        // - except, the item was the only item in group, then the group must be removed

                        // make sure the index is in range
                        if (uIIndexToDelete > 0) {
                            if (this.uIAdapterList.elementAt(uIIndexToDelete - 1).elementType == GROUP_ELEMENT) {
                                // the previous element is the group header and the removed item was the last in group, so the group consists of only one item, this makes the group empty, so remove it too!
                                this.removeGroupElement(
                                    this.uIAdapterList.elementAt(uIIndexToDelete - 1).internalElementIndex,
                                    false
                                )
                            } else {
                                // the previous element is a property element, check if it is part of the group (must be?) and set isGroupMember to true, then launch changed event
                                this.uIAdapterList.elementAt(uIIndexToDelete - 1).isLastInGroup =
                                    true
                                if (!isOtherThanDeviceMain) {
                                    this.propertyCallback.onUIAdaptableArrayItemChanged(
                                        uIIndexToDelete - 1
                                    )
                                }
                            }
                        }
                    }
                    // finally bring the global indexes in the right order
                    this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                        devicePropertyListContentInformation.globalIndex = index
                    }
                }
            } catch (e: Exception) {
                Log.e("removePropertyElement", "Exception: $e")
            }
        }
    }

    private fun removeGroupElement(pIndex: Int, rearrangeGlobalIndexes: Boolean){
        if(pIndex >= this.laRoomyPropertyGroupList.size){
            // error out of bounds
            Log.e("removeGroupElement", "Error: invalid index for removing group. Index was $pIndex. Group-Count is ${this.laRoomyPropertyGroupList.size}")
            applicationProperty.logControl("E: Invalid index for removing group. Index was $pIndex. Group-count is ${this.laRoomyPropertyGroupList.size}")
        } else {
            if (verboseLog) {
                Log.d(
                    "removeGroupElement",
                    "REMOVE element with index: $pIndex"
                )
            }
            applicationProperty.logControl("I: Group-Remove. REMOVE element with index: $pIndex")

            try {
                // delete group element
                this.laRoomyPropertyGroupList.removeAt(pIndex)
                // re-order the indexes of the group list
                this.laRoomyPropertyGroupList.forEachIndexed { index, laRoomyDevicePropertyGroup ->
                    laRoomyDevicePropertyGroup.groupIndex = index
                }
                // update device info (group-count)
                this.bleDeviceData.groupCount--

                // delete elements in UI-List
                var uIIndexToDelete = -1
                var newGroupIndex = 0

                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                    // search group element
                    if (devicePropertyListContentInformation.elementType == GROUP_ELEMENT) {
                        if (devicePropertyListContentInformation.internalElementIndex == pIndex) {
                            uIIndexToDelete = index
                        } else {
                            // re-order the internal element-indexes of the groups
                            devicePropertyListContentInformation.internalElementIndex =
                                newGroupIndex
                            newGroupIndex++
                        }
                    }
                }
                // if found, remove it and launch event
                if (uIIndexToDelete != -1) {
                    // get indication if this occurred on deviceMainActivity or not
                    val isOtherThanDeviceMain =
                        this.isOtherThanDeviceMainActivity()

                    // delete UI-Element
                    this.uIAdapterList.removeAt(uIIndexToDelete)

                    // trigger event or invalidate adapter, in relation to the open activity
                    if (isOtherThanDeviceMain) {
                        applicationProperty.uiAdapterInvalidatedOnPropertySubPage = true
                    } else {
                        this.propertyCallback.onUIAdaptableArrayItemRemoved(uIIndexToDelete)
                    }

                    // check if the group had properties and remove them too:
                    val uIIndexList = ArrayList<Int>()
                    val propListIndexes = ArrayList<Int>()

                    for (i in uIIndexToDelete until this.uIAdapterList.size) {
                        if (this.uIAdapterList.elementAt(i).isGroupMember) {
                            uIIndexList.add(i)
                            propListIndexes.add(this.uIAdapterList.elementAt(i).internalElementIndex)
                        } else {
                            break
                        }
                    }
                    // delete the properties in UI-List
                    for (i in (uIIndexList.size - 1) downTo 0) {
                        // delete element
                        this.uIAdapterList.removeAt(i)
                        // only launch event if the receiver(deviceMainActivity) is open
                        if (!isOtherThanDeviceMain) {
                            this.propertyCallback.onUIAdaptableArrayItemRemoved(i)
                        }
                    }
                    // re-order the internal and global indexes in UI-List
                    var newInternalIndex = 0
                    this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                        if (rearrangeGlobalIndexes) {
                            devicePropertyListContentInformation.globalIndex = index
                        }
                        if (devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT) {
                            devicePropertyListContentInformation.internalElementIndex =
                                newInternalIndex
                            newInternalIndex++
                        }
                    }

                    // delete the properties in the prop-list
                    if (propListIndexes.isNotEmpty()) {
                        for (i in (propListIndexes.size - 1) downTo 0) {
                            this.laRoomyDevicePropertyList.removeAt(i)
                        }
                        // reorder the indexes
                        this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
                            laRoomyDeviceProperty.propertyIndex = index
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("removeGroupElement", "Exception: $e")
            }
        }
    }

    private fun enablePropertyElement(propertyIndex: Int, enable: Boolean){

        var wasFound = false

        // at first update the element in the internal property list
        this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
            if(index == propertyIndex){
                laRoomyDeviceProperty.isEnabled = enable
                wasFound = true
                return@forEachIndexed
            }
        }
        if(wasFound){
            // update the parameter in the ui-list
            this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
                if((devicePropertyListContentInformation.internalElementIndex == propertyIndex)&&(devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)){
                    devicePropertyListContentInformation.isEnabled = enable

                    // launch event if device main page is open or mark as changed for later update
                    if(this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() != -1){
                        applicationProperty.uiAdapterChanged = true
                        devicePropertyListContentInformation.hasChanged = true
                    } else {
                        this.propertyCallback.onUIAdaptableArrayItemChanged(index)
                    }
                    return@forEachIndexed
                }
            }
        } else {
            Log.e("enablePropertyElement", "Error: Request to update property element with index $propertyIndex failed. Index not found!")
            applicationProperty.logControl("E: Request to update property element with index -$propertyIndex- failed. Index not found!")
        }
    }

    private fun startLoopTimeoutWatcher(loopType: Int){

        timeoutWatcherData.isStarted = true
        timeoutWatcherData.timeoutFlag = true
        timeoutWatcherData.loopType = loopType

        if(verboseLog){
            Log.d("StartTimeOutWatcher", "Loop-Timeout Watcher started..")
        }

        this.timeoutTimer = Timer()

        this.timeoutTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {

                    // first check if loops are running, if not, stop the timer
                    if(!complexStateLoopActive && !propertyLoopActive && !groupLoopActive && !simpleStateLoopActive){
                        if(verboseLog){
                            Log.w("LoopTimeoutWatcher", "All loops are offline. Execute self reset. Loop-type was: ${loopTypeToString(timeoutWatcherData.loopType)}")
                        }
                        timeoutWatcherData.clear()
                        cancel()
                    } else {
                        if (timeoutWatcherData.timeoutFlag) {
                            // the flag was not reset during the reception of a response, there must be a problem
                            if(timeoutWatcherData.loopType == LOOPTYPE_NONE){
                                // unexpected loop-type
                                Log.e("LoopTimeoutWatcher", "Unexpected Loop-Type: None. Cancel watcher!")
                                applicationProperty.logControl("E: Unexpected Loop-Type: None. Cancel watcher!")
                                // clear watcher data
                                timeoutWatcherData.clear()
                                cancel()
                            } else {
                                if (timeoutWatcherData.loopRepeatCounter < 4) {
                                    if (verboseLog) {
                                        Log.w(
                                            "LoopTimeoutWatcher",
                                            "Timeout occurred in ${loopTypeToString(timeoutWatcherData.loopType)} loop - restarting loop. Repeat-Counter is: ${timeoutWatcherData.loopRepeatCounter}"
                                        )
                                    }
                                    applicationProperty.logControl("W: Timeout occurred in ${loopTypeToString(timeoutWatcherData.loopType)} loop - restarting loop. Repeat-Counter is: ${timeoutWatcherData.loopRepeatCounter}")
                                    
                                    // reset fragmented transmission data
                                    resetFragmentedTransmissionData(timeoutWatcherData.loopType)
    
                                    // increase repeat counter
                                    timeoutWatcherData.loopRepeatCounter++
    
                                    // restart loop from loop-type
                                    when(timeoutWatcherData.loopType){
                                        LOOPTYPE_SIMPLESTATE -> {
                                            cancel()
                                            startSimpleStateDataLoop()
                                        }
                                        LOOPTYPE_COMPLEXSTATE -> {
                                            cancel()
                                            startComplexStateDataLoop()
                                        }
                                        LOOPTYPE_PROPERTY -> {
                                            cancel()
    
                                            //clearPropertyRelatedParameterAndStopAllLoops()
                                            //startPropertyListing(invokeCallbackLoopAfterUIDataGeneration)
    
                                            // TODO: reload, not start!!!!!!!!
    
                                            reloadProperties()
    
                                        }
                                        LOOPTYPE_GROUP -> {
                                            cancel()
                                            laRoomyPropertyGroupList.clear()
                                            startGroupListing()
                                        }
                                    }
                                } else {
                                    // loop could not be finalized
                                    Log.e("LoopTimeoutWatcher", "Loop-Timeout occurred 3 times! - Device not responding! Loop-Type was: ${loopTypeToString(timeoutWatcherData.loopType)}")
                                    applicationProperty.logControl("E: Loop-Timeout occurred 3 times! - Device not responding! Loop-Type was: ${loopTypeToString(timeoutWatcherData.loopType)}")
    
                                    // reset loop param
                                    when(timeoutWatcherData.loopType) {
                                        LOOPTYPE_SIMPLESTATE -> {
                                            simpleStateLoopActive = false
                                        }
                                        LOOPTYPE_COMPLEXSTATE -> {
                                            complexStateLoopActive = false
                                        }
                                        LOOPTYPE_PROPERTY -> {
                                            propertyLoopActive = false
                                        }
                                        LOOPTYPE_GROUP -> {
                                            groupLoopActive = false
                                        }
                                    }
    
                                    // clear watcher data
                                    timeoutWatcherData.clear()
    
                                    // raise event
                                    callback.onConnectionError(
                                        BLE_CONNECTION_MANAGER_CRITICAL_DEVICE_NOT_RESPONDING
                                    )
    
                                    cancel()
                                }
                            }
                        } else {
                            if(verboseLog){
                                Log.d("LoopTimeoutWatcher", "Timeout condition was checked. Everything fine..")
                            }
                            // set the flag to true (if it is still true on the next execution, there must be a problem)
                            timeoutWatcherData.timeoutFlag = true
                        }
                    }
                }
            }, (0).toLong(), (1000).toLong()
        )
    }

    private fun notifyLoopIsWorking(currentIndex: Int){
        if(this.timeoutWatcherData.isStarted){
            // save index
            this.timeoutWatcherData.currentIndex = currentIndex
            // reset the timeout flag
            this.timeoutWatcherData.timeoutFlag = false
        } else {
            if(verboseLog) {
                Log.d(
                    "NotifyTimeoutWatcher",
                    "NotifyTimeoutWatcher was invoked. No loop active. Action was discarded."
                )
            }
        }
    }

    private fun stopLoopTimeoutWatcher(){
        if(this.timeoutWatcherData.isStarted) {
            if (verboseLog) {
                Log.d("StopTimeOutWatcher", "Loop-Timeout Watcher stopped! Loop-Type was ${loopTypeToString(timeoutWatcherData.loopType)}")
            }
            this.timeoutWatcherData.clear()
            this.timeoutTimer.cancel()
        }
    }

    private fun isOtherThanDeviceMainActivity() : Boolean {
        return (this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex() != -1)
    }
    
    private fun handleForcedBackNavigation(){
        if(this.isOtherThanDeviceMainActivity()){
            if (verboseLog) {
                Log.e("ForcedBackNavigation", "Unexpected: Forced back navigation was requested during a stand-alone-property session. Notification will be forwarded, but should have no effect.")
            }
            applicationProperty.logControl("W: Unexpected: Forced back navigation was requested during a stand-alone-property session. Notification will be forwarded, but should have no effect. To navigate back in stand-alone mode: close the device.")
            
            this.propertyCallback.onRemoteBackNavigationRequested()
        } else {
            if(verboseLog){
                Log.e("ForcedBackNavigation", "Unexpected: back-navigation notification received in DeviceMainActivity.")
            }
            applicationProperty.logControl("W: Unexpected: back-navigation notification received in DeviceMainActivity. This only works on complex property pages. To navigate back on Device-Main-Page: close the device.")
        }
    }
    
    private fun sendDeviceReconnectedNotification(){
        val currentPageIndex = this.propertyCallback.getCurrentOpenComplexPropPagePropertyIndex()
        val dataToSend = "${this.deviceReconnectedNotificationEntry}${a8bitValueTo2CharHexValue(currentPageIndex)}\r"
        this.sendData(dataToSend)
    }
    
    private fun resetFragmentedTransmissionData(loopType: Int = -1){
        when(loopType){
            LOOPTYPE_PROPERTY -> {
                // this is a property loop = char a
                for(i in this.openFragmentedTransmissionData.lastIndex downTo 0){
                    if(this.openFragmentedTransmissionData.elementAt(i).transmissionString.elementAt(0) == 'a'){
                        this.openFragmentedTransmissionData.removeAt(i)
                    }
                }
            }
            LOOPTYPE_GROUP -> {
                // this is a group loop = char b
                for(i in this.openFragmentedTransmissionData.lastIndex downTo 0){
                    if(this.openFragmentedTransmissionData.elementAt(i).transmissionString.elementAt(0) == 'b'){
                        this.openFragmentedTransmissionData.removeAt(i)
                    }
                }
            }
            LOOPTYPE_SIMPLESTATE -> {
                // this is a simple state loop, we cannot distinguish between simple and complex
                for(i in this.openFragmentedTransmissionData.lastIndex downTo 0){
                    if(this.openFragmentedTransmissionData.elementAt(i).transmissionString.elementAt(0) == 'c'){
                        this.openFragmentedTransmissionData.removeAt(i)
                    }
                }
            }
            LOOPTYPE_COMPLEXSTATE -> {
                // this is a complex state loop, we cannot distinguish between simple and complex
                for(i in this.openFragmentedTransmissionData.lastIndex downTo 0){
                    if(this.openFragmentedTransmissionData.elementAt(i).transmissionString.elementAt(0) == 'c'){
                        this.openFragmentedTransmissionData.removeAt(i)
                    }
                }
            }
            else -> {
                // otherwise delete all data
                this.openFragmentedTransmissionData.clear()
            }
        }
    }
    
    private fun cleanUpOnDispatchError(){
        this.openFragmentedTransmissionData.clear()
    }
    
    private fun startRssiReadTimer(){
        try {
            this.rssiReadTimer = Timer()
            this.rssiReadTimer.scheduleAtFixedRate(object : TimerTask() {
                @SuppressLint("MissingPermission")
                override fun run() {
                    bluetoothGatt?.readRemoteRssi()
                }
            }, (2000).toLong(), (2000).toLong())
            
            Log.d("BLEConnectionManager", "M:startRssiReadTimer: RSSI Timer started")
            
        } catch (e: Exception){
            Log.e("BLEConnectionManager", "M:startRssiReadTimer error: $e")
        }
    }
    
    private fun stopRssiReadTimer(){
        try {
            this.rssiReadTimer.cancel()
            Log.d("BLEConnectionManager", "M:stopRssiTimer: RSSI Timer stopped!")
        } catch (e: Exception){
            Log.e("BLEConnectionManager", "M:stopRssiTimer error: $e")
        }
    }

    // the callback definition for the event handling in the calling class
    interface BleEventCallback : Serializable{
        fun onConnectionStateChanged(state: Boolean){}
        fun onDataReceived(data: String?){}
        fun onConnectionError(errorID: Int){}
        fun onConnectionEvent(eventID: Int){}
        fun onInitializationSuccessful(){}
        fun onBindingPasskeyRejected(){}
        fun onDeviceReadyForCommunication(){}
        fun onRssiValueRead(rssi: Int){}
    }

    interface PropertyCallback: Serializable {
        fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>){}
        fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation){}
        fun onUIAdaptableArrayItemChanged(index: Int){}
        fun onUIAdaptableArrayItemInserted(index: Int){}
        fun onUIAdaptableArrayItemRemoved(index: Int){}
        fun onStandAlonePropertyModePreparationComplete(){}
        fun onPropertyInvalidated(){}
        fun onRemoteBackNavigationRequested(){}
        fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int){}
        fun onComplexPropertyStateChanged(UIAdapterElementIndex: Int, newState: ComplexPropertyState){}
        fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData){}
        fun onFastDataPipeInvoked(propertyID: Int, data: String){}
        fun onBindingResponse(responseID: Int){}
        fun getCurrentOpenComplexPropPagePropertyIndex() : Int {
            // NOTE: if overwritten, do not return the super method!
            return -1
        }
    }
}