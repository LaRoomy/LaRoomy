package com.laroomysoft.laroomy

import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.app.Activity
import android.bluetooth.*
import android.os.*
import android.util.Log
import java.io.Serializable
import java.lang.IndexOutOfBoundsException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

//private const val MAX_CONNECTION_ATTEMPTS = 10

//const val UNKNOWN_DEVICETYPE = 0
//const val LAROOMYDEVICETYPE_XNG = 1
//const val LAROOMYDEVICETYPE_CTX = 2
//const val LAROOMYDEVICETYPE_TVM = 3

//const val UPDATE_TYPE_ELEMENT_DEFINITION = 1
//const val UPDATE_TYPE_DETAIL_DEFINITION = 2

const val BINDING_RESPONSE_BINDING_NOT_SUPPORTED = 1
const val BINDING_RESPONSE_BINDING_SUCCESS = 2
const val BINDING_RESPONSE_BINDING_ERROR = 3
const val BINDING_RESPONSE_RELEASE_BINDING_FAILED_WRONG_PASSKEY = 4
const val BINDING_RESPONSE_RELEASE_BINDING_FAILED_UNKNOWN_ERROR = 5
const val BINDING_RESPONSE_RELEASE_BINDING_SUCCESS = 6

const val BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE = 1
const val BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE = 2
const val BLE_UNEXPECTED_BLUETOOTH_DEVICE_NOT_CONNECTED = 3
const val BLE_UNEXPECTED_CRITICAL_BINDING_KEY_MISSING = 4

class BLEConnectionManager(private val applicationProperty: ApplicationProperty) {

    private val clientCharacteristicConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // static notifications
    private val propertyLoadingCompleteNotification = "5000030010\r"         // sent when the retrieving of the properties is complete (raw loading - not from cache)
    private val deviceReconnectedNotification = "500002002\r"                // sent when the connection was suspended (user has left the app) and the user re-invoked the app
    private val userNavigatedBackToDeviceMainNotification = "500002004\r"    // sent when the user has opened a complex property page and navigated back to device-main-page
    private val propertyLoadedFromCacheCompleteNotification = "5000030011\r" // sent when the properties and groups are loaded from cache and the operation is complete

    // data holder objects
    private val bleDeviceData = BLEDeviceData()
    private val timeoutWatcherData = LoopTimeoutWatcherData()

    // timer
    private lateinit var timeoutTimer: Timer


    var isConnected:Boolean = false
        private set

    val initializationSuccess : Boolean
        get() = this.bleDeviceData.authenticationSuccess

    var isBindingRequired = false
        private set

    var connectionSuspended = false
        private set

    //var isCurrentConnectionDoneWithSharedBindingKey = false

    private var propertyLoopActive = false
    private var groupLoopActive = false
    private var isResumeConnectionAttempt = false
    private var invokeCallbackLoopAfterUIDataGeneration = false

    private var suspendedDeviceAddress = ""


    // parameter regarding the complex state loop
    private var currentComplexStateRetrievingIndex = -1 // initialize with invalid marker
    private var complexStateLoopActive = false
    private var complexStatePropertyIndexes = ArrayList<Int>()


    // Callbacks
    private lateinit var callback: BleEventCallback
    private lateinit var propertyCallback: PropertyCallback

    // bluetooth system objects
    var currentDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    lateinit var gattCharacteristic: BluetoothGattCharacteristic
    // --
    private val bondedList: Set<BluetoothDevice>? by lazy(LazyThreadSafetyMode.NONE){
        this.bleAdapter?.bondedDevices
    }
    // --
    private val bleAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE){
        val bluetoothManager =
            applicationProperty.applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }


    // device-property objects
    var laRoomyDevicePropertyList = ArrayList<LaRoomyDeviceProperty>()
    var laRoomyPropertyGroupList = ArrayList<LaRoomyDevicePropertyGroup>()
    var uIAdapterList = ArrayList<DevicePropertyListContentInformation>()

    var currentUsedServiceUUID = ""
        // used to cache the current service uuid
        private set

    val currentUsedCharacteristicUUID: String
        get() = this.gattCharacteristic.uuid.toString()

    val isLastAddressValid: Boolean
        get() = (this.getLastConnectedDeviceAddress().isNotEmpty())

    val isConnectionDoneWithSharedKey: Boolean
    get() = (this.bleDeviceData.passKeyTypeUsed == PASSKEY_TYPE_SHARED)


    var bondedLaRoomyDevices = ArrayList<LaRoomyDevicePresentationModel>()
        get() {
            // clear the list:
            field.clear()

            // add the data
            this.bleAdapter?.bondedDevices?.forEach {
                val device = LaRoomyDevicePresentationModel()
                device.name = it.name
                device.address = it.address
                device.image = 0

                field.add(device)
            }
            return field
        }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val requestEnableBT: Int = 13

    private var uIItemAddCounter = -1


    // callback implementation for BluetoothGatt
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            when(newState){
                BluetoothProfile.STATE_CONNECTED -> {

                    // if successful connected reset the device-data
                    //bleDeviceData.clear()

                    isConnected = true

                    if(verboseLog) {
                        Log.d("M:CB:ConStateChanged", "Connection-state changed to: CONNECTED")
                    }
                    callback.onConnectionStateChanged(true)

                    if(!isResumeConnectionAttempt) {
                        if(verboseLog) {
                            Log.d(
                                "M:CB:ConStateChanged",
                                "This is no resume action, so discover services"
                            )
                            Log.d("M:CB:ConStateChanged", "Invoking discoverServices()")
                        }
                        applicationProperty.logControl("I: Starting to discover Services")

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
                        val descriptor = gattCharacteristic.getDescriptor(clientCharacteristicConfig)
                            .apply {
                                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            }
                        gatt?.writeDescriptor(descriptor)

                        // notify the remote device
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendData(deviceReconnectedNotification)
                        }, 200)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {

                    isConnected = false

                    if(verboseLog) {
                        Log.d("M:CB:ConStateChanged", "Connection-state changed to: DISCONNECTED")
                    }
                    callback.onConnectionStateChanged(false)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            when(status){
                BluetoothGatt.GATT_SUCCESS -> {
                    // iterate throughout the services and display it in the debug-log
                    gatt?.services?.forEachIndexed { index, bluetoothGattService ->

                        if(verboseLog) {
                            Log.d(
                                "M:CB:onServicesDisc",
                                "Service discovered. Index: $index ServiceUUID: ${bluetoothGattService.uuid} Type: ${bluetoothGattService.type}"
                            )
                        }
                        applicationProperty.logControl("I: Service discovered. UUID: ${bluetoothGattService.uuid}")

                        // look if the service matches an uuid profile
                        val profileIndex = applicationProperty.uuidManager.profileIndexFromServiceUUID(bluetoothGattService.uuid)

                        if(profileIndex != -1)
                        {
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:onServicesDisc",
                                    "Correct service found - retrieving characteristics for the service"
                                )
                            }
                            applicationProperty.logControl("I: This service is found in UUID Profile")
                            applicationProperty.logControl("I: Perform lookup for Characteristic UUID")

                            // cache the service uuid
                            currentUsedServiceUUID = bluetoothGattService.uuid.toString()

                            // iterate through the characteristics in the service
                            bluetoothGattService.characteristics.forEach {

                                if(verboseLog) {
                                    Log.d(
                                        "M:CB:onServicesDisc",
                                        "Characteristic found: UUID: ${it.uuid}  InstanceID: ${it.instanceId}"
                                    )
                                }
                                applicationProperty.logControl("I: Characteristic in Service found. UUID: ${it.uuid}")

                                // set the characteristic notification for the desired characteristic in the service
                                if(it.uuid == applicationProperty.uuidManager.uUIDProfileList.elementAt(profileIndex).characteristicUUID) {

                                    if(verboseLog) {
                                        Log.d(
                                            "M:CB:onServicesDisc",
                                            "Correct characteristic found - enable notifications"
                                        )
                                    }
                                    applicationProperty.logControl("I: Characteristic match: enable notifications")

                                    // save characteristic
                                    gattCharacteristic = it
                                    //gattCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED//WRITE_TYPE_NO_RESPONSE
                                    // enable notification on the device
                                    gatt.setCharacteristicNotification(gattCharacteristic, true)

                                    if(verboseLog) {
                                        Log.d("M:CB:onServicesDisc", "Set Descriptor")
                                    }

                                    // set the correct descriptor for this characteristic
                                    val descriptor = gattCharacteristic.getDescriptor(clientCharacteristicConfig)
                                        .apply {
                                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        }
                                    gatt.writeDescriptor(descriptor)

                                    // report object ready
                                    callback.onDeviceReadyForCommunication()
                                }
                            }
                        }
                    }
                }
                else -> {
                    Log.e("M:CB:onServicesDisc", "Gatt-Status: $status")
                    applicationProperty.logControl("E: Unexpected Gatt-Status: $status")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            // get data
            val dataAsString = characteristic?.getStringValue(0)

            // log (string)
            if(verboseLog) {
                Log.d("M:CB:CharChanged", "Characteristic changed. String-Value: $dataAsString")
            }
            applicationProperty.logControl("I: Characteristic changed: $dataAsString")

            try {
                if (!dispatchTransmission(dataAsString ?: "")) {
                    callback.onDataReceived(dataAsString ?: "")
                }
            } catch (e: Exception) {
                if (verboseLog) {
                    Log.e(
                        "onCharacteristicChanged",
                        "Exception while dispatching the incoming transmission. Exception: $e / Message: ${e.message}"
                    )
                }
                applicationProperty.logControl("E: Exception while dispatching the incoming transmission. Exception: $e / Message: ${e.message}")
            }
        }
    }

    fun dispatchTransmission(data: String): Boolean {
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
            var strDataSize = "0x"
            strDataSize += data[4]
            strDataSize += data[5]

            // convert to integer
            var dataSize =
                Integer.decode(strDataSize)
            // add the header length
            dataSize += 8

            // check error flag
            if(data[6] != '0'){
                this.onErrorFlag(data[6])
            }

            var dataProcessed = false

            // evaluate transmission type
            when(data[0]){
                '1' -> {
                    // property definition data
                    dataProcessed = readPropertyString(data, dataSize)
                }
                '2' -> {
                    // group definition data
                    dataProcessed = readGroupString(data, dataSize)
                }
                '3' -> {
                    // property state data
                    dataProcessed = dispatchPropertyStateTransmission(data, dataSize)
                }
                '4' -> {
                    // property execution command (only response)
                    dataProcessed = readPropertyExecutionResponse(data, dataSize)
                }
                '5' -> {
                    // device to app notification
                    dataProcessed = readDeviceNotification(data, dataSize)
                }
                '6' -> {
                    // binding response
                    dataProcessed = readBindingResponse(data, dataSize)
                }
                '7' -> {
                    // init transmission string
                    dataProcessed = readInitTransmission(data, dataSize)
                }
                '8' -> {
                    // fast data setter
                    dataProcessed = readFastDataSetterTransmission(data, dataSize)
                }
            }
            // return true if the data is fully handled, otherwise it will be forwarded to the callback
            return dataProcessed
        }
    }

    private fun readDeviceNotification(data: String, dataSize: Int) : Boolean {

        if(dataSize < 9){
            //invalid transmission length
            if(verboseLog){
                Log.e("readDevNotification", "Invalid transmission length. Transmission-length was: ${data.length} > minimum transmission-length is 9!")
            }
            applicationProperty.logControl("E: Invalid transmission length. Transmission-length was: ${data.length} > minimum transmission-length is 9!")
            return false
        } else {
            // get ID

            var hexString = "0x"
            hexString += data[2]
            hexString += data[3]
            val elementIndex =
                Integer.decode(hexString)

            when(data[8]){
                '1' -> {
                    // user-message notification
                    this.handleUserMessage(data, dataSize)
                }
                '2' -> {
                    // time request notification from device
                    this.handleTimeRequest()
                }
                '3' -> {
                    // property invalidated notification
                    this.propertyCallback.onPropertyInvalidated()
                }
                '4' -> {
                    // save properties to cache notification
                    this.savePropertyDataToCacheIfPermitted()
                }
                '5' -> {

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

    private fun handleUserMessage(data: String, dataSize: Int){

        // check transmission length
        if(dataSize > 9) {
            val deviceInfoHeaderData = DeviceInfoHeaderData()

            // get image id (transferred in the id fields)
            var hexString = "0x"
            hexString += data[2]
            hexString += data[3]

            deviceInfoHeaderData.imageID = Integer.decode(hexString)
            deviceInfoHeaderData.type = data[9]
            deviceInfoHeaderData.displayTime = data[10].toString().toLong()
            deviceInfoHeaderData.message = data.removeRange(0, 11)

            this.propertyCallback.onRemoteUserMessage(deviceInfoHeaderData)
        } else {
            // no notification data
            // TODO! error message
        }
    }

    private fun handleTimeRequest(){
        // get the time and send the client command to the device
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)// 24 hour format!
        val min = calendar.get(Calendar.MINUTE)
        val sec = calendar.get(Calendar.SECOND)

        var timeRequestResponse = "520008005"
        timeRequestResponse += a8bitValueTo2CharHexValue(hour)
        timeRequestResponse += a8bitValueTo2CharHexValue(min)
        timeRequestResponse += a8bitValueTo2CharHexValue(sec)
        timeRequestResponse += '\r'
        this.sendData(timeRequestResponse)
    }

    private fun readPropertyExecutionResponse(data: String, dataSize: Int) : Boolean {
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

    private fun readSimpleStateData(propertyIndex: Int, data: String, dataSize: Int){
        if(dataSize < 10){
            // invalid data size
            if(verboseLog){
                Log.e("readSimpleStateData", "Invalid transmission size. Data-size was: $dataSize. Minimum is: 10")
            }
            applicationProperty.logControl("E: Invalid simple-state transmission size. Data-size was: $dataSize. Minimum is: 10")
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
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> {
                this.processBarGraphData(propertyIndex, data)
            }
        }
        // check if the loop is active and send the next request if necessary
        if(this.complexStateLoopActive){

            // increase retrieving index
            this.currentComplexStateRetrievingIndex++

            // check if the loop is finished
            if(this.currentComplexStateRetrievingIndex >= this.complexStatePropertyIndexes.size){
                val finalCount = this.currentComplexStateRetrievingIndex

                // the complex state data loop is finished
                this.currentComplexStateRetrievingIndex = -1
                this.complexStatePropertyIndexes.clear()
                this.complexStateLoopActive = false
                this.stopLoopTimeoutWatcher()

                if(verboseLog){
                    Log.d("readComplexStateData", "Complex property state loop finished: Count is: $finalCount")
                }
                applicationProperty.logControl("I: Complex property state loop finished: Count is: $finalCount")
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

                // TODO: renew the timeout, to detect if the device is responding
            }
        }
    }

    private fun readGroupString(data: String, dataSize: Int) : Boolean {

        // check if the length of the transmission is valid
        if(dataSize < 12){
            // invalid data size
            if(verboseLog){
                Log.e("readGroupString", "Invalid data size. Data-size was: $dataSize - minimum is 12!")
            }
            applicationProperty.logControl("E: Invalid data size. Data-size was: $dataSize - minimum is 12!")
            return false
        } else {
            // decode group string to class
            val laRoomyDevicePropertyGroup = LaRoomyDevicePropertyGroup()
            laRoomyDevicePropertyGroup.fromString(data)

            // add group and request next group if the loop is active
            if(this.groupLoopActive){
                this.laRoomyPropertyGroupList.add(laRoomyDevicePropertyGroup)
                this.sendNextGroupRequest(laRoomyDevicePropertyGroup.groupIndex)
            } else {
                // if the loop is not active this must be an update-transmission, so replace the group
                if(this.laRoomyPropertyGroupList.size > laRoomyDevicePropertyGroup.groupIndex){
                    this.laRoomyPropertyGroupList[laRoomyDevicePropertyGroup.groupIndex] =
                        laRoomyDevicePropertyGroup
                }
                // update UI
                this.updateGroupElementInUIList(laRoomyDevicePropertyGroup)
            }
            return true
        }
    }

    private fun readPropertyString(data: String, dataSize: Int) : Boolean {

        // check if the length of the transmission is valid
        if(dataSize < 18){
            //invalid data size
            if(verboseLog){
                Log.e("readPropertyString", "Invalid data size. Data-Size was: $dataSize > minimum is 18!")
            }
            applicationProperty.logControl("E: Invalid data size of property transmission. Data-Size was: $dataSize > minimum is 18!")
            return false
        } else {
            // decode property string to class
            val laRoomyDeviceProperty = LaRoomyDeviceProperty()
            laRoomyDeviceProperty.fromString(data)

            // add property and request next property if the loop is active
            if(this.propertyLoopActive){
                this.laRoomyDevicePropertyList.add(laRoomyDeviceProperty)
                this.sendNextPropertyRequest(laRoomyDeviceProperty.propertyIndex)
            } else {
                if(verboseLog){
                    Log.d("readPropertyString", "Property-Transmission received. Loop not active. Update the element with index: ${laRoomyDeviceProperty.propertyIndex}")
                }
                applicationProperty.logControl("I: Property-Transmission received. Loop not active. Update the element with index: ${laRoomyDeviceProperty.propertyIndex}")

                // if the loop is not active, this must be an update-transmission, so replace the property
                if (this.laRoomyDevicePropertyList.size > laRoomyDeviceProperty.propertyIndex) {
                    // update internal array-list
                    this.laRoomyDevicePropertyList[laRoomyDeviceProperty.propertyIndex] =
                        laRoomyDeviceProperty
                    // update ui-adapter-list
                    this.updatePropertyElementInUIList(laRoomyDeviceProperty)
                }
            }
            return true
        }
    }

    private fun readInitTransmission(data: String, dataSize: Int) : Boolean {

        // check if the length of the transmission is valid
        if(dataSize < 13){
            //invalid data size
            if(verboseLog){
                Log.e("readInitTransmission", "Invalid data size. Data-Size was: $dataSize > minimum is 7!")
            }
            applicationProperty.logControl("E: Invalid data size of init-transmission. Data-Size was: $dataSize > minimum is 7!")
            return false
        } else {
            // save device data:
            var hexString = "0x"
            hexString += data[8]
            hexString += data[9]

            bleDeviceData.propertyCount = Integer.decode(hexString)

            hexString = "0x"
            hexString += data[10]
            hexString += data[11]

            bleDeviceData.groupCount = Integer.decode(hexString)

            if (data[12] == '1') {
                bleDeviceData.hasCachingPermission = true
            }

            // check if binding is required
            when {
                (data[13] == '1') -> {
                    this.bleDeviceData.isBindingRequired = true

                    // binding is required, so send the binding request on basis of the passkey setup
                    val useCustomKeyForBinding =
                        applicationProperty.loadBooleanData(
                            R.string.FileKey_AppSettings,
                            R.string.DataKey_UseCustomBindingKey
                        )

                    sendBindingRequest(useCustomKeyForBinding)
                }
                else -> {
                    // no binding is required, so notify the subscriber of the callback
                    saveLastSuccessfulConnectedDeviceAddress(currentDevice?.address ?: "")
                    bleDeviceData.authenticationSuccess = true
                    callback.onInitializationSuccessful()
                }
            }
        }
        return true
    }

    private fun readBindingResponse(data: String, dataSize: Int) : Boolean {

        if(dataSize < 10){
            if(verboseLog){
                Log.e("BindingResponse", "Invalid data size. Data size was $dataSize - Required: 9")
            }
            applicationProperty.logControl("Invalid data size. Data size was $dataSize - Required: 9")
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
                        // binding success
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

    private fun readFastDataSetterTransmission(data: String, dataSize: Int) : Boolean {
        return if(dataSize < 8){
            if(verboseLog){
                Log.e("readFastDataSetter", "DataSize too short. DataSize was $dataSize. Minimum is 8!")
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
                    this.laRoomyDevicePropertyList = propertyData.deviceProperties
                    this.laRoomyPropertyGroupList = propertyData.devicePropertyGroups
                    this.generateUIAdaptableArrayListFromDeviceProperties(addInALoopWhenReady, true)

                    if(!addInALoopWhenReady){

                        // TODO: start simple state loop ???

                        // must be loaded from cache, so start the complex loop here
                        this.startComplexStateDataLoop()
                    }

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

    fun startReloadProperties(){
        this.invokeCallbackLoopAfterUIDataGeneration = true
        this.propertyLoopActive = true
        this.startLoopTimeoutWatcher(LOOPTYPE_PROPERTY)
        this.sendNextPropertyRequest(-1)
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
            val languageIdentificationString =
                when (applicationProperty.systemLanguage) {
                    "Deutsch" -> "de\r"
                    else -> "en\r"
                }
            rqString += languageIdentificationString

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

                    val devicePropertyCacheData = DevicePropertyCacheData()
                    DevicePropertyCacheData().generate(this.laRoomyDevicePropertyList, this.laRoomyPropertyGroupList)

                    PropertyCacheManager(applicationProperty.applicationContext)
                        .savePCacheData(
                            devicePropertyCacheData,
                            this.currentDevice?.address ?: ""
                        )
                }

                // generate UI-data
                this.generateUIAdaptableArrayListFromDeviceProperties(this.invokeCallbackLoopAfterUIDataGeneration, false)

                // start retrieving the complex property states
                Handler(Looper.getMainLooper()).postDelayed({
                    this.startComplexStateDataLoop()
                }, 1000)
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

            // add language identifier
            val languageIdentificationString =
                when (applicationProperty.systemLanguage) {
                    "Deutsch" -> "de\r"
                    else -> "en\r"
                }
            rqString += languageIdentificationString

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
            Handler(Looper.getMainLooper()).postDelayed({
                this.startComplexStateDataLoop()
            }, 1000)
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
            val cState =
                unlockControlState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
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
                if(it.internalElementIndex == propertyIndex){
                    barGraphState.fromComplexPropertyState(it.complexPropertyState)
                }
            }
        }

        // validate the existing state and apply the appropriate method
        val res = if(barGraphState.isValid()){
            barGraphState.updateFromString(data)
        } else {
            barGraphState.fromString(data)
        }

        // check the result and update the data (or handle error)
        if(!res){
            // handle error
            applicationProperty.logControl("E: Error reading data from BarGraphState data transmission.")
            return
        } else {
            val cState =
                barGraphState.toComplexPropertyState()

            this.updateInternalComplexPropertyStateDataAndTriggerEvent(cState, propertyIndex)
        }
    }

    private fun sendPropertyStateRequest(propertyIndex: Int){
        var rqString = "31"
        val hexString = Integer.toHexString(propertyIndex)
        if(hexString.length < 2){
            rqString += '0'
            rqString += hexString
        } else {
            rqString += hexString
        }
        rqString += "0000\r"
        this.sendData(rqString)
    }

//    private fun sendSinglePropertyRequest(propertyIndex: Int){
//
//        // if the loop is not active, the property will be updated, not added
//
//        //this.singlePropertyRetrievingAction = true
//
//        // invalidate parameter???
//
//        // TODO!
//    }
//
//    private fun sendSingleGroupRequest(groupIndex: Int){
//        //this.singleGroupRetrievingAction = true
//
//        // TODO!
//    }

    ///////////////////////////////////////////////////////////////// section!

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
                Log.e("initDeviceTransmsn", "Init failed - no connection/ dev-info: address: ${this.currentDevice?.address} dev-type: ${this.currentDevice?.type} ?: ${this.currentDevice}")
            }
            applicationProperty.logControl("E: Init failed - no connection")
        }
    }


    fun clear(){
        this.bluetoothGatt?.close()
        this.bluetoothGatt = null

        //this.preselectIndex = -1
        //this.scanResultList.clear()

        this.currentDevice = null
        this.currentUsedServiceUUID = ""
        this.isConnected = false
        //this.authRequired = true
        //this.propertyUpToDate = false
        //this.connectionTestSucceeded = false
        this.connectionSuspended = false
        this.isResumeConnectionAttempt = false
        this.suspendedDeviceAddress = ""
        this.uIAdapterList.clear()
        //this.singlePropertyRetrievingAction = false
        //this.singleGroupRetrievingAction = false
        //this.singlePropertyDetailRetrievingAction = false
        //this.singleGroupDetailRetrievingAction = false
        //this.updateStackProcessActive = false
        //this.multiComplexPropertyPageOpen = false
        //this.currentPropertyResolveID = -1
        //this.currentGroupResolveID = -1
        //this.multiComplexPageID = -1
        //this.multiComplexTypeID = -1
        this.isBindingRequired = false
        //this.isCurrentConnectionDoneWithSharedBindingKey = false
        //this.passKeySecondTryOut = false

        //this.propertyRequestIndexCounter = 0

        this.propertyLoopActive = false
        //this.propertyNameResolveLoopActive = false
        this.groupLoopActive = false
        //this.groupInfoLoopActive = false
        //this.deviceHeaderRecordingActive = false

        this.invokeCallbackLoopAfterUIDataGeneration = false

        this.laRoomyDevicePropertyList.clear()
        this.laRoomyPropertyGroupList.clear()
        this.bleDeviceData.clear()
    }

    private fun clearPropertyRelatedParameterAndStopAllLoops(){

        // stop loops
        this.propertyLoopActive = false
        this.groupLoopActive = false
        this.complexStateLoopActive = false
        this.invokeCallbackLoopAfterUIDataGeneration = false

        // clear arrays
        this.uIAdapterList.clear()
        this.laRoomyDevicePropertyList.clear()
        this.laRoomyPropertyGroupList.clear()
        this.complexStatePropertyIndexes.clear()

        // reset other params
        this.currentComplexStateRetrievingIndex = -1
    }

    private fun imageFromIndexCounter(indexCounter: Int) : Int {
        return when(indexCounter){
            0 -> {
                R.drawable.bluetooth_green_glow_sq64
            }
            1 -> {
                R.drawable.bluetooth_orange_glow_sq64
            }
            2 -> {
                R.drawable.bluetooth_blue_glow_sq64
            }
            3 -> {
                R.drawable.bluetooth_purple_glow_sq64
            }
            4 -> {
                R.drawable.bluetooth_green_glow_sq64
            }
            5 -> {
                R.drawable.bluetooth_orange_glow_sq64
            }
            6 -> {
                R.drawable.bluetooth_blue_glow_sq64
            }
            7 -> {
                R.drawable.bluetooth_purple_glow_sq64
            }
            8 -> {
                R.drawable.bluetooth_green_glow_sq64
            }
            9 -> {
                R.drawable.bluetooth_orange_glow_sq64
            }
            10 -> {
                R.drawable.bluetooth_blue_glow_sq64
            }
            11 -> {
                R.drawable.bluetooth_purple_glow_sq64
            }
            else -> {
                R.drawable.bluetooth_orange_glow_sq64
            }
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
        this.currentDevice =
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress)
        this.bluetoothGatt = this.currentDevice?.connectGatt(applicationProperty.applicationContext, false, this.gattCallback)
    }

    fun connectToBondedDeviceWithMacAddress(macAddress: String?){
        this.bondedList?.forEach {
            if(it.address == macAddress){
                this.currentDevice = it
            }
        }
        this.bluetoothGatt = this.currentDevice?.connectGatt(applicationProperty.applicationContext, false, this.gattCallback)
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

        if(!this.isConnected){
            Log.e("M:sendData", "Unexpected error, bluetooth device not connected")
            applicationProperty.logControl("E: Unexpected: sendData invoked, but device not connected")

            this.callback.onConnectionError(BLE_UNEXPECTED_BLUETOOTH_DEVICE_NOT_CONNECTED)
        }
        else {
            if(verboseLog) {
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

            this.gattCharacteristic.setValue(data)

            if (this.bluetoothGatt == null) {
                Log.e("M:sendData", "Member bluetoothGatt was null!")
            }

            this.bluetoothGatt?.writeCharacteristic(this.gattCharacteristic)
        }
    }

    fun checkBluetoothEnabled(caller: Activity){
        if(verboseLog) {
            Log.d("M:bluetoothEnabled?", "Check if bluetooth is enabled")
        }
        bleAdapter?.takeIf{it.isDisabled}?.apply{
            // this lambda expression will be applied to the object(bleAdapter) (but only if "isDisabled" == true)
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            caller.startActivityForResult(enableBtIntent, requestEnableBT)
        }
    }

    fun close(){
        this.bluetoothGatt?.close()
        this.bluetoothGatt = null
        this.isConnected = false
    }

    fun disconnect(){
        this.bluetoothGatt?.disconnect()
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

            this.bluetoothGatt?.connect()

            // set up a handler to check if the connection works
            Executors.newSingleThreadScheduledExecutor().schedule({
                if(!isConnected){
                    callback.onConnectionError(
                        BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE)
                }
            }, 10000, TimeUnit.MILLISECONDS)

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

    fun savePropertyDataToCacheIfPermitted(){
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
    }

    private fun generateUIAdaptableArrayListFromDeviceProperties(addInALoop: Boolean, isCacheLoadingOperation: Boolean){

        //this.dataReadyToShow = false
        this.uIAdapterList.clear()

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
                            // TODO: maybe fix the inconsistency?? laRoomyDeviceProperty.groupIndex = expectedGroupIndex ???
                        }

                        // check if this element is part of the next group
                        if((laRoomyDeviceProperty.groupIndex > expectedGroupIndex)&&(currentGroup != -1)){
                            currentGroup = -1
                            expectedGroupIndex++
                            // the last element must have been the last in group
                            this.uIAdapterList.elementAt(globalIndex - 1).isLastInGroup = true
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
                                val dpl = DevicePropertyListContentInformation()
                                dpl.elementType = GROUP_ELEMENT
                                dpl.canNavigateForward = false
                                dpl.internalElementIndex = laRoomyDevicePropertyGroup.groupIndex
                                dpl.elementText = laRoomyDevicePropertyGroup.groupName
                                dpl.imageID = laRoomyDevicePropertyGroup.imageID
                                // add the global index of the position in the array
                                dpl.globalIndex = globalIndex
                                globalIndex++
                                // add the group to the list
                                this.uIAdapterList.add(dpl)
                            } else {
                                if(verboseLog){
                                    Log.e("generateUIArray", "Error group index out of range")
                                }
                                applicationProperty.logControl("E: Group index out of range!")
                            }
                        }

                        // add the property element(s) to the group
                        val propertyEntry = DevicePropertyListContentInformation()
                        propertyEntry.elementType = PROPERTY_ELEMENT
                        propertyEntry.canNavigateForward =
                            laRoomyDeviceProperty.needNavigation()
                        propertyEntry.isGroupMember = true
                        propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
                        propertyEntry.imageID = laRoomyDeviceProperty.imageID
                        propertyEntry.internalElementIndex = laRoomyDeviceProperty.propertyIndex
                        propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
                        propertyEntry.simplePropertyState = laRoomyDeviceProperty.propertyState
                        // set global index
                        propertyEntry.globalIndex = globalIndex
                        globalIndex++
                        // add it to the list
                        this.uIAdapterList.add(propertyEntry)

                        // check if this is the last property element
                        if(laRoomyDevicePropertyList.size == index + 1){
                            currentGroup = -1
                            expectedGroupIndex++
                            // the last element must have been the last in group
                            this.uIAdapterList.elementAt(globalIndex - 1).isLastInGroup = true
                            return@forEachIndexed // TODO: check if this will work!!!
                        }

                    } else {
                        // element is not part of a group, add it raw

                        // but before reset the group params (if they are set)
                        if (currentGroup != -1) {
                            currentGroup = -1
                            expectedGroupIndex++
                            // the last element must have been the last in group
                            this.uIAdapterList.elementAt(globalIndex - 1).isLastInGroup = true
                        }

                        // create the entry
                        val propertyEntry = DevicePropertyListContentInformation()
                        propertyEntry.elementType = PROPERTY_ELEMENT
                        propertyEntry.canNavigateForward = laRoomyDeviceProperty.needNavigation()
                        propertyEntry.internalElementIndex = laRoomyDeviceProperty.propertyIndex
                        propertyEntry.imageID = laRoomyDeviceProperty.imageID
                        propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
                        //propertyEntry.indexInsideGroup = index
                        propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
                        propertyEntry.simplePropertyState = laRoomyDeviceProperty.propertyState
                        // set global index
                        propertyEntry.globalIndex = globalIndex
                        globalIndex++
                        // add it to the list
                        this.uIAdapterList.add(propertyEntry)
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
                            propertyCallback.onUIAdaptableArrayListItemAdded(
                                uIAdapterList.elementAt(
                                    uIItemAddCounter
                                )
                            )

                            uIItemAddCounter++

                            if (uIItemAddCounter == uIAdapterList.size) {
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
                (150).toLong()
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

            }, 500, TimeUnit.MILLISECONDS)
        }
    }

    private fun propertyTypeFromID(ID: Int) : Int {
        this.laRoomyDevicePropertyList.forEach {
            if(ID == it.propertyIndex){
                return it.propertyType
            }
        }
        return -1
    }

    private fun propertyElementFromID(ID: Int): LaRoomyDeviceProperty {
        this.laRoomyDevicePropertyList.forEach {
            if(ID == it.propertyIndex){
                return it
            }
        }
        return LaRoomyDeviceProperty()
    }

    private fun startComplexStateDataLoop(){
        if(verboseLog) {
            Log.d(
                "M:startCompDataLoop",
                "ComplexStateDataLoop started - At first: Indexing the Property-IDs with complexStateData"
            )
        }
        // clear the ID-Array
        this.complexStatePropertyIndexes.clear()
        // loop the properties and save the ones with complex state
        this.laRoomyDevicePropertyList.forEach {
            // start with the first complex state data element
            if(it.propertyType >= COMPLEX_PROPERTY_START_INDEX){
                this.complexStatePropertyIndexes.add(it.propertyIndex)
            }
        }

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

            // setup a timeout, to recognize if the remote device is not responding
            this.startLoopTimeoutWatcher(LOOPTYPE_COMPLEXSTATE)

            this.sendComplexPropertyStateRequest(
                this.complexStatePropertyIndexes.elementAt(this.currentComplexStateRetrievingIndex)
            )
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

    fun doComplexPropertyStateRequestForPropertyIndex(propertyIndex: Int) {
        this.sendComplexPropertyStateRequest(propertyIndex)
    }

    private fun sendBindingRequest(useCustomKey: Boolean){

        val passKey: String

        // at first look for a shared key for the device
        val bindingPairManager = BindingPairManager(applicationProperty.applicationContext)
        val sharedKey = bindingPairManager.lookUpForPassKeyWithMacAddress(this.currentDevice?.address ?: "")

        // if a shared binding key for the mac address exists, the key is preferred,
        // because a key from a sharing link will only be saved if it defers from the main key

        passKey = if (sharedKey.isNotEmpty()) {
            bleDeviceData.passKeyTypeUsed = PASSKEY_TYPE_SHARED
            sharedKey
        } else {
            if (useCustomKey) {
                bleDeviceData.passKeyTypeUsed = PASSKEY_TYPE_CUSTOM
                (applicationProperty.loadSavedStringData(
                    R.string.FileKey_AppSettings,
                    R.string.DataKey_CustomBindingPasskey
                ))
            } else {
                bleDeviceData.passKeyTypeUsed = PASSKEY_TYPE_NORM
                (applicationProperty.loadSavedStringData(
                    R.string.FileKey_AppSettings,
                    R.string.DataKey_DefaultRandomBindingPasskey
                ))
            }
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

//    fun isMultiComplexProperty(propertyID: Int) : Boolean{
//
//        return when(this.propertyTypeFromID(propertyID)){
//            COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> true
//            // TODO: add all multicomplex properties here
//            else -> false
//        }
//    }

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

    fun enableDeviceBinding(passKey: String){
        //this.sendData("$enableBindingSetterCommandEntry$passKey$")
        this.isBindingRequired = true
    }

    fun releaseDeviceBinding(){
        //this.sendData(releaseDeviceBindingCommand)
        this.isBindingRequired = false
    }

    private fun propertyTypeFromIndex(propertyIndex: Int) : Int {
        return if(propertyIndex < this.laRoomyDevicePropertyList.size){
            this.laRoomyDevicePropertyList.elementAt(propertyIndex).propertyType
        } else {
            Log.e("propTypeFromIndex", "Invalid index. Size of array is: ${this.laRoomyDevicePropertyList.size}. Index was: $propertyIndex")
            -1
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
            }
            '2' -> {
                Log.e("onErrorFlag", "Error was: Transmission undeliverable")
            }
            else -> {
                Log.e("onErrorFlag", "Error was: undefined flag value")
            }
        }
    }

    private fun updatePropertyElementInUIList(laRoomyDeviceProperty: LaRoomyDeviceProperty){

        this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->

            if(laRoomyDeviceProperty.propertyIndex == devicePropertyListContentInformation.internalElementIndex){

                devicePropertyListContentInformation.elementText = laRoomyDeviceProperty.propertyDescriptor
                devicePropertyListContentInformation.canNavigateForward = laRoomyDeviceProperty.needNavigation()
                //devicePropertyListContentInformation.complexPropertyState = laRoomyDeviceProperty.complexPropertyState// the complex state is not included in the transmission, so it is not initialized, yet
                devicePropertyListContentInformation.elementType = PROPERTY_ELEMENT
                devicePropertyListContentInformation.isGroupMember = laRoomyDeviceProperty.isGroupMember
                devicePropertyListContentInformation.imageID = laRoomyDeviceProperty.imageID
                devicePropertyListContentInformation.internalElementIndex = laRoomyDeviceProperty.propertyIndex
                devicePropertyListContentInformation.propertyType = laRoomyDeviceProperty.propertyType
                devicePropertyListContentInformation.simplePropertyState = laRoomyDeviceProperty.propertyState

                this.propertyCallback.onUIAdaptableArrayItemChanged(index)
                return@forEachIndexed
            }
        }
    }

    private fun updateGroupElementInUIList(laRoomyDevicePropertyGroup: LaRoomyDevicePropertyGroup){

        this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->

            if(laRoomyDevicePropertyGroup.groupIndex == devicePropertyListContentInformation.internalElementIndex){

                devicePropertyListContentInformation.elementType = GROUP_ELEMENT
                devicePropertyListContentInformation.canNavigateForward = false
                devicePropertyListContentInformation.elementText = laRoomyDevicePropertyGroup.groupName
                devicePropertyListContentInformation.imageID = laRoomyDevicePropertyGroup.imageID

                this.propertyCallback.onUIAdaptableArrayItemChanged(index)
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
            if(devicePropertyListContentInformation.internalElementIndex == propertyIndex){
                uIElementIndex = index
                devicePropertyListContentInformation.complexPropertyState = cState
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
            if(devicePropertyListContentInformation.internalElementIndex == propertyIndex){
                devicePropertyListContentInformation.complexPropertyState = cState
            }
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
                    if(!complexStateLoopActive && !propertyLoopActive && !groupLoopActive){
                        if(verboseLog){
                            Log.w("LoopTimeoutWatcher", "All loops are offline. Execute self reset. Loop-type was: ${loopTypeToString(timeoutWatcherData.loopType)}")
                        }
                        timeoutWatcherData.reset()
                        cancel()
                    } else {

                        if (timeoutWatcherData.timeoutFlag) {
                            // the flag was not reset during the reception of a response, there must be a problem
                            if (timeoutWatcherData.loopRepeatCounter < 4) {
                                if (verboseLog) {
                                    Log.w(
                                        "LoopTimeoutWatcher",
                                        "Timeout occurred in ${loopTypeToString(timeoutWatcherData.loopType)} loop - restarting loop. Repeat-Counter is: ${timeoutWatcherData.loopRepeatCounter}"
                                    )
                                }
                                applicationProperty.logControl("W: Timeout occurred in ${loopTypeToString(timeoutWatcherData.loopType)} loop - restarting loop. Repeat-Counter is: ${timeoutWatcherData.loopRepeatCounter}")


                                // TODO: restart loop from loop-type!

                            } else {
                                // loop could not be finalized
                                Log.e("LoopTimeoutWatcher", "Loop-Timeout occurred 3 times! - Device not responding!")
                                applicationProperty.logControl("E: Loop-Timeout occurred 3 times! - Device not responding!")


                                // TODO: what to do here?? notify user?? close device??
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
            }, (0).toLong(), (500).toLong()
        )
    }

    private fun notifyLoopIsWorking(currentIndex: Int){
        if(this.timeoutWatcherData.isStarted){
            this.timeoutWatcherData.currentIndex = currentIndex
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
            this.timeoutWatcherData.reset()
            this.timeoutTimer.cancel()

            if (verboseLog) {
                Log.d("StopTimeOutWatcher", "Loop-Timeout Watcher stopped!")
            }
        }
    }

    // the callback definition for the event handling in the calling class
    interface BleEventCallback : Serializable{
        fun onConnectionStateChanged(state: Boolean){}
        fun onDataReceived(data: String?){}
        fun onConnectionError(errorID: Int){}
        fun onInitializationSuccessful(){}
        fun onBindingPasskeyRejected(){}
        fun onDeviceReadyForCommunication(){}
    }

    interface PropertyCallback: Serializable {
        fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>){}
        fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation){}
        fun onUIAdaptableArrayItemChanged(index: Int){}
        fun onPropertyInvalidated(){}
        fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int){}
        fun onComplexPropertyStateChanged(UIAdapterElementIndex: Int, newState: ComplexPropertyState){}
        fun onRemoteUserMessage(deviceHeaderData: DeviceInfoHeaderData){}
        //fun onMultiComplexPropertyDataUpdated(data: MultiComplexPropertyData){}
        fun onFastDataPipeInvoked(propertyID: Int, data: String){}
        fun onBindingResponse(responseID: Int){}
        fun getCurrentOpenComplexPropPagePropertyIndex() : Int {
            // if overwritten, do not return the super method!
            return -1
        }
    }
}