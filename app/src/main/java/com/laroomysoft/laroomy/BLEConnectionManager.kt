package com.laroomysoft.laroomy

import android.content.Context
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

private const val MAX_CONNECTION_ATTEMPTS = 10

//const val UNKNOWN_DEVICETYPE = 0
//const val LAROOMYDEVICETYPE_XNG = 1
//const val LAROOMYDEVICETYPE_CTX = 2
//const val LAROOMYDEVICETYPE_TVM = 3

const val UPDATE_TYPE_ELEMENT_DEFINITION = 1
const val UPDATE_TYPE_DETAIL_DEFINITION = 2

const val SINGLEACTION_NOT_PROCESSED = 1
const val SINGLEACTION_PARTIALLY_PROCESSED = 2
const val SINGLEACTION_PROCESSING_COMPLETE = 3
const val SINGLEACTION_PROCESSING_ERROR = 4

const val DEVICE_NOTIFICATION_BINDING_NOT_SUPPORTED = 1
const val DEVICE_NOTIFICATION_BINDING_SUCCESS = 2
const val DEVICE_NOTIFICATION_BINDING_ERROR = 3

const val BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE = "unable to resume connection - device not reachable"
const val BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE = "unable to resume connection - invalid address"




//class LaRoomyDevicePresentationModel {
//    // NOTE: This is the data-model for the DeviceListItem in the main-activity
//    var name = ""
//    var address = ""
//    //var type = 0
//    var image = 0
//}

//class ComplexPropertyState {
//    // shared state values (multi-purpose)
//    var valueOne = -1      // (R-Value in RGB Selector)     // (Level-Value in ExtendedLevelSelector)   // (hour-value in SimpleTimeSelector)       // (on-time hour-value in TimeFrameSelector)        // (number of bars in bar-graph activity)
//    var valueTwo = -1      // (G-Value in RGB Selector)     // (not used in ExtendedLevelSelector)      // (minute-value in SimpleTimeSelector)     // (on-time minute-value in TimeFrameSelector)      // (use value as bar-descriptor in bar-graph activity)
//    var valueThree = -1    // (B-Value in RGB Selector)     // (not used in ExtendedLevelSelector)      // (??                                      // (off-time hour-value in TimeFrameSelector)       // (fixed maximum value in bar-graph activity)
//    var valueFour = -1     // general use                   // flag-value in simple Navigator
//    var valueFive = -1     // general use                   // flag value in simple Navigator
//    var commandValue = -1  // (Command in RGB Selector)     // (not used in ExtendedLevelSelector)      // (??                                      // (off-time minute-value in TimeFrameSelector)
//    var enabledState = true// at this time only a placeholder (not implemented yet)
//    var onOffState = false // (not used in RGB Selector)    // used in ExLevelSelector                  // not used(for on/off use extra property)  //  not used(for on/off use extra property)
//    var strValue = ""
//
//    // single used values (only valid in specific complex states)
//    var hardTransitionFlag = false  // Value for hard-transition in RGB Selector (0 == SoftTransition / 1 == HardTransition)
//    var timeSetterIndex = -1        // Value to identify the time setter type
//}

//class DeviceInfoHeaderData {
//    var message = ""
//    var imageID = -1
//    var valid = false
//
//    fun clear(){
//        this.message = ""
//        this.imageID = -1
//        this.valid = false
//    }
//}
//
//class ElementUpdateInfo{
//    var elementID = -1
//    var elementIndex = -1
//    var elementType = -1
//    var updateType = -1
//}
//
//class MultiComplexPropertyData{
//    var dataIndex = -1
//    var dataName = ""
//    var dataValue = -1
//    var isName = false
//}
//
//class DevicePropertyListContentInformation : SeekBar.OnSeekBarChangeListener{
//    // NOTE: This is the data-model for the PropertyElement in the PropertyList on the DeviceMainActivty
//
//    var handler: OnPropertyClickListener? = null
//
//    var canNavigateForward = false
//    var isGroupMember = false
//    var isLastInGroup = false
//    var elementType = -1 //SEPARATOR_ELEMENT
//    var indexInsideGroup = -1
//    var globalIndex = -1
//    var elementText = ""
//    var elementID = -1
//    var imageID = -1
//    var propertyType = -1
//    //var initialElementValue = -1
//    var simplePropertyState = -1
//    var complexPropertyState = ComplexPropertyState()
//
//    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//        this.handler?.onSeekBarPositionChange(this.globalIndex, progress, SEEK_BAR_PROGRESS_CHANGING)
//    }
//
//    override fun onStartTrackingTouch(seekBar: SeekBar?) {
//        this.handler?.onSeekBarPositionChange(this.globalIndex, -1, SEEK_BAR_START_TRACK)
//    }
//
//    override fun onStopTrackingTouch(seekBar: SeekBar?) {
//        this.handler?.onSeekBarPositionChange(this.globalIndex, -1, SEEK_BAR_STOP_TRACK)
//    }
//}

class BLEConnectionManager(private val applicationProperty: ApplicationProperty) {

    // constant properties (regarding the device!) ////////////////////////
    //private val serviceUUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    //private val characteristicUUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    private val clientCharacteristicConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val authenticationString = "xPsM0-33wSp_mmT$"// outgoing
    private val testCommand = "vXtest385_26$"// outgoing
    private val propertyStringPrefix = "IPR" // incoming -prefix
    private val groupStringPrefix = "IPG"// incoming -prefix
    private val groupMemberStringPrefix = "mI%"
    private val propertyNameStartIndicator = "PD:S"// incoming -prefix
    private val propertyNameEndIndicator = "PD:E"// incoming
    private val groupInfoStartIndicator = "GI:S"//incoming -prefix
    private val groupInfoEndIndicator = "GI:E"// incoming
    private val complexDataStateTransmissionEntry = "PSC"
    private val simpleDataStateTransmissionEntry = "PSS"
    private val propertyChangedNotificationEntry = "DnPcx1="
    private val propertyGroupChangedNotificationEntry = "DnPGc=t"
    private val deviceHeaderStartEntry = "DnDIHs7"
    private val deviceHeaderCloseMessage = "DnDIHe+"

    private val deviceReconnectedNotification = "500002002\r"

    private val navigatedToDeviceMainPageNotification = "yDnNavM=5$"
    private val multiComplexPropertyPageInvokedStartEntry = "yDnMCIv-X"
    private val multiComplexPropertyNameSetterEntry = "MCN&"
    private val multiComplexPropertyDataSetterEntry = "MCD&"
    private val enableBindingSetterCommandEntry = "SeB:"
    private val releaseDeviceBindingCommand = "SrB>$"
    private val propertyRetrievalCompleteNotification = "yDnPRf-P!$"// outgoing
    private val requestLocalTimeCommand = "RqLcTime"// incoming
    private val bindingNotSupportedNotification = "DnBNS=x"// incoming
    private val bindingSuccessNotification = "DnBNS=y"// incoming
    private val bindingErrorNotification = "DnBNS=e"// incoming

    // static notifications
    private val propertyLoadingCompleteNotification = "500002001\r"

    // new ones:
    private val bleDeviceData = BLEDeviceData()

    /////////////////////////////////////////////////

    var isConnected:Boolean = false
        private set

    val initializationSuccess : Boolean
        get() {
        return this.bleDeviceData.authenticationSuccess
    }

    var isBindingRequired = false
        private set
    var connectionTestSucceeded = false
        private set
    var connectionSuspended = false
        private set

    var isCurrentConnectionDoneWithSharedBindingKey = false

    private var authRequired = true
    private var propertyLoopActive = false
    private var propertyNameResolveLoopActive = false
    private var passKeySecondTryOut = false
    private var groupLoopActive = false
    private var groupInfoLoopActive = false
    private var propertyConfirmationModeActive = false
    private var propertyNameResolveSingleAction = false
    private var propertyGroupNameResolveSingleAction = false
    private var singlePropertyRetrievingAction = false
    private var singlePropertyDetailRetrievingAction = false
    private var singleGroupRetrievingAction = false
    private var singleGroupDetailRetrievingAction = false
    private var isResumeConnectionAttempt = false
    private var propertyUpToDate = false
    private var dataReadyToShow = false
    private var complexStateLoopActive = false
    private var deviceHeaderRecordingActive = false
    private var updateStackProcessActive = false
    private var multiComplexPropertyPageOpen = false
    private var suspendedDeviceAddress = ""
    private var currentPropertyResolveID = -1 // initialize with invalid marker
    private var currentPropertyResolveIndex = -1 // initialize with invalid marker
    private var currentGroupResolveID = -1 // initialize with invalid marker
    private var currentGroupResolveIndex = -1 // initialize with invalid marker
    private var currentStateRetrievingIndex = -1 // initialize with invalid marker
    private var multiComplexPageID = -1 // initialize with invalid marker
    private var multiComplexTypeID = -1 // initialize with invalid marker
    private lateinit var activityContext: Context
    //private lateinit var callingActivity: Activity
    private lateinit var callback: BleEventCallback
    private lateinit var propertyCallback: PropertyCallback
    var currentDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    lateinit var gattCharacteristic: BluetoothGattCharacteristic
    var deviceInfoHeaderData = DeviceInfoHeaderData()

    var laRoomyDevicePropertyList = ArrayList<LaRoomyDeviceProperty>()
    var laRoomyPropertyGroupList = ArrayList<LaRoomyDevicePropertyGroup>()
    var uIAdapterList = ArrayList<DevicePropertyListContentInformation>()
    var elementUpdateList = ArrayList<ElementUpdateInfo>()

    var complexStatePropertyIDs = ArrayList<Int>()

    var currentUsedServiceUUID = ""
        // used to cache the current service uuid
        private set

    val currentUsedCharacteristicUUID: String
        get() = this.gattCharacteristic.uuid.toString()


    //private val scanResultList: MutableList<ScanResult?> = ArrayList()

    private lateinit var mHandler: Handler

    val isPropertyUpToDate: Boolean
        get() = this.propertyUpToDate

    val isUIDataReady: Boolean
        get() = this.dataReadyToShow

    val isLastAddressValid: Boolean
        get() = (this.getLastConnectedDeviceAddress().isNotEmpty())

    private val bondedList: Set<BluetoothDevice>? by lazy(LazyThreadSafetyMode.NONE){
        this.bleAdapter?.bondedDevices
    }

    var bondedLaRoomyDevices = ArrayList<LaRoomyDevicePresentationModel>()
        get() {
            // clear the list:
            field.clear()
            // check if the user wants to list all bonded devices
            val listAllDevices =
                (activityContext.applicationContext as ApplicationProperty).loadBooleanData(
                    R.string.FileKey_AppSettings,
                    R.string.DataKey_ListAllDevices
                )
            // fill the list with all bonded devices
            if (listAllDevices) {

                var imageIndex = 0

                this.bleAdapter?.bondedDevices?.forEach {
                    val device = LaRoomyDevicePresentationModel()
                    device.name = it.name
                    device.address = it.address

                    //device.type = laRoomyDeviceTypeFromName(it.name)

                    if(device.name.contains("laroomy", true)||(device.name.contains("lry", true))){
                        device.image = R.drawable.laroomy_icon_sq64
                    } else {
                        device.image = imageFromIndexCounter(imageIndex)
                        imageIndex++
                    }
                    field.add(device)
                }
            } else {
                // fill the list only with laroomy devices (or devices which follow the laroomy-name guidelines)
                this.bleAdapter?.bondedDevices?.forEach {
                    // It is not possible to get the uuids from the device here
                    // -> so the name must be the criteria to identify a laroomy device!
                    if (it.name.startsWith("Laroomy") || it.name.contains("LRY", true) || it.name.contains("laroomy", true)) {
                        val device = LaRoomyDevicePresentationModel()
                        device.name = it.name
                        device.address = it.address
                        device.image = R.drawable.laroomy_icon_sq64
                        //device.type = laRoomyDeviceTypeFromName(it.name)
                        field.add(device)
                    }
                }
            }
            return field
        }

    private val bleAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE){

        val bluetoothManager =
            activityContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val requestEnableBT: Int = 13

    private var propertyDetailChangedOnConfirmation = false
    private var groupDetailChangedOnConfirmation = false

    private var mScanning: Boolean = false

    //private var preselectIndex: Int = -1

    private var connectionAttemptCounter = 0// remove!

    private var propertyRequestIndexCounter = 0
    private var groupRequestIndexCounter = 0

    private var itemAddCounter = -1


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

            // log (binary):
            //Log.d("M:CB:CharChanged", "Characteristic changed. Value: ${characteristic?.value}")

            // get data
            val dataAsString = characteristic?.getStringValue(0)

//            // format data
//            dataAsString = formatIncomingData(dataAsString ?: "")

            // log (string)
            if(verboseLog) {
                Log.d("M:CB:CharChanged", "Characteristic changed. String-Value: $dataAsString")
            }
            applicationProperty.logControl("I: Characteristic changed: $dataAsString")

            try {
                if (!dispatchTransmission(dataAsString ?: "")) {
                    callback.onDataReceived(dataAsString ?: "")
                }
            } catch (e:Exception){
                if(verboseLog){
                    Log.e("onCharacteristicChanged", "Exception while dispatching the incoming transmission. Exception: $e / Message: ${e.message}")
                }
                applicationProperty.logControl("E: Exception while dispatching the incoming transmission. Exception: $e / Message: ${e.message}")


                // TODO: notify user and navigate back, however respond to the error
            }

            // check authentication
            /*
            if(authRequired){

                if(verboseLog) {
                    Log.d("M:CB:CharChanged", "Data Received - Authentication Required")
                }

                when(dataAsString){
                    authenticationResponse -> {
                        if(verboseLog) {
                            Log.d(
                                "M:CB:CharChanged",
                                "Data Received - Authentication successful - Device ID confirmed"
                            )
                        }
                        if(passKeySecondTryOut){
                            isCurrentConnectionDoneWithSharedBindingKey = true
                        }
                        authRequired = false
                        authenticationSuccess = true
                        // save the device address (but only if the authentication was successful)
                        saveLastSuccessfulConnectedDeviceAddress(gatt?.device?.address ?: "")
                        callback.onAuthenticationSuccessful()
                    }
                    authenticationResponseBindingPasskeyRequired -> {
                        if(verboseLog) {
                            Log.d(
                                "M:CB:CharChanged",
                                "Data Received - Authentication - Device ID confirmed - additional binding is required"
                            )
                        }
                        applicationProperty.logControl("I: Authentication success. Binding is required!")

                        // check if custom or default binding is active
                        val defaultBindingKeyMustBeUsed =
                            (activityContext.applicationContext as ApplicationProperty).loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_UseCustomBindingKey)

                        isBindingRequired = true
                        sendBindingRequest(defaultBindingKeyMustBeUsed)


//                        if(!defaultBindingKeyMustBeUsed){
//                            Log.e(
//                                "M:CB:CharChanged",
//                                "Binding is not activated - Connection attempt must be rejected!"
//                            )
//                            //callback.onConnectionAttemptFailed(activityContext.getString(R.string.CA_BindingNotActivated))
//                        } else {
//                            Log.d(
//                                "M:CB:CharChanged",
//                                "Data Received - Authentication - Send Binding information"
//                            )
//                            isBindingRequired = true
//                            // send the passkey to proceed
//                            sendBindingRequest()
//                        }
                    }
                    authenticationResponseBindingPasskeyInvalid -> {
                        Log.e("M:CB:CharChanged", "Data Received - Authentication - Passkey rejected from device - PASSKEY INVALID")
                        applicationProperty.logControl("E: Passkey invalid - Connection rejected")

                        if(!passKeySecondTryOut) {
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:CharChanged",
                                    "Searching for a saved passkey for a specific mac address"
                                )
                            }
                            applicationProperty.logControl("I: Generate lookup for shared passkey")

                            val passKey =
                                tryUseSavedPassKeyForSpecificMacAddress(currentDevice?.address ?: "")

                            if(passKey == ERROR_NOTFOUND){
                                Log.e("M:CB:CharChanged", "No saved passKey - reject connection")
                                applicationProperty.logControl("E: No shared key - reject connection")
                                callback.onBindingPasskeyRejected()
                            } else {
                                if(verboseLog) {
                                    Log.d(
                                        "M:CB:CharChanged",
                                        "Saved passkey found - try to use this key in the second chance"
                                    )
                                }
                                applicationProperty.logControl("I: Shared passkey found - try again")

                                passKeySecondTryOut = true
                                sendBindingRequest(passKey)
                            }
                        } else {
                            Log.e("M:CB:CharChanged", "Passkey rejected on the second chance - finally reject connection")
                            applicationProperty.logControl("E: Passkey finally rejected. Stop process.")
                            // raise event
                            callback.onBindingPasskeyRejected()
                        }
                    }
                    else -> {
                        Log.w("M:CB:CharChanged", "!Warning: Authentication failed! Unexpected Authentication Token")
                        applicationProperty.logControl("E: Authentication failed: Unexpected token.")
                    }
                }
            }
            else {
                var dataProcessed = checkForDeviceCommandsAndNotifications(dataAsString ?: "error")

                if(!dataProcessed) {

//                    if(updateStackProcessActive) {
//                        dataProcessed = checkSingleAction(dataAsString ?: "error")
//                    }
                    dataProcessed = processElementUpdateStack(dataAsString ?: "error")

                    // TODO: add user-logs to all loops:  ???

                    if(!dataProcessed) {
                        // check if one of the property-retrieving-loops is active
                        if (propertyLoopActive) {
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:CharChanged",
                                    "Data Received - Property Loop is active - check for property-string"
                                )
                            }
                            // try to add the device property, if the end is reached the loop will be cut off
                            dataProcessed = addDeviceProperty(dataAsString ?: "error")
                        }
                        if (propertyNameResolveLoopActive) {
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:CharChanged",
                                    "Data Received - PropertyName resolve Loop is active - check for name-strings"
                                )
                            }
                            // check and handle property-name-resolve-request
                            dataProcessed = resolvePropertyName(dataAsString ?: "error")
                        }
                        if (groupLoopActive) {
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:CharChanged",
                                    "Data Received - group indexing Loop is active - check for group-id for indexes"
                                )
                            }
                            // try to add the group, if the end is reached, the loop will be cut off
                            dataProcessed = addPropertyGroup(dataAsString ?: "error")
                        }
                        if (groupInfoLoopActive) {
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:CharChanged",
                                    "Data Received - group-detail-info Loop is active - check the detailed info for the given ID"
                                )
                            }
                            // if the detailed group info loop is active, check the string for group info
                            dataProcessed = resolveGroupInfo(dataAsString ?: "error")
                        }
                        if(deviceHeaderRecordingActive){
                            if(verboseLog) {
                                Log.d(
                                    "M:CB:CharChanged",
                                    "Data Received - deviceHeaderDataRecording is active -> add string to buffer. Data: $dataAsString"
                                )
                            }
                            deviceInfoHeaderData.message += dataAsString
                        }
                    }
                }
                // launch event
                if(!dataProcessed){
                    callback.onDataReceived(dataAsString)
                }
            }*/
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
                    // data-setter string
                    // TODO: !
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

            when(data[9]){
                '1' -> {
                    // property state changed notification
                    sendPropertyStateRequest(elementIndex)
                }
                '2' -> {
                    // property element changed notification
                    sendSinglePropertyRequest(elementIndex)
                }
                '3' -> {
                    // group element changed notification
                    sendSingleGroupRequest(elementIndex)
                }
                '4' -> {
                    // device-header / user-message notification
                    handleUserMessage(data, dataSize)
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
            // get image id (transferred in the id fields)
            var hexString = "0x"
            hexString += data[2]
            hexString += data[3]
            this.deviceInfoHeaderData.imageID = Integer.decode(hexString)

            data.removeRange(0, 8)

            this.deviceInfoHeaderData.message = data
            this.deviceInfoHeaderData.valid = true
            this.propertyCallback.onDeviceHeaderChanged(this.deviceInfoHeaderData)
        } else {
            // no notification data
            // TODO!
        }
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
                this.readComplexStateData(pIndex, pType, data, dataSize)
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

    private fun readComplexStateData(propertyIndex: Int, propertyType: Int, data: String, dataSize: Int){
        when(propertyType){
            COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR -> {
                this.processRGBSelectorData(propertyIndex, data, dataSize)
            }
            COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR -> {
                this.processEXLevelSelectorData(propertyIndex, data, dataSize)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR -> {
                this.processTimeSelectorData(propertyIndex, data, dataSize)
            }
            COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR -> {
                this.processTimeFrameSelectorData(propertyIndex, data, dataSize)
            }
            COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL -> {
                this.processUnlockControlData(propertyIndex, data, dataSize)
            }
            COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR -> {
                this.processNavigatorData(propertyIndex, data, dataSize)
            }
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> {
                this.processBarGraphData(propertyIndex, data, dataSize)
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
                        (activityContext.applicationContext as ApplicationProperty).loadBooleanData(
                            R.string.FileKey_AppSettings,
                            R.string.DataKey_UseCustomBindingKey
                        )

                    sendBindingRequest(useCustomKeyForBinding)
                }
                else -> {
                    // no binding is required, so notify the subscriber of the callback
                    saveLastSuccessfulConnectedDeviceAddress(currentDevice?.address ?: "")
                    bleDeviceData.authenticationSuccess = true
                    callback.onAuthenticationSuccessful()
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
                        callback.onAuthenticationSuccessful()
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
                            }
                            else -> {
                                if (verboseLog) {
                                    Log.e("EnableBinding", "Enable-Binding Response: FAILED. -Unknown error")
                                }
                                applicationProperty.logControl("E: Received Binding-Failed response from the device. -Unknown error")
                            }
                        }
                    } else {
                        // enable binding success
                        if(verboseLog){
                            Log.d("EnableBinding", "Enable-Binding Response: SUCCESS.")
                        }
                        applicationProperty.logControl("I: Received Enable-Binding-Success response from the device.")
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
                            }
                            else -> {
                                if (verboseLog) {
                                    Log.e("ReleaseBinding", "Release-Binding Response: FAILED. -Unknown error")
                                }
                                applicationProperty.logControl("E: Received Release-Failed response from the device. -Unknown error")
                            }
                        }
                    } else {
                        // release binding success
                        if(verboseLog){
                            Log.d("ReleaseBinding", "Release-Binding Response: SUCCESS.")
                        }
                        applicationProperty.logControl("I: Received Release-Binding-Success response from the device.")
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

    fun startPropertyListing(){
        if(verboseLog){
            Log.d("startPropList", "Starting property listing...")
        }
        applicationProperty.logControl("I: Starting property listing...")

        this.propertyLoopActive = true
        this.sendNextPropertyRequest(-1)
    }

    private fun sendNextPropertyRequest(currentIndex: Int){

        // increase index
        val newIndex = currentIndex + 1

        // check for the end of the property count
        if(newIndex < this.bleDeviceData.propertyCount){
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
                when ((this.activityContext.applicationContext as ApplicationProperty).systemLanguage) {
                    "Deutsch" -> "de\r"
                    else -> "en\r"
                }
            rqString += languageIdentificationString

            sendData(rqString)
        } else {

            this.propertyLoopActive = false

            if(verboseLog){
                Log.d("sendNextPropRQ", "Final property count reached. Property count is: ${this.bleDeviceData.propertyCount}")
            }
            applicationProperty.logControl("I: Final property count reached. Property-count is: ${this.bleDeviceData.propertyCount}")

            // the property loop is complete, check if there are groups and request them
            if(this.bleDeviceData.groupCount > 0){
                this.startGroupListing()
            } else {

                // TODO: there are no groups, so proceed without

                // generate UI-data
                this.generateUIAdaptableArrayListFromDeviceProperties()

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
        this.sendNextGroupRequest(-1)
    }

    private fun sendNextGroupRequest(currentIndex: Int){

        // increase index
        val newIndex = currentIndex + 1

        // check for the end of the group count
        if(newIndex < this.bleDeviceData.groupCount){

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
                when ((this.activityContext.applicationContext as ApplicationProperty).systemLanguage) {
                    "Deutsch" -> "de\r"
                    else -> "en\r"
                }
            rqString += languageIdentificationString

            sendData(rqString)
        } else {
            // end of loop
            this.groupLoopActive = false

            if(verboseLog){
                Log.d("sendNextGroupRQ", "Final group count reached. Group-count is: ${this.bleDeviceData.groupCount}")
            }
            applicationProperty.logControl("I: Final group count reached. Group-count is: ${this.bleDeviceData.groupCount}")

            // generate UI-data
            this.generateUIAdaptableArrayListFromDeviceProperties()

            // start retrieving the complex property states
            Handler(Looper.getMainLooper()).postDelayed({
                this.startComplexStateDataLoop()
            }, 1000)
        }
    }

    private fun processRGBSelectorData(propertyIndex: Int, data: String, dataSize: Int){

    }

    private fun processEXLevelSelectorData(propertyIndex: Int, data: String, dataSize: Int){

    }

    private fun processTimeSelectorData(propertyIndex: Int, data: String, dataSize: Int){

    }

    private fun processTimeFrameSelectorData(propertyIndex: Int, data: String, dataSize: Int){

    }

    private fun processUnlockControlData(propertyIndex: Int, data: String, dataSize: Int){

    }

    private fun processNavigatorData(propertyIndex: Int, data: String, dataSize: Int){

    }

    private fun processBarGraphData(propertyIndex: Int, data: String, dataSize: Int){

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

    private fun sendSinglePropertyRequest(propertyIndex: Int){

        // if the loop is not active, the property will be updated, not added

        //this.singlePropertyRetrievingAction = true

        // invalidate parameter???

        // TODO!
    }

    private fun sendSingleGroupRequest(groupIndex: Int){
        //this.singleGroupRetrievingAction = true

        // TODO!
    }

    ///////////////////////////////////////////////////////////////// section!

    fun reAlignContextReferences(cContext: Context, eventHandlerObject: BleEventCallback){
        this.activityContext = cContext
        this.callback = eventHandlerObject
    }

//    private fun groupRequestLoopRequired():Boolean{
//        var ret = false
//        if(this.laRoomyDevicePropertyList.isNotEmpty()){
//            this.laRoomyDevicePropertyList.forEach {
//                if(it.isGroupMember){
//                    ret = true
//                }
//            }
//        }
//        return ret
//    }

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
        this.authRequired = true
        this.propertyUpToDate = false
        this.connectionTestSucceeded = false
        this.connectionSuspended = false
        this.isResumeConnectionAttempt = false
        this.suspendedDeviceAddress = ""
        this.uIAdapterList.clear()
        this.elementUpdateList.clear()
        this.singlePropertyRetrievingAction = false
        this.singleGroupRetrievingAction = false
        this.singlePropertyDetailRetrievingAction = false
        this.singleGroupDetailRetrievingAction = false
        this.updateStackProcessActive = false
        this.multiComplexPropertyPageOpen = false
        this.currentPropertyResolveID = -1
        this.currentGroupResolveID = -1
        this.multiComplexPageID = -1
        this.multiComplexTypeID = -1
        this.isBindingRequired = false
        this.isCurrentConnectionDoneWithSharedBindingKey = false
        this.passKeySecondTryOut = false

        this.propertyRequestIndexCounter = 0

        this.propertyLoopActive = false
        this.propertyNameResolveLoopActive = false
        this.groupLoopActive = false
        this.groupInfoLoopActive = false
        this.deviceHeaderRecordingActive = false

        this.laRoomyDevicePropertyList.clear()
        this.laRoomyPropertyGroupList.clear()
        this.bleDeviceData.clear()
    }

    fun clearPropertyRelatedParameter(){
        this.propertyUpToDate = false
        this.uIAdapterList.clear()
        this.elementUpdateList.clear()
        this.singlePropertyRetrievingAction = false
        this.singleGroupRetrievingAction = false
        this.singlePropertyDetailRetrievingAction = false
        this.singleGroupDetailRetrievingAction = false
        this.updateStackProcessActive = false
        this.multiComplexPropertyPageOpen = false
        this.currentPropertyResolveID = -1
        this.currentGroupResolveID = -1
        this.multiComplexPageID = -1
        this.multiComplexTypeID = -1

        this.propertyRequestIndexCounter = 0

        this.propertyLoopActive = false
        this.propertyNameResolveLoopActive = false
        this.groupLoopActive = false
        this.groupInfoLoopActive = false
        this.deviceHeaderRecordingActive = false

        this.laRoomyDevicePropertyList.clear()
        this.laRoomyPropertyGroupList.clear()
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

//    fun connectToDeviceWithInternalScanList(macAddress: String?){
//
//        var i: Int = -1
//        // search for the mac-address in the result list and try to connect
//        this.scanResultList.forEachIndexed { index, scanResult ->
//
//            if(scanResult?.device?.address == macAddress){
//                i = index
//            }
//        }
//        when(i){
//            -1 -> return
//            else -> {
//                this.currentDevice = scanResultList[i]?.device
//
//                this.bluetoothGatt = this.currentDevice?.connectGatt(this.activityContext, false, this.gattCallback)
//
//            }
//        }
//    }

    fun getLastConnectedDeviceAddress() : String {

        val address =
            this.applicationProperty.loadSavedStringData(R.string.FileKey_BLEManagerData, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)

        return if(address == ERROR_NOTFOUND) ""
        else address
    }

    fun connectToLastSuccessfulConnectedDevice() {

        val address =
            this.applicationProperty.loadSavedStringData(R.string.FileKey_BLEManagerData, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)

        if(address == ERROR_NOTFOUND)
            this.callback.onConnectionAttemptFailed("Error: no device address found")
        else
            this.connectToRemoteDevice(address)
    }

    fun connectToRemoteDevice(macAddress: String?){
        this.currentDevice =
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress)
        this.bluetoothGatt = this.currentDevice?.connectGatt(this.activityContext, false, this.gattCallback)
    }

    fun connectToBondedDeviceWithMacAddress(macAddress: String?){

        this.bondedList?.forEach {
            if(it.address == macAddress){
                this.currentDevice = it
            }
        }
        this.bluetoothGatt = this.currentDevice?.connectGatt(this.activityContext, false, this.gattCallback)
    }

//    fun connectToCurrentDevice(){
//        // TODO: This is a mark for the use of this method in another app: checking the bond state is not necessary, so this method is obsolete
//        if(this.currentDevice?.bondState == BluetoothDevice.BOND_NONE){
//            // device is not bonded, the connection attempt will raise an prompt for the user to connect
//            checkBondingStateWithDelay()
//        }
//        this.connect()
//    }

    private fun connect(){
        if(this.currentDevice != null) {
            this.bluetoothGatt =
                this.currentDevice?.connectGatt(this.activityContext, false, this.gattCallback)
        }
        else
            this.callback.onConnectionAttemptFailed(activityContext.getString(R.string.Error_ConnectionFailed_NoDevice))
    }

    fun sendData(data: String){

        if(!this.isConnected){
            Log.e("M:sendData", "Unexpected error, bluetooth device not connected")
            applicationProperty.logControl("E: Unexpected: sendData invoked, but device not connected")
            this.callback.onComponentError("Unexpected error, bluetooth device not connected")
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

            // raise event:
            this.callback.onDataSent(data)
        }
    }

//    private fun checkBondingStateWithDelay(){
//        if(this.connectionAttemptCounter < MAX_CONNECTION_ATTEMPTS) {
//
//            Handler(Looper.getMainLooper()).postDelayed({
//                // check if device is bonded now
//                if ((this.currentDevice?.bondState == BluetoothDevice.BOND_NONE)
//                    || (this.currentDevice?.bondState == BluetoothDevice.BOND_BONDING)
//                ) {
//                    // count the number of attempts
//                    this.connectionAttemptCounter++
//                    // call the delay again
//                    checkBondingStateWithDelay()
//                } else {
//                    connect()
//                }
//            }, 4000)
//        }
//    }

//    fun selectCurrentDeviceFromInternalList(index: Int){
//
//        if(this.scanResultList.isNotEmpty())
//            this.currentDevice = this.scanResultList[index]?.device
//        else
//            this.currentDevice = this.bondedList?.elementAt(index)
//    }

    fun checkBluetoothEnabled(caller: Activity){

        // TODO: check if this function works!

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

    private fun disconnect(){
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
            this.bluetoothGatt?.connect()

            // set up a handler to check if the connection works
            Executors.newSingleThreadScheduledExecutor().schedule({
                if(!isConnected){
                    callback.onComponentError(
                        BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_DEVICE_NOT_REACHABLE)
                }
            }, 3000, TimeUnit.MILLISECONDS)

        } else {
            Log.e("M:Bmngr:resumeC", "BluetoothManager: Internal Device Address invalid- trying to connect to saved address")
            this.callback.onComponentError(
                BLE_CONNECTION_MANAGER_COMPONENT_ERROR_RESUME_FAILED_NO_DEVICE)
        }
    }

//    private fun laRoomyDeviceTypeFromName(name: String): Int {
//        return when(name){
//            "LaRoomy XNG" -> LAROOMYDEVICETYPE_XNG
//            "LaRoomy CTX" -> LAROOMYDEVICETYPE_CTX
//            "LaRoomy TVM" -> LAROOMYDEVICETYPE_TVM
//            else -> UNKNOWN_DEVICETYPE
//        }
//    }

    fun saveLastSuccessfulConnectedDeviceAddress(address: String){
        if(verboseLog) {
            Log.d(
                "M:SaveAddress",
                "Saving address of successful connected device - address: $address"
            )
        }
        this.applicationProperty.saveStringData(address, R.string.FileKey_BLEManagerData, R.string.DataKey_LastSuccessfulConnectedDeviceAddress)
    }

//    fun startDevicePropertyListing(){
//
//        // TODO: what is if the property array is already filled? Init the confirm process! Or erase????
//
//        if(this.isConnected){
//            // only start if there is no pending process/loop
//            if(!(this.propertyLoopActive || this.propertyNameResolveLoopActive || this.groupLoopActive || this.groupInfoLoopActive)) {
//                if(verboseLog) {
//                    Log.d("M:StartPropListing", "Device property listing started.")
//                }
//                this.propertyRequestIndexCounter = 0
//                this.propertyLoopActive = true
//                this.propertyUpToDate = false
//
//                // only clear the list if this is not the confirmation mode
//                if(!this.propertyConfirmationModeActive) {
//                    this.dataReadyToShow = false
//                    this.laRoomyDevicePropertyList.clear()
//                    this.uIAdapterList.clear()
//                }
//                // start:
//                this.sendData("A000$")// request first index (0)
//            }
//        }
//    }

    fun reloadProperties(){

        // TODO: reset all necessary arrays and parameter???
        // something is reset in "startDevicePropertyListing":

        this.clearPropertyRelatedParameter()

        //this.startDevicePropertyListing()

    }

/*
    fun addDeviceProperty(property: String) :Boolean {
        var stringProcessed = false

        if (property == this.propertyLoopEndIndication) {
            // log
            if (verboseLog) {
                Log.d(
                    "M:addDeviceProperty",
                    "Device property loop end received - stopping loop."
                )
            }
            applicationProperty.logControl("I: Property-Loop END mark received - stopping loop")

            this.propertyLoopActive = false
            // set return value
            stringProcessed = true
            // start resolve loop
            this.startRetrievingPropertyNames()
        }
        else {
            if(property.startsWith("IPR")){
                // log
                if (verboseLog) {
                    Log.d("M:addDeviceProperty", "Device property string received: $property")
                }
                applicationProperty.logControl("I: Property-String received: $property")
                // add the element (only if the confirmation-mode is inactive)
                val laRoomyDeviceProperty = LaRoomyDeviceProperty()
                laRoomyDeviceProperty.fromString(property)
                if(this.propertyConfirmationModeActive){
                    // confirmation mode active: compare the property but note: at this point we can not use
                    // the overridden member "equals(...)" to compare, because only the raw data is recorded to the class
                    // -> so use the .checkRawEquality(...) member!
                    if (!this.laRoomyDevicePropertyList.elementAt(laRoomyDeviceProperty.propertyIndex)
                            .checkRawEquality(laRoomyDeviceProperty)
                    ) {
                        // log:
                        // the element does not confirm
                        // invalidate all property
                        this.stopAllPendingLoopsAndResetParameter()
                        this.propertyCallback.onCompletePropertyInvalidated()
                        return true
                    }
                }
                else this.laRoomyDevicePropertyList.add(laRoomyDeviceProperty)
                // set return value
                stringProcessed = true

                this.requestNextProperty()
            }
        }
        return stringProcessed
    }

    private fun requestNextProperty(){
        // increase index counter
        this.propertyRequestIndexCounter++
        // generate request string
        val data =
            when {
                (propertyRequestIndexCounter < 10) -> "A00${this.propertyRequestIndexCounter}$"
                ((propertyRequestIndexCounter > 9) && (propertyRequestIndexCounter < 100)) -> "A0${this.propertyRequestIndexCounter}$"
                else -> "A${this.propertyRequestIndexCounter}$"
            }
        // log:
        if(verboseLog) {
            Log.d("M:requestNextProperty", "Send next request index - message: $data")
        }
        // send the string
        this.sendData(data)
    }

    private fun startRetrievingPropertyNames(){
        if(verboseLog) {
            Log.d("M:RetrievingPropNames", "Starting to resolve the property descriptions")
        }
        if(this.isConnected){
            if(this.laRoomyDevicePropertyList.isNotEmpty()) {

                this.currentPropertyResolveIndex = 0

                val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                    "Deutsch" -> '1'
                    else -> '0'
                }
                var id = this.laRoomyDevicePropertyList.elementAt(currentPropertyResolveIndex).propertyIndex
                var hundred = 0
                var tenth = 0
                val single: Int

                if(id > 100) hundred = (id / 100)
                id -= (hundred*100)
                if(id > 10) tenth = (id / 10)
                id -= (tenth*10)
                single = id

                val requestString = "B$languageIdentificationChar$hundred$tenth$single$"

                if(verboseLog) {
                    Log.d(
                        "M:RetrievingPropNames",
                        "Sending Request String for the first element: $requestString"
                    )
                }
                this.propertyNameResolveLoopActive = true
                this.sendData(requestString)
            }
        }
    }

    fun resolvePropertyName(propertyName: String) :Boolean {

        var stringProcessed = false

        if(verboseLog) {
            Log.d(
                "M:resolvePropNames",
                "Task: trying to resolve the received string to property name"
            )
        }

        if(currentPropertyResolveID == -1) {

            if(verboseLog) {
                Log.d(
                    "M:resolvePropNames",
                    "The resolve-ID was invalidated, look if this is a description-name start indicator"
                )
            }

            var propertyID = ""
            var propertyState = ""

            propertyName.forEachIndexed { index, c ->
                when (index) {
                    0 -> if (c != 'P')return false
                    1 -> if (c != 'D')return false
                    2 -> if (c != ':')return false
                    3 -> if (c != 'S')return false
                    4 -> propertyID += c
                    5 -> propertyID += c
                    6 -> propertyID += c
                    7 -> propertyState += c
                    8 -> propertyState += c
                    9 -> propertyState += c
                }
            }
            // convert property ID to Int and check the value
            val id =
                propertyID.toInt()

            if(id < 256 && id > -1){
                // set ID
                this.currentPropertyResolveID = id
                // mark the string as processed
                stringProcessed = true

                // set the state in the appropriate deviceProperty
                val state =
                    propertyState.toInt()

                if(state < 256 && state > -1){
                    this.setPropertyStateForId(id, state, true)
                }
            }

            if(verboseLog) {
                Log.d(
                    "M:resolvePropNames",
                    "This was a start indicator. The next received string should be the name for the property with ID: $propertyID"
                )
            }
        }
        else {
            if(verboseLog) {
                Log.d("M:resolvePropNames", "This must be the name string or a end indicator")
            }
            // must be the name-string or the finalization-string
            if(propertyName == propertyNameEndIndicator){
                if(verboseLog) {
                    Log.d(
                        "M:resolvePropNames",
                        "It's a finalization string: reset necessary parameter"
                    )
                }
                // it's a finalization string -> check for loop end
                if(this.laRoomyDevicePropertyList.last().propertyIndex == currentPropertyResolveID){
                    if(verboseLog) {
                        Log.d(
                            "M:resolvePropNames",
                            "This was the last property name to resolve: close loop and reset parameter"
                        )
                    }
                    // it's the last index, close loop
                    propertyNameResolveLoopActive = false
                    // invalidate the index
                    this.currentPropertyResolveIndex = -1
                    // raise event (but only in retrieving-mode, not in confirmation mode)
                    if(!this.propertyConfirmationModeActive)
                        this.propertyCallback.onPropertyDataRetrievalCompleted(this.laRoomyDevicePropertyList)
                    // check if one of the device-properties is part of a Group -> if so, start group-retrieving loop
                    if(verboseLog) {
                        Log.d("M:resolvePropNames", "Check if some properties are part of a group")
                    }
                    if(this.groupRequestLoopRequired()){
                        if(!this.propertyNameResolveSingleAction) {
                            if(verboseLog) {
                                Log.d(
                                    "M:resolvePropNames",
                                    "This checkup was true. A group retrieval is required: start loop!"
                                )
                            }
                            // start retrieving
                            this.startGroupIndexingLoop()
                        }
                        else {
                            // this was a single retrieving action, do not proceed with the group request
                            // reset parameter:
                            this.propertyNameResolveSingleAction = false
                            this.propertyCallback.onPropertyDataChanged(-1, 0)
                        }
                    }
                    else{
                        // the retrieving process is finished at this point, so if this was a confirmation mode, reset parameter here and raise event if necessary
                        if(this.propertyConfirmationModeActive) {
                            this.propertyConfirmationModeActive = false

                            if(this.propertyDetailChangedOnConfirmation){
                                this.propertyDetailChangedOnConfirmation = false
                                this.propertyCallback.onPropertyDataChanged(-1, 0)
                            }
                        }
                        // set property up to date
                        this.propertyUpToDate = true
                        this.generateUIAdaptableArrayListFromDeviceProperties()

                        // TODO: start retrieving the complex property states

                        this.startComplexStateDataLoop()
                    }
                }
                else {
                    if(verboseLog) {
                        Log.d(
                            "M:resolvePropNames",
                            "This was not the last property index: send next request"
                        )
                    }
                    // it's not the last index, request next propertyName
                    requestNextPropertyDescription()
                }
                // invalidate the resolve id
                this.currentPropertyResolveID = -1
                // mark the string as processed
                stringProcessed = true
            }
            else {
                if(verboseLog) {
                    Log.d(
                        "M:resolvePropNames",
                        "This must be the name string for the property with ID: $currentPropertyResolveID"
                    )
                }
                // must be a name-string
                if(this.propertyConfirmationModeActive){
                    // confirmation mode active: compare the property name
                    if (propertyName != this.laRoomyDevicePropertyList.elementAt(this.currentPropertyResolveIndex).propertyDescriptor) {
                        // log:
                        if (verboseLog) {
                            Log.d(
                                "M:resolvePropNames",
                                "Confirmation-Mode active: this propertyName does not equal to the saved one.\nPropertyID: $currentPropertyResolveID\nPropertyIndex: $currentGroupResolveIndex"
                            )
                        }
                        // the property-name has changed
                        this.laRoomyDevicePropertyList.elementAt(this.currentPropertyResolveIndex).hasChanged = true
                        this.propertyDetailChangedOnConfirmation = true
                    }
                }
                else setPropertyDescriptionForID(currentPropertyResolveID, propertyName)

                // TODO: the string should be marked as processed, but are you sure at this point??
                //stringProcessed = true
            }
        }
        return stringProcessed
    }

    private fun requestNextPropertyDescription(){
        if(verboseLog) {
            Log.d("M:RQNextPropNames", "Requesting next property description")
        }
        // increment the property counter and send next request
        currentPropertyResolveIndex++

        if(currentPropertyResolveIndex < laRoomyDevicePropertyList.size) {

            val languageIdentificationChar =
                when ((this.activityContext.applicationContext as ApplicationProperty).systemLanguage) {
                    "Deutsch" -> '1'
                    else -> '0'
                }
            var id =
                this.laRoomyDevicePropertyList.elementAt(currentPropertyResolveIndex).propertyIndex
            var hundred = 0
            var tenth = 0
            val single: Int

            if (id > 100) hundred = (id / 100)
            id -= (hundred*100)
            if (id > 10) tenth = (id / 10)
            id -= (tenth*10)
            single = id

            val requestString = "B$languageIdentificationChar$hundred$tenth$single$"

            if(verboseLog) {
                Log.d("M:RQNextPropNames", "Sending next request: $requestString")
            }
            this.sendData(requestString)
        }
        else {
            Log.w("M:RQNextPropNames", "!! unexpected end of property array found, this should not happen, because the preliminary executed method \"resolvePropertyName\" is expected to close the retrieving loop")
            // !! unexpected end of property array found, this should not happen, because the preliminary executed method "resolvePropertyName" is expected to close the retrieving loop
            // be that as it may. Force loop closing:
            propertyNameResolveLoopActive = false
            currentPropertyResolveID = -1
            currentPropertyResolveIndex = -1
            propertyConfirmationModeActive = false
        }
    }

    private fun startGroupIndexingLoop(){

        // TODO: what is if the group array is already filled? Init the confirm process?? Or erase????

        // TODO: make sure there is no possibility of a double execution

        if(this.isConnected){
            if(verboseLog) {
                Log.d("M:StartGroupListing", "Device property GROUP listing started.")
            }
            this.groupRequestIndexCounter = 0
            this.groupLoopActive = true
            // only clear the list if this is not the confirmation mode
            if(!this.propertyConfirmationModeActive)
                this.laRoomyPropertyGroupList.clear()
            // start:
            this.sendData("E000$")// request first group index (0)
        }
    }

    private fun addPropertyGroup(group: String) : Boolean {
        var stringProcessed = false
        // log:
        if(verboseLog) {
            Log.d("M:AddPropGroup", "Add Property Group Invoked - check for type of data")
        }
        // check type of data
        if(group == this.groupLoopEndIndication){
            // log:
            if (verboseLog) {
                Log.d(
                    "M:AddPropGroup",
                    "This was the finalization indicator - close loop, reset parameter and start detailed-info-loop"
                )
            }
            applicationProperty.logControl("I: Group-Loop END marker received")

            // reset params:
            this.groupLoopActive = false
            this.groupRequestIndexCounter = 0
            // start detailed info loop:
            startDetailedGroupInfoLoop()
            // mark string as processed
            stringProcessed = true
        }
        else{
            // log:
            if (verboseLog) {
                Log.d(
                    "M:AddPropGroup",
                    "This could be a property-group-definition string - check this!"
                )
            }

            // check start-character to identify the group-string
            if(group.startsWith(groupStringPrefix, false)){
                // log:
                if (verboseLog) {
                    Log.d("M:AddPropGroup", "It is a group string - add to collection")
                }
                applicationProperty.logControl("I: Group-String received: $group")
                // add group to collection
                val propertyGroup = LaRoomyDevicePropertyGroup()
                propertyGroup.fromString(group)

                if(this.propertyConfirmationModeActive){
                    // confirmation mode active: compare the groups but note: at this point we can not use
                    // the overridden member "equals(...)" to compare, because only the raw data is recorded to the class
                    // -> so use the .checkRawEquality(...) member!
                    if(this.laRoomyPropertyGroupList.elementAt(propertyGroup.groupIndex).checkRawEquality(propertyGroup)){
                        // log:
                        if (verboseLog) {
                            Log.d(
                                "M:AddPropGroup",
                                "Confirmation-Mode active: The element differs from the saved one:\nGroupIndex: ${propertyGroup.groupIndex}"
                            )
                        }
                        // the group element differs from the saved element
                        // the proceeding of this process is not necessary
                        // -> invalidate all property data!
                        this.stopAllPendingLoopsAndResetParameter()
                        this.propertyCallback.onCompletePropertyInvalidated()
                        return true
                    }
                }
                else this.laRoomyPropertyGroupList.add(propertyGroup)
                // mark string as processed
                stringProcessed = true
                // proceed with retrieving loop:
                this.requestNextPropertyGroup()
            }
        }
        return stringProcessed
    }

    private fun requestNextPropertyGroup(){
        // increase the group index counter
        this.groupRequestIndexCounter++
        // generate request string
        val data =
            when {
                (groupRequestIndexCounter < 10) -> "E00${this.groupRequestIndexCounter}$"
                ((groupRequestIndexCounter > 9) && (groupRequestIndexCounter < 100)) -> "E0${this.groupRequestIndexCounter}$"
                else -> "E${this.groupRequestIndexCounter}$"
            }
        // log:
        if(verboseLog) {
            Log.d("M:requestNextGroup", "Send next property-group request index - message: $data")
        }
        // send the string
        this.sendData(data)
    }

    private fun startDetailedGroupInfoLoop(){
        if(verboseLog) {
            Log.d("M:RetrievingGroupNames", "Starting to resolve the detailed group info")
        }
        if(this.isConnected){
            if(this.laRoomyPropertyGroupList.isNotEmpty()) {

                this.currentGroupResolveIndex = 0

                val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                    "de" -> 1
                    else -> 0
                }
                var id = this.laRoomyPropertyGroupList.elementAt(currentGroupResolveIndex).groupIndex
                var hundred = 0
                var tenth = 0
                val single: Int

                if(id > 100) hundred = (id / 100)
                id -= (hundred*100)
                if(id > 10) tenth = (id / 10)
                id -= (tenth*10)
                single = id

                val requestString = "F$languageIdentificationChar$hundred$tenth$single$"

                if(verboseLog) {
                    Log.d(
                        "M:RetrievingGroupInfo",
                        "Sending Group-Info Request String for the first element: $requestString"
                    )
                }
                this.groupInfoLoopActive = true
                this.sendData(requestString)
            }
        }
    }

    private fun resolveGroupInfo(groupInfoData: String) : Boolean {
        var stringProcessed = false
        // log:
        if(verboseLog) {
            Log.d(
                "M:resolveGroupInfo",
                "Task: trying to resolve the received string to group info data"
            )
        }
        // check invalidated condition
        if(currentGroupResolveID == -1) {

            if(verboseLog) {
                Log.d(
                    "M:resolveGroupInfo",
                    "The resolve-ID was invalidated, look if this is a group-info start indicator"
                )
            }

            var groupID = ""

            groupInfoData.forEachIndexed { index, c ->
                when (index) {
                    0 -> if (c != 'G')return false
                    1 -> if (c != 'I')return false
                    2 -> if (c != ':')return false
                    3 -> if (c != 'S')return false
                    4 -> groupID += c
                    5 -> groupID += c
                    6 -> groupID += c
                }
            }
            // convert group ID to Int and check the value
            val id =
                groupID.toInt()

            if(id < 256 && id > -1){
                // set ID
                this.currentGroupResolveID = id
                // mark the string as processed
                stringProcessed = true
            }
            if(verboseLog) {
                Log.d(
                    "M:resolveGroupInfo",
                    "This was a start indicator. The next received string should be some data for the group with ID: $groupID"
                )
            }
        }
        else {
            if(verboseLog) {
                Log.d("M:resolveGroupInfo", "This must be the group-info or a end indicator")
            }
            // must be the name-string or the finalization-string
            if(groupInfoData == groupInfoEndIndicator){
                if(verboseLog) {
                    Log.d(
                        "M:resolveGroupInfo",
                        "It's a finalization string: reset necessary parameter"
                    )
                }
                // it's a finalization string -> check for loop end
                if(this.laRoomyPropertyGroupList.last().groupIndex == currentGroupResolveID){
                    if(verboseLog) {
                        Log.d(
                            "M:resolveGroupInfo",
                            "This was the last group-info to resolve: close loop and reset parameter"
                        )
                    }
                    // it's the last index, close loop
                    groupInfoLoopActive = false
                    this.propertyUpToDate = true
                    // invalidate the index
                    this.currentGroupResolveIndex = -1
                    // check if this was a confirmation process
                    if(this.propertyConfirmationModeActive){
                        // reset the confirmation parameter
                        this.propertyConfirmationModeActive = false

                        if(this.groupDetailChangedOnConfirmation){
                            this.groupDetailChangedOnConfirmation = false
                            this.propertyCallback.onPropertyGroupDataChanged(-1, 0)
                        }
                    }
                    else {
                        // raise event
                        if(this.propertyGroupNameResolveSingleAction){
                            this.propertyGroupNameResolveSingleAction = false
                            // trigger changed event (-1 means all entries!)
                            this.propertyCallback.onPropertyGroupDataChanged(-1, 0)
                        }
                        else {
                            // trigger retrieval event
                            this.propertyCallback.onGroupDataRetrievalCompleted(this.laRoomyPropertyGroupList)// TODO: this is not used - delete??

                            this.generateUIAdaptableArrayListFromDeviceProperties()

                            // TODO: start retrieving the complex property states
                            Handler(Looper.getMainLooper()).postDelayed({
                                this.startComplexStateDataLoop()
                            }, 2000)// TODO: no delay???
                        }
                    }
                }
                else {
                    if(verboseLog) {
                        Log.d(
                            "M:resolveGroupInfo",
                            "This was not the last group index: send next request"
                        )
                    }
                    // it's not the last index, request next propertyName
                    requestNextGroupInfo()
                }
                // invalidate the resolve id
                this.currentGroupResolveID = -1
                // mark the string as processed
                stringProcessed = true
            }
            else {
                if(verboseLog) {
                    Log.d(
                        "M:resolveGroupInfo",
                        "This must be the data for the group with ID: $currentGroupResolveID"
                    )
                }
                // must be group data:
                // check the type of data:
                if (groupInfoData.startsWith(groupMemberStringPrefix, false)) {
                    // log
                    if (verboseLog) {
                        Log.d(
                            "M:resolveGroupInfo",
                            "This must be the member IDs for the group with ID: $currentGroupResolveID"
                        )
                    }
                    // this was the member id identification, so set the member IDs
                    return if (this.propertyConfirmationModeActive) {
                        // confirmation mode is active so check the element
                        if (!this.compareMemberIDStringWithIntegerArrayList(
                                groupInfoData,
                                this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).memberIDs
                            )
                        ) {
                            // log:
                            if (verboseLog) {
                                Log.d(
                                    "M:resolveGroupInfo",
                                    "Confirmation-Mode active: The received member IDs differ from the saved ones.\nGroupID: $currentGroupResolveID"
                                )
                            }
                            // the member ids differ from the save ones
                            this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).hasChanged =
                                true
                            this.groupDetailChangedOnConfirmation = true
                        }
                        true
                    }
                    else setGroupMemberIDArrayForGroupID(this.currentGroupResolveID, groupInfoData)
                } else {
                    // log
                    if (verboseLog) {
                        Log.d(
                            "M:resolveGroupInfo",
                            "This must be the name-string for the group with ID: $currentGroupResolveID"
                        )
                    }
                    // this should be the name of the group, so set the group name
                    if (this.propertyConfirmationModeActive) {
                        // confirmation mode active: check for changes in the group-name
                        if (groupInfoData != this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).groupName) {
                            // log:
                            if (verboseLog) {
                                Log.d(
                                    "M:resolveGroupInfo",
                                    "Confirmation-Mode active: detected changed group-name.\nGroupID: $currentGroupResolveID"
                                )
                            }
                            // the group-name changed
                            this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).hasChanged =
                                true
                            this.groupDetailChangedOnConfirmation = true
                        }
                    } else setGroupName(this.currentGroupResolveID, groupInfoData)
                }
                // TODO: the string should be marked as processed, but are you sure at this point??
                //stringProcessed = true
            }
        }
        return stringProcessed
    }

    private fun requestNextGroupInfo(){
        // log
        if(verboseLog) {
            Log.d("M:RQNextGroupInfo", "Requesting next group info")
        }
        // increment the group index counter and send next request
        currentGroupResolveIndex++
        // check if the index is inside the valid scope
        if(currentGroupResolveIndex < laRoomyPropertyGroupList.size) {

            val languageIdentificationChar =
                when ((this.activityContext.applicationContext as ApplicationProperty).systemLanguage) {
                    "Deutsch" -> '1'
                    else -> '0'
                }
            var id =
                this.laRoomyPropertyGroupList.elementAt(currentGroupResolveIndex).groupIndex
            var hundred = 0
            var tenth = 0
            val single: Int

            if (id > 100) hundred = (id / 100)
            id -= (hundred*100)
            if (id > 10) tenth = (id / 10)
            id -= (tenth*10)
            single = id
            // create the request string
            val requestString = "F$languageIdentificationChar$hundred$tenth$single$"
            // log:
            if(verboseLog) {
                Log.d("M:RQNextGroup", "Sending next request: $requestString")
            }
            // send data:
            this.sendData(requestString)
        }
        else {
            Log.e("M:RQNextGroupInfo", "!! unexpected end of group array found, this should not happen, because the preliminary executed method \"resolveGroupInfo\" is expected to close the retrieving loop")
            // !! unexpected end of group array found, this should not happen, because the preliminary executed method "resolveGroupInfo" is expected to close the retrieving loop
            // be that as it may. Force loop closing:
            groupInfoLoopActive = false
            currentGroupResolveID = -1
            currentGroupResolveIndex = -1
            propertyConfirmationModeActive = false
        }
    }
*/

    private fun setPropertyDescriptionForID(id:Int, description: String){
        laRoomyDevicePropertyList.forEach {
            if(it.propertyIndex == id){
                if((this.activityContext.applicationContext as ApplicationProperty).systemLanguage == "Deutsch"){
//                    it.propertyDescriptor =
//                        encodeGermanString(description)
                } else {
                    it.propertyDescriptor = description
                }
            }
        }
    }

    private fun setGroupMemberIDArrayForGroupID(id: Int, data: String) : Boolean {
        try {

            // TODO: add logs

            var str1 = ""
            var str2 = ""
            var str3 = ""
            var str4 = ""
            var str5 = ""

            if (data.length >= 17) {

                data.forEachIndexed { index, c ->
                    when (index) {
                        3 -> str1 += c
                        4 -> str1 += c
                        5 -> str1 += c
                        6 -> str2 += c
                        7 -> str2 += c
                        8 -> str2 += c
                        9 -> str3 += c
                        10 -> str3 += c
                        11 -> str3 += c
                        12 -> str4 += c
                        13 -> str4 += c
                        14 -> str4 += c
                        15 -> str5 += c
                        16 -> str5 += c
                        17 -> str5 += c
                    }
                }
                laRoomyPropertyGroupList.forEach {
//                    if(it.groupIndex == id){
//                        it.setMemberIDs(
//                            if(str1.isNotEmpty())
//                                str1.toInt()
//                            else 0,
//                            if(str2.isNotEmpty())
//                                str2.toInt()
//                            else 0,
//                            if(str3.isNotEmpty())
//                                str3.toInt()
//                            else 0,
//                            if(str4.isNotEmpty())
//                                str4.toInt()
//                            else 0,
//                            if(str5.isNotEmpty())
//                                str5.toInt()
//                            else 0
//                        )
//                    }
                }
                return true
            } else
                return false
        }
        catch(except: Exception){
            return false
        }
    }

    private fun compareMemberIDStringWithIntegerArrayList(data: String, ids: ArrayList<Int>) :Boolean {
        try {
            if(ids.size < 4)return false    // 5 ??? no index ???

            var str1 = ""
            var str2 = ""
            var str3 = ""
            var str4 = ""
            var str5 = ""

            if (data.length >= 17) {

                data.forEachIndexed { index, c ->
                    when (index) {
                        3 -> str1 += c
                        4 -> str1 += c
                        5 -> str1 += c
                        6 -> str2 += c
                        7 -> str2 += c
                        8 -> str2 += c
                        9 -> str3 += c
                        10 -> str3 += c
                        11 -> str3 += c
                        12 -> str4 += c
                        13 -> str4 += c
                        14 -> str4 += c
                        15 -> str5 += c
                        16 -> str5 += c
                        17 -> str5 += c
                    }
                }
                if(ids.elementAt(0) != str1.toInt())return false
                if(ids.elementAt(1) != str2.toInt())return false
                if(ids.elementAt(2) != str3.toInt())return false
                if(ids.elementAt(3) != str4.toInt())return false
                if(ids.elementAt(4) != str5.toInt())return false
                return true
            } else
                return false
        }
        catch(except: Exception){
            return false
        }
    }

//    private fun checkForDeviceCommandsAndNotifications(data: String) :Boolean {
//        var dataProcessed = false
//        // log:
//        if(verboseLog) {
//            Log.d("M:CheckDevComAndNoti", "Check received string for notification")
//        }
//
//        // TODO: add user-logs to all notifications???
//        // check:
//
//        // only accept notifications if the property and group loops are not in progress
//        if(!this.propertyLoopActive && !this.groupLoopActive && !this.propertyNameResolveLoopActive && !this.groupInfoLoopActive) {
//            when {
//                data.startsWith(propertyChangedNotificationEntry) -> {
//                    this.updateProperty(data)
//                    dataProcessed = true
//
//                    if(verboseLog) {
//                        Log.d("M:CheckDevComAndNoti", "Property-Changed Notification detected")
//                    }
//                }
//
//                data.startsWith(propertyGroupChangedNotificationEntry) -> {
//                    this.updatePropertyGroup(data)
//                    dataProcessed = true
//
//                    if(verboseLog) {
//                        Log.d("M:CheckDevComAndNoti", "PropertyGroup-Changed Notification detected")
//                    }
//                }
//                data.startsWith(this.complexDataStateTransmissionEntry) -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "Complex-Property-State data received -> try to resolve it!"
//                        )
//                    }
//                    this.resolveComplexStateData(data)
//                    dataProcessed = true
//                }
//                data.startsWith(this.multiComplexPropertyNameSetterEntry) -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "Multi-Complex-Property Name-Data received -> try to resolve it!"
//                        )
//                    }
//                    this.resolveMultiComplexStateData(data, true)
//                    dataProcessed = true
//                }
//                data.startsWith(this.multiComplexPropertyDataSetterEntry) -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "Multi-Complex-Property Value-Data received -> try to resolve it!"
//                        )
//                    }
//                    this.resolveMultiComplexStateData(data, false)
//                    dataProcessed = true
//                }
//                data.startsWith(this.simpleDataStateTransmissionEntry) -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "Simple-Property-State data received -> try to resolve it!"
//                        )
//                    }
//                    this.resolveSimpleStateData(data)
//                    dataProcessed = true
//                }
//                data.startsWith(this.deviceHeaderStartEntry) -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "DeviceHeader start notification detected -> start recording"
//                        )
//                    }
//                    //this.startDeviceHeaderRecording(data)
//                    dataProcessed = true
//                }
//                data == this.deviceHeaderCloseMessage -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "DeviceHeader end notification detected -> reset parameter and trigger event"
//                        )
//                    }
//                    //this.endDeviceHeaderRecording()
//                    dataProcessed = true
//                }
//                data == this.testCommand -> {
//                    if(verboseLog) {
//                        Log.d("M:CheckDevComAndNoti", "Test command received.")
//                    }
//                    this.connectionTestSucceeded = true
//                    this.callback.onConnectionTestSuccess()
//                    dataProcessed = true
//                }
//                data == this.requestLocalTimeCommand -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "Local Time Request received. Sending Time to Device."
//                        )
//                    }
//                    this.setDeviceTime()
//                }
//                data == this.bindingNotSupportedNotification -> {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:CheckDevComAndNoti",
//                            "The device has the create-binding request rejected. -> Forward to current Activity (must be the DeviceSettingsActivity"
//                        )
//                    }
//                    this.propertyCallback.onDeviceNotification(DEVICE_NOTIFICATION_BINDING_NOT_SUPPORTED)
//                }
//                data == this.bindingSuccessNotification -> {
//                    Log.d(
//                        "M:CheckDevComAndNoti",
//                        "Binding-Success: The device has the binding command accepted."
//                    )
//                    this.propertyCallback.onDeviceNotification(DEVICE_NOTIFICATION_BINDING_SUCCESS)
//                }
//                data == this.bindingErrorNotification -> {
//                    Log.d(
//                        "M:CheckDevComAndNoti",
//                        "Binding-Error: The device has reported an error on a binding command"
//                    )
//                    this.propertyCallback.onDeviceNotification(DEVICE_NOTIFICATION_BINDING_ERROR)
//                }
//            }
//        }
//        else {
//            if(verboseLog) {
//                Log.d(
//                    "M:CheckDevComAndNoti",
//                    "Loop must be active - skip Notification or Command processing"
//                )
//            }
//        }
//        return dataProcessed
//    }

    private fun updateProperty(data: String){
        if(data.length < 19){
            var entireProperty = false
            var entireDetail = false
            var thisProperty = false
            var thisPropertyDetail = false
            var propID = ""
            var propIndex = ""

            data.forEachIndexed { index, c ->
                when(index){
                    7 -> propID += c
                    8 -> propID += c
                    9 -> propID += c
                    10 -> propIndex += c
                    11 -> propIndex += c
                    12 -> propIndex += c
                    13 -> if(c != '0')entireProperty = true
                    14 -> if(c != '0')entireDetail = true
                    15 -> if(c != '0')thisProperty = true
                    16 -> if(c != '0')thisPropertyDetail = true
                }
            }
            if(verboseLog) {
                Log.d(
                    "M:updateProperty",
                    "Recording of Property-Update String complete:\nUpdate: entireProperty = $entireProperty\nUpdate: entireDetail = $entireDetail\nUpdate: thisProperty = $thisProperty\nUpdate: thisPropertyDetail = $thisPropertyDetail"
                )
            }
            if(entireProperty){
                // TODO: this must be checked
                this.propertyCallback.onCompletePropertyInvalidated()
                return
            }
            if(entireDetail){
                // TODO: this must be checked
                this.propertyNameResolveSingleAction = true

                //startRetrievingPropertyNames()

            }
            if(thisProperty){
                val updateInfo = ElementUpdateInfo()
                updateInfo.elementID = propID.toInt()
                updateInfo.elementIndex = propIndex.toInt()
                updateInfo.elementType = PROPERTY_ELEMENT
                updateInfo.updateType = UPDATE_TYPE_ELEMENT_DEFINITION

                this.elementUpdateList.add(updateInfo)

                this.startUpdateStackProcessing()

                //this.sendSinglePropertyRequest(propIndex.toInt())
            }
            if(thisPropertyDetail){
                val updateInfo = ElementUpdateInfo()
                updateInfo.elementID = propID.toInt()
                updateInfo.elementIndex = propIndex.toInt()
                updateInfo.elementType = PROPERTY_ELEMENT
                updateInfo.updateType = UPDATE_TYPE_DETAIL_DEFINITION

                this.elementUpdateList.add(updateInfo)

                this.startUpdateStackProcessing()

                //this.sendSinglePropertyResolveRequest(propID.toInt())
            }

        }
        else {
            Log.e("M:updateProperty", "Error: insufficient string length")
        }
    }

    private fun updatePropertyGroup(data: String){
        if(data.length < 19){
            var entirePropertyGroup = false
            var entireGroupDetail = false
            var thisGroup = false
            var thisGroupDetail = false
            var groupID = ""
            var groupIndex = ""

            data.forEachIndexed { index, c ->
                when(index){
                    7 -> groupID += c
                    8 -> groupID += c
                    9 -> groupID += c
                    10 -> groupIndex += c
                    11 -> groupIndex += c
                    12 -> groupIndex += c
                    13 -> if(c != '0')entirePropertyGroup = true
                    14 -> if(c != '0')entireGroupDetail = true
                    15 -> if(c != '0')thisGroup = true
                    16 -> if(c != '0')thisGroupDetail = true
                }
            }
            if(verboseLog) {
                Log.d(
                    "M:updatePropGroup",
                    "Recording of Property Group Update String complete:\nUpdate: entirePropertyGroup = $entirePropertyGroup\nUpdate: entireGroupDetail = $entireGroupDetail\nUpdate: thisgroup = $thisGroup\nUpdate: thisGroupDetail = $thisGroupDetail"
                )
            }

            if(entirePropertyGroup){
                // TODO: check if this works!
                this.propertyCallback.onCompletePropertyInvalidated()
                return
            }
            if(entireGroupDetail){
                // TODO: check if this works!
                this.propertyGroupNameResolveSingleAction = true



                //startDetailedGroupInfoLoop()


            }
            if(thisGroup){
                val updateInfo = ElementUpdateInfo()
                updateInfo.elementID = groupID.toInt()
                updateInfo.elementIndex = groupIndex.toInt()
                updateInfo.elementType = GROUP_ELEMENT
                updateInfo.updateType = UPDATE_TYPE_ELEMENT_DEFINITION

                this.elementUpdateList.add(updateInfo)

                this.startUpdateStackProcessing()

                //this.sendSinglePropertyGroupRequest(groupIndex.toInt())
            }
            if(thisGroupDetail){
                val updateInfo = ElementUpdateInfo()
                updateInfo.elementID = groupID.toInt()
                updateInfo.elementIndex = groupIndex.toInt()
                updateInfo.elementType = GROUP_ELEMENT
                updateInfo.updateType = UPDATE_TYPE_DETAIL_DEFINITION

                this.elementUpdateList.add(updateInfo)

                this.startUpdateStackProcessing()

                //this.sendSingleGroupDetailRequest(groupID.toInt())
            }

        }
        else {
            Log.e("M:updatePropGroup", "Error: insufficient string length")
        }
    }

    private fun sendSinglePropertyResolveRequest(propertyID: Int){
        if(verboseLog) {
            Log.d("M:SendRequest", "SendSinglePropertyResolveRequest: Invoked")
        }
        if(this.isConnected) {
            // set marker
            this.singlePropertyDetailRetrievingAction = true
            // get language identification
            val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                "Deutsch" -> 1
                else -> 0
            }
            // build string
            val data =
                when {
                    (propertyID < 10) -> "B${languageIdentificationChar}00$propertyID$"
                    ((propertyID > 9) && (propertyID < 100)) -> "B${languageIdentificationChar}0$propertyID$"
                    else -> "B${languageIdentificationChar}$propertyID$"
                }
            // send request
            this.sendData(data)
        }
    }

    private fun sendSingleGroupDetailRequest(groupID: Int){
        if(verboseLog) {
            Log.d("M:SendRequest", "SendSingleGroupDetailRequest: Invoked")
        }
        if(this.isConnected) {
            // set marker
            this.singleGroupDetailRetrievingAction = true
            // get language identification
            val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                "Deutsch" -> 1
                else -> 0
            }
            // build string
            val data =
                when {
                    (groupID < 10) -> "F${languageIdentificationChar}00$groupID$"
                    ((groupID > 9) && (groupID < 100)) -> "F${languageIdentificationChar}0$groupID$"
                    else -> "F${languageIdentificationChar}$groupID$"
                }
            // send request
            this.sendData(data)
        }
    }

    private fun generateUIAdaptableArrayListFromDeviceProperties(){

        this.dataReadyToShow = false
        this.uIAdapterList.clear()

        // new: test!

        try {
            if (this.laRoomyDevicePropertyList.size > 0) {

                var globalIndex = 0
                var currentGroup = -1
                var expectedGroupIndex = 0

                this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->

                    // check if the property-element is part of a group
                    if (laRoomyDeviceProperty.isGroupMember) {
                        // element is part of a group, add the group first (if it isn't already)

                            // FIXME: the element is a group-member, but of the next group????????????

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
                        }

                        // add the group element
                        if (currentGroup == -1) {
                            currentGroup = laRoomyDeviceProperty.groupIndex

                            // make sure the group exist
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
                                // TODO: log: group not found
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

                        // check if the member-id-array in the group element contains this element
                        if(!this.laRoomyPropertyGroupList.elementAt(expectedGroupIndex).memberIDs.contains(laRoomyDeviceProperty.propertyIndex)){
                            if(verboseLog){
                                Log.w("generateUIArray", "Inconsistency detected. The property element with index: ${laRoomyDeviceProperty.propertyIndex} is defined as part of the group with index: ${laRoomyDeviceProperty.groupIndex} but the group definition has no property with index: ${laRoomyDeviceProperty.propertyIndex}")
                            }
                            applicationProperty.logControl("W: Inconsistency detected. The property element with index: ${laRoomyDeviceProperty.propertyIndex} is defined as part of the group with index: ${laRoomyDeviceProperty.groupIndex} but the group definition has no property with index: ${laRoomyDeviceProperty.propertyIndex}")
                        }


                    } else {
                        // element is not part of a group, add it raw

                        // but before reset the group params (if they are set)
                        if (currentGroup != -1) {
                            currentGroup = -1
                            expectedGroupIndex++
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

            // TODO: log critical error!!!!!!!!!!

            return
        }






        ////////////////////////////////////
        // old:
//        if(this.laRoomyDevicePropertyList.size > 0) {
//
//            var globalIndex = 0
//
//            if (this.laRoomyPropertyGroupList.size > 0) {
//                for (laRoomyDevicePropertyGroup in this.laRoomyPropertyGroupList) {
//                    // create the group entry
//                    val dpl = DevicePropertyListContentInformation()
//                    dpl.elementType = GROUP_ELEMENT
//                    dpl.canNavigateForward = false
//                    dpl.internalElementIndex = laRoomyDevicePropertyGroup.groupIndex
//                    dpl.elementText = laRoomyDevicePropertyGroup.groupName
//                    dpl.imageID = laRoomyDevicePropertyGroup.imageID
//                    // add the global index of the position in the array
//                    dpl.globalIndex = globalIndex
//                    globalIndex++
//                    // add the group to the list
//                    this.uIAdapterList.add(dpl)
//
//                    // add the device properties to the group by their IDs
//                    for (ID in laRoomyDevicePropertyGroup.memberIDs) {
//                        this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
//                            if (laRoomyDeviceProperty.propertyIndex == ID) {
//                                // ID found -> add property to list
//                                val propertyEntry = DevicePropertyListContentInformation()
//                                propertyEntry.elementType = PROPERTY_ELEMENT
//                                propertyEntry.canNavigateForward =
//                                    laRoomyDeviceProperty.needNavigation()
//                                propertyEntry.isGroupMember = true
//                                propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
//                                propertyEntry.imageID = laRoomyDeviceProperty.imageID
//                                propertyEntry.internalElementIndex = laRoomyDeviceProperty.propertyIndex
//                                //propertyEntry.indexInsideGroup = index
//                                propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
//                                propertyEntry.simplePropertyState = laRoomyDeviceProperty.propertyState
//                                // set global index
//                                propertyEntry.globalIndex = globalIndex
//                                globalIndex++
//                                // add it to the list
//                                this.uIAdapterList.add(propertyEntry)
//                                // ID found -> further processing not necessary -> break the loop
//                                return@forEachIndexed
//                            }
//                        }
//                    }
//
//                    // mark the last element in the group
//                    this.uIAdapterList.elementAt(globalIndex - 1).isLastInGroup = true
//                }
//            }
//            // now add the properties which are not part of a group
//            this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
//                // only add the non-group properties
//                if (!laRoomyDeviceProperty.isGroupMember) {
//                    // create the entry
//                    val propertyEntry = DevicePropertyListContentInformation()
//                    propertyEntry.elementType = PROPERTY_ELEMENT
//                    propertyEntry.canNavigateForward = laRoomyDeviceProperty.needNavigation()
//                    propertyEntry.internalElementIndex = laRoomyDeviceProperty.propertyIndex
//                    propertyEntry.imageID = laRoomyDeviceProperty.imageID
//                    propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
//                    //propertyEntry.indexInsideGroup = index
//                    propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
//                    propertyEntry.simplePropertyState = laRoomyDeviceProperty.propertyState
//                    // set global index
//                    propertyEntry.globalIndex = globalIndex
//                    globalIndex++
//                    // add it to the list
//                    this.uIAdapterList.add(propertyEntry)
//                }
//            }

            // end: old
            //////////////////////////////////////

            // notify finalization of the process
            Handler(Looper.getMainLooper()).postDelayed({
                this.sendData(propertyLoadingCompleteNotification)
            }, 700)


            // add the elements with a timer-delay
            itemAddCounter = 0

            Timer().scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        propertyCallback.onUIAdaptableArrayListItemAdded(
                            uIAdapterList.elementAt(
                                itemAddCounter
                            )
                        )

                        itemAddCounter++

                        if (itemAddCounter == uIAdapterList.size) {
                            cancel()
                            itemAddCounter = -1
                            dataReadyToShow = true
                            propertyCallback.onUIAdaptableArrayListGenerationComplete(uIAdapterList)
                        }
                    } catch (e: IndexOutOfBoundsException){
                        // FIXME: reset the whole???
                        cancel()
                    }
                }
            }, (0).toLong(), (150).toLong())// 300 or higher is the best (frame-skipping problem) // but 210 does not show any skipped frame with the parameter 5 frames set!

    }


//    private fun checkSingleAction(data: String) :Int {
//
//
//        //Log.d("M:CheckSingleAction", "single-action-retrieving is active look for a transmission to record")
//
//        // TODO: add more logs!
//
//        // TODO: if the retrieving loop is active, this should not be executed
//
//        if(this.singlePropertyRetrievingAction) {
//            if(verboseLog) {
//                Log.d(
//                    "M:CheckSingleAction",
//                    "Single Property Request is active, look for an appropriate transmission"
//                )
//            }
//            if (data.startsWith(propertyStringPrefix)) {
//                if(verboseLog) {
//                    Log.d("M:CheckSingleAction", "Property-String-Prefix detected")
//                }
//                // its a  single property request response
//
//                // initialize the property element from string
//                val updatedLaRoomyDeviceProperty = LaRoomyDeviceProperty()
//                updatedLaRoomyDeviceProperty.fromString(data)
//                var updateIndex = -1
//
//                // search the property ID in the list
//                this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
//                    if(laRoomyDeviceProperty.propertyIndex == updatedLaRoomyDeviceProperty.propertyIndex){
//                        updateIndex = index
//                        return@forEachIndexed
//                    }
//                }
//
//                // replace the origin
//                if(updateIndex != -1) {
//                    // get the original property entry
//                    val laroomyDeviceProperty =
//                        this.laRoomyDevicePropertyList.elementAt(updateIndex)
//
//                    // check for invalidation
//                    if(laroomyDeviceProperty.propertyIndex != updatedLaRoomyDeviceProperty.propertyIndex){
//                        // this can only occur if the complete property changed -> launch invalidated event
//                        this.propertyCallback.onCompletePropertyInvalidated()
//                        return SINGLEACTION_PROCESSING_ERROR
//                    }
//                    // set the possible new values
//                    laroomyDeviceProperty.imageID = updatedLaRoomyDeviceProperty.imageID
//                    laroomyDeviceProperty.propertyType = updatedLaRoomyDeviceProperty.propertyType
//                    laroomyDeviceProperty.groupIndex = updatedLaRoomyDeviceProperty.groupIndex
//
//                    // override the existing entry (reset)
//                    this.laRoomyDevicePropertyList[updateIndex] = laroomyDeviceProperty
//                }
//                updateIndex = -1
//
//                // search the appropriate element in the UI-Adapter-List and save the index
//                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
//                    if((devicePropertyListContentInformation.internalElementIndex == updatedLaRoomyDeviceProperty.propertyIndex)
//                        && (devicePropertyListContentInformation.elementType == PROPERTY_ELEMENT)){
//                        updateIndex = index
//                        return@forEachIndexed
//                    }
//                }
//
//                // replace the origin
//                if(updateIndex != -1) {
//                    // get the element from the UI-Adapter list and update all possible data
//                    val updateDevicePropertyListContentInformation =
//                        uIAdapterList.elementAt(updateIndex)
//                    updateDevicePropertyListContentInformation.canNavigateForward =
//                        updatedLaRoomyDeviceProperty.needNavigation()
//                    updateDevicePropertyListContentInformation.propertyType =
//                        updatedLaRoomyDeviceProperty.propertyType
//                    updateDevicePropertyListContentInformation.imageID =
//                        updatedLaRoomyDeviceProperty.imageID
//                    updateDevicePropertyListContentInformation.isGroupMember =
//                        updatedLaRoomyDeviceProperty.isGroupMember
//
//                    // replace the element in the UI-Adapter
//                    this.uIAdapterList[updateIndex] = updateDevicePropertyListContentInformation
//                    this.propertyCallback.onUIAdaptableArrayItemChanged(updateIndex)
//                }
//                // mark the single action as processed
//                this.singlePropertyRetrievingAction = false
//
//                return SINGLEACTION_PROCESSING_COMPLETE
//            }
//        }
//
//        if(this.singlePropertyDetailRetrievingAction){
//            if(verboseLog) {
//                Log.d(
//                    "M:CheckSingleAction",
//                    "Single Property DETAIL Request is active, look for an appropriate transmission"
//                )
//            }
//            when{
//                data.startsWith(propertyNameStartIndicator) -> {
//                    if(verboseLog) {
//                        Log.d("M:CheckSingleAction", "Property-Name start indicator detected")
//                    }
//                    // its a property description request -> look for the property id to record
//                    val id = this.propertyIDFromStartEntry(data)
//                    if(id != -1){
//                        this.currentPropertyResolveID = id
//                    }
//                    // TODO: if the id is invalid -> reset parameter???
//                    return SINGLEACTION_PARTIALLY_PROCESSED
//                }
//                data.startsWith(propertyNameEndIndicator) -> {
//                    if(verboseLog) {
//                        Log.d("M:CheckSingleAction", "Property-Name end indicator detected")
//                    }
//                    // end of transmission, set marker to false and erase the ID
//                    this.currentPropertyResolveID = -1
//                    this.singlePropertyDetailRetrievingAction = false
//                    return SINGLEACTION_PROCESSING_COMPLETE
//                }
//                else -> {
//                    var updateIndex = -1
//
//                    if(this.currentPropertyResolveID != -1){
//                        if(verboseLog) {
//                            Log.d(
//                                "M:CheckSingleAction",
//                                "Must be the property name. Data is: <$data>"
//                            )
//                        }
//                        // must be the name for the property
//                        // search the element in the property-list
//                        this.laRoomyDevicePropertyList.forEach {
//                            if(it.propertyIndex == this.currentPropertyResolveID){
//                                it.propertyDescriptor = data
//                                return@forEach
//                            }
//                        }
//                        // find the element in the UI-Adapter
//                        this.uIAdapterList.forEach {
//                            if((it.elementType == PROPERTY_ELEMENT) && (it.internalElementIndex == this.currentPropertyResolveID)){
//                                it.elementText = data
//                                updateIndex = it.globalIndex
//                                return@forEach
//                            }
//                        }
//                        this.propertyCallback.onUIAdaptableArrayItemChanged(updateIndex)
//                        return SINGLEACTION_PARTIALLY_PROCESSED
//                    }
//                }
//            }
//        }
//
//        if(this.singleGroupRetrievingAction){
//            if(verboseLog) {
//                Log.d(
//                    "M:CheckSingleAction",
//                    "Single Group Request is active, look for an appropriate transmission"
//                )
//            }
//            if(data.startsWith(groupStringPrefix)){
//                if(verboseLog) {
//                    Log.d("M:CheckSingleAction", "Group-Prefix detected")
//                }
//                // its a group request response
//
//                val updatedGroup = LaRoomyDevicePropertyGroup()
//                updatedGroup.fromString(data)
//
//                var updateIndex = -1
//
//                // search the element in the groupList
//                this.laRoomyPropertyGroupList.forEachIndexed { index, laroomyDevicePropertyGroup ->
//                    if(laroomyDevicePropertyGroup.groupIndex == updatedGroup.groupIndex){
//                        updateIndex = index
//                        return@forEachIndexed
//                    }
//                }
//
//                // replace the origin
//                if(updateIndex != -1) {
//                    // get to original element
//                    val propGroup =
//                        laRoomyPropertyGroupList.elementAt(updateIndex)
//
//                    // check for invalidation
//                    if(propGroup.groupIndex != updatedGroup.groupIndex){
//                        this.propertyCallback.onCompletePropertyInvalidated()
//                        return SINGLEACTION_PROCESSING_ERROR
//                    }
//                    // set possible values
//                    propGroup.imageID = updatedGroup.imageID
//                    propGroup.memberCount = updatedGroup.memberCount
//
//                    // override the existing entry (reset)
//                    this.laRoomyPropertyGroupList[updateIndex] = propGroup
//                }
//                updateIndex = -1
//
//                // update the UI-Adapter
//                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
//                    if((devicePropertyListContentInformation.elementType == GROUP_ELEMENT)
//                    && (devicePropertyListContentInformation.internalElementIndex == updatedGroup.groupIndex)){
//                        updateIndex = index
//                        return@forEachIndexed
//                    }
//                }
//
//                // replace the origin
//                if(updateIndex != -1){
//                    // get the original element
//                    val originElement =
//                        uIAdapterList.elementAt(updateIndex)
//
//                    // set new possible values
//                    originElement.imageID = updatedGroup.imageID
//
//                    // replace the original in the UI-Adapter
//                    this.uIAdapterList[updateIndex] = originElement
//
//                    this.propertyCallback.onUIAdaptableArrayItemChanged(updateIndex)
//                }
//                this.singleGroupRetrievingAction = false
//                return SINGLEACTION_PROCESSING_COMPLETE
//            }
//        }
//
//        if(this.singleGroupDetailRetrievingAction){
//            if(verboseLog) {
//                Log.d(
//                    "M:CheckSingleAction",
//                    "Single Group DETAIL Request is active, look for an appropriate transmission"
//                )
//            }
//           when{
//               data.startsWith(groupInfoStartIndicator) -> {
//                   if(verboseLog) {
//                       Log.d("M:CheckSingleAction", "Group Info start indicator detected")
//                   }
//                   // its a group detail request response start entry -> look for the group id to record
//                   val id = this.groupIDFromStartEntry(data)
//                   if(id != -1){
//                       this.currentGroupResolveID = id
//                   }
//                   // TODO: if the id is invalid -> reset parameter???
//                   return SINGLEACTION_PARTIALLY_PROCESSED
//               }
//               data.startsWith(groupInfoEndIndicator) -> {
//                   if(verboseLog) {
//                       Log.d("M:CheckSingleAction", "Group Info end indicator detected")
//                   }
//                   // end of transmission, set marker to false and erase the ID
//                   this.currentGroupResolveID = -1
//                   this.singleGroupDetailRetrievingAction = false
//                   return SINGLEACTION_PROCESSING_COMPLETE
//               }
//               else -> {
//                   // must be the new group detail
//                   if(this.currentGroupResolveID != -1){
//                       if(verboseLog) {
//                           Log.d(
//                               "M:CheckSingleAction",
//                               "Must be Group-detail data. Data is <$data>"
//                           )
//                       }
//                       return if(data.startsWith(groupMemberStringPrefix)){
//                           if(verboseLog) {
//                               Log.d("M:CheckSingleAction", "Group-Member String Prefix detected")
//                           }
//                           // must be the member ID transmission part
//                           // TODO: if the member IDs changed, the whole property must be invalidated and rearranged
//                           //this.propertyCallback.onCompletePropertyInvalidated()
//                           SINGLEACTION_PARTIALLY_PROCESSED
//                       } else {
//                           if(verboseLog) {
//                               Log.d("M:CheckSingleAction", "Must be the Group-Name..")
//                           }
//                           // must be the name for the group
//                           // search the element in the groupList
//                           this.laRoomyPropertyGroupList.forEach {
//                               if(it.groupIndex == this.currentGroupResolveID){
//                                   it.groupName = data
//                                   return@forEach
//                               }
//                           }
//                           var updateIndex = -1
//
//                           // find the element in the UI-Adapter
//                           this.uIAdapterList.forEach {
//                               if((it.internalElementIndex == this.currentGroupResolveID) && (it.elementType == GROUP_ELEMENT)){
//                                   it.elementText = data
//                                   updateIndex = it.globalIndex
//                                   return@forEach
//                               }
//                           }
//                           this.propertyCallback.onUIAdaptableArrayItemChanged(updateIndex)
//                           // return true:
//                           SINGLEACTION_PARTIALLY_PROCESSED
//                       }
//                   }
//               }
//           }
//        }
//        return SINGLEACTION_NOT_PROCESSED
//    }

//    private fun setPropertyStateForId(propertyID: Int, propertyState: Int, enabled: Boolean){
//        this.laRoomyDevicePropertyList.forEach {
//            if(it.propertyIndex == propertyID){
//                it.propertyState = propertyState
//                return@forEach
//            }
//        }
//    }

//    private fun resolveSimpleStateData(data: String){
//        // simple state data transmission length is 11 chars, for example: "PSS0221840$
//        if(data.length > 9){
//            // resolve ID:
//            val propertyID: Int
//            var strID = ""
//            strID += data.elementAt(3)
//            strID += data.elementAt(4)
//            strID += data.elementAt(5)
//            propertyID = strID.toInt()
//
//            if(verboseLog) {
//                Log.d(
//                    "M:resolveSimpleSData",
//                    "Trying to resolve simple state data for Property-ID: $propertyID"
//                )
//            }
//
//            if(propertyID > -1 && propertyID < 256) {
//                val propertyElement = this.propertyElementFromID(propertyID)
//
//                // resolve state
//                var state = ""
//                state += data.elementAt(6)
//                state += data.elementAt(7)
//                state += data.elementAt(8)
//                val newState = state.toInt()
//
//                // apply new state to property-array
//                //setPropertyStateForId(propertyID, newState, (data.elementAt(9) == '1'))
//
//                // apply new state to uIAdapter
//                var changedIndex = -1
//
//                this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
//                    if (devicePropertyListContentInformation.internalElementIndex == propertyElement.propertyIndex) {
//                        devicePropertyListContentInformation.simplePropertyState = newState
//                        changedIndex = index
//                    }
//                }
//
//                // 4. launch property changed event
//                this.propertyCallback.onSimplePropertyStateChanged(
//                    changedIndex,
//                    newState
//                )
//            } else {
//                Log.e("M:resolveSimpleSData", "Property-ID invalid! ID: $propertyID")
//            }
//        } else {
//            Log.e("M:resolveSimpleSData", "Simple-State transmission-length too short")
//        }
//    }
//
//    private fun resolveComplexStateData(data: String){
//        // minimum complex data array must be 6!
//        if(data.length > 5) {
//
//            // 1. Transform ID and get the type for the ID
//            var id = ""
//            id += data.elementAt(3)
//            id += data.elementAt(4)
//            id += data.elementAt(5)
//
//            val propertyID =
//                id.toInt()
//
//            if(verboseLog) {
//                Log.d(
//                    "M:resolveComplexSData",
//                    "Trying to resolve complexStateData for ID: $propertyID"
//                )
//            }
//
//            if(propertyID > -1 && propertyID < 256) {
//                val propertyElement = this.propertyElementFromID(propertyID)
//
//                // 2. Retrieve the type-associated data
//                val propertyStateChanged = when (propertyElement.propertyType) {
//                    COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR -> retrieveRGBStateData(
//                        propertyElement.propertyIndex,
//                        data
//                    )
//                    COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR -> retrieveExLevelSelectorData(
//                        propertyElement.propertyIndex,
//                        data
//                    )
//                    COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR -> retrieveSimpleTimeSelectorData(
//                        propertyElement.propertyIndex,
//                        data
//                    )
//
//                    // elapse-time selector mission
//
//                    COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR -> retrieveTimeFrameSelectorData(
//                        propertyElement.propertyIndex,
//                        data
//                    )
//                    COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR -> retrieveSimpleNavigatorData(
//                        propertyElement.propertyIndex,
//                        data
//                    )
//                    COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> retrieveBarGraphDisplayData(
//                        propertyElement.propertyIndex,
//                        data
//                    )
//
//                    // TODO: handle all complex types here!
//
//                    else -> true
//
//                }
//
//                // 3. Change UI Adapter
//                if (propertyStateChanged) {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:resolveComplexSData",
//                            "ComplexPropertyState has changed -> adapting changes to UI"
//                        )
//                    }
//
//                    var changedIndex = -1
//
//                    this.uIAdapterList.forEachIndexed { index, devicePropertyListContentInformation ->
//                        if (devicePropertyListContentInformation.internalElementIndex == propertyElement.propertyIndex) {
//                            devicePropertyListContentInformation.complexPropertyState =
//                                laRoomyDevicePropertyList.elementAt(propertyElement.propertyIndex).complexPropertyState
//                            changedIndex = index
//                        }
//                    }
//
//                    // 4. launch property changed event
//                    //      !! = but only if the complex-state-loop is not active (because the successive invocation will impact the UI performance)
//                    if(!this.complexStateLoopActive) {
//                        this.propertyCallback.onComplexPropertyStateChanged(
//                            changedIndex,
//                            this.uIAdapterList.elementAt(changedIndex).complexPropertyState
//                        )
//                    }
//                }
//
//                // 5. Check if the state-loop is active and continue or close it
//                if (this.complexStateLoopActive) {
//                    if(verboseLog) {
//                        Log.d("M:resolveComplexSData", "Complex state loop is active")
//                    }
//                    if (this.currentStateRetrievingIndex < this.complexStatePropertyIDs.size) {
//                        this.requestPropertyState(
//                            this.complexStatePropertyIDs.elementAt(this.currentStateRetrievingIndex)
//                        )
//                        this.currentStateRetrievingIndex++
//
//                        // test:
//                        if (this.currentStateRetrievingIndex == this.complexStatePropertyIDs.size){
//
//                            // works!!!
//
//                                if(verboseLog) {
//                                    Log.d(
//                                        "M:resolveComplexSData",
//                                        "Complex state loop reached invalid index -> close Loop"
//                                    )
//                                }
//                            this.complexStateLoopActive = false
//                            this.currentStateRetrievingIndex = -1
//
//                            //this.setDeviceTime()
//                        }
//
//                    } else {
//                        if(verboseLog) {
//                            Log.d(
//                                "M:resolveComplexSData",
//                                "Complex state loop reached invalid index -> close Loop"
//                            )
//                        }
//                        this.complexStateLoopActive = false
//                        this.currentStateRetrievingIndex = -1
//                    }
//                }
//            } else {
//                Log.e("M:resolveComplexSData", "Property-ID invalid ID: $propertyID")
//                applicationProperty.logControl("E: Property ID invalid: $propertyID")
//            }
//        }
//    }

    private fun resolveMultiComplexStateData(data: String, isName: Boolean){

        if(verboseLog) {
            Log.d(
                "M:RslveMultiCmplx",
                "ResolveMultiComplexStateData invoked - isName = $isName / data = $data"
            )
        }

        if(this.multiComplexPropertyPageOpen){
            val dataIndex = data.elementAt(4).toString().toInt()
            var name = ""

            if(isName){
                data.forEachIndexed { index, c ->
                    if(index > 4){
                        name += c
                    }
                }

                val mcd = MultiComplexPropertyData()
                mcd.isName = true
                mcd.dataName = name
                mcd.dataIndex = dataIndex

                // trigger event
                this.propertyCallback.onMultiComplexPropertyDataUpdated(mcd)
            } else {
                this.resolveSpecificMultiComplexStateData(data, dataIndex)
            }
        } else {
            // send error notification ?!
            Log.e(
                "M:RslveMultiCmplx",
                "ResolveMultiComplexStateData error: multi-complex page not open!"
            )
        }
    }

    private fun resolveSpecificMultiComplexStateData(data: String, dataIndex: Int){

        when(this.multiComplexTypeID){
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> {
                var stringData = ""
//                stringData += data.elementAt(5)
//                stringData += data.elementAt(6)
//                stringData += data.elementAt(7)

//                try {
//                    for (i in 5 until data.length) {
//                        stringData += data.elementAt(i)
//                    }
//                }
//                catch(e: IndexOutOfBoundsException){
//                    Log.e("M:resolveSMCSD", "Exception in \"resolveSpecificMultiComplexStateData\" Message: ${e.localizedMessage}")
//                    return
//                }

                data.forEachIndexed { index, c ->
                    if(index > 4){
                        stringData += c
                    }
                }

                val mcd = MultiComplexPropertyData()
                mcd.dataIndex = dataIndex
                mcd.dataValue = stringData.toInt()
                mcd.isName = false

                // trigger event
                this.propertyCallback.onMultiComplexPropertyDataUpdated(mcd)
            }
            else -> {
                // send error?
            }
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

    private fun retrieveRGBStateData(elementIndex: Int, data: String): Boolean {
        // check the transmission length first
        if(data.length < 19)
            return false
        else {
            var rVal = ""
            var gVal = ""
            var bVal = ""
            var command = ""

            for(i in 6..8)
                command += data.elementAt(i)
            for(i in 9..11)
                rVal += data.elementAt(i)
            for(i in 12..14)
                gVal += data.elementAt(i)
            for(i in 15..17)
                bVal += data.elementAt(i)

            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.commandValue = command.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueOne = rVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueTwo = gVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueThree = bVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.hardTransitionFlag =
                when(data.elementAt(18)){
                    '0' -> false
                    else -> true
                }

            return true
        }
    }

    private fun retrieveExLevelSelectorData(elementIndex: Int, data: String) : Boolean {
        // check the transmission length first
        if(data.length < 9){
            return false
        } else {
            var strLevel = ""

            for(i in 7..9)
                strLevel += data.elementAt(i)

            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueOne = strLevel.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.onOffState =
                when(data.elementAt(6)){
                    '1' -> true
                    else -> false
                }
            return true
        }
    }

    private fun retrieveSimpleTimeSelectorData(elementIndex: Int, data: String): Boolean {
        // check the transmission length first
        if(data.length < 13){
            return false
        } else {
            var hourVal = ""
            var minVal = ""
            var timeSetter = ""
            timeSetter += data.elementAt(6)

            for(i in 7..8)
                hourVal += data.elementAt(i)

            for(i in 9..10)
                minVal += data.elementAt(i)

            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.timeSetterIndex = timeSetter.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueOne = hourVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueTwo = minVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.onOffState =
                when(data.elementAt(11)){
                    '1' -> true
                    else -> false
                }
            return true
        }
    }

    private fun retrieveTimeFrameSelectorData(elementIndex: Int, data: String) : Boolean {
        // check the transmission length first
        if(data.length < 16){
            return false
        } else {
            var onTimeHourVal = ""
            var onTimeMinVal = ""
            var offTimeHourVal = ""
            var offTimeMinVal = ""

            // data[6] is reserved and could be used for extra data

            for(i in 7..8)
                onTimeHourVal += data.elementAt(i)

            for(i in 9..10)
                onTimeMinVal += data.elementAt(i)

            for(i in 11..12)
                offTimeHourVal += data.elementAt(i)

            for(i in 13..14)
                offTimeMinVal += data.elementAt(i)


            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueOne = onTimeHourVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueTwo = onTimeMinVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueThree = offTimeHourVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.commandValue = offTimeHourVal.toInt()
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.onOffState =
                when(data.elementAt(15)){
                    '1' -> true
                    else -> false
                }
            return true
        }
    }

    private fun retrieveSimpleNavigatorData(elementIndex: Int, data: String): Boolean{
        // check the transmission length first
        if(data.length < 11){
            return false
        } else {
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueOne =
                when(data.elementAt(6)){
                    '1' -> 1
                    else -> 0
                }
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueTwo =
                when(data.elementAt(7)){
                    '1' -> 1
                    else -> 0
                }
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueThree =
                when (data.elementAt(8)){
                    '1' -> 1
                    else -> 0
                }
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueFour =
                when (data.elementAt(9)){
                    '1' -> 1
                    else -> 0
                }
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueFive =
                when(data.elementAt(10)){
                    '1' -> 1
                    else -> 0
                }
            return true
        }
    }

    private fun retrieveBarGraphDisplayData(elementIndex: Int, data: String): Boolean{
        // check the transmission length first
        if(data.length < 7){
            return false
        } else {
            // at index 6 -> number of bars
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueOne = data.elementAt(6).toString().toInt()
            // at index 7 -> show value as bar-descriptor
            this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueTwo = data.elementAt(7).toString().toInt()
            // at index 8 until string-end -> fixed maximum value
            if(data.length > 8){
                var fixedMaxVal = ""

                data.forEachIndexed { index, c ->
                    if(index > 7){
                        fixedMaxVal += c
                    }
                }
                this.laRoomyDevicePropertyList.elementAt(elementIndex).complexPropertyState.valueThree = fixedMaxVal.toInt()
            }
        }
        return true
    }

    private fun startComplexStateDataLoop(){
        if(verboseLog) {
            Log.d(
                "M:startCompDataLoop",
                "ComplexStateDataLoop started - At first: Indexing the Property-IDs with complexStateData"
            )
        }
        // clear the ID-Array
        this.complexStatePropertyIDs.clear()
        // loop the properties and save the ones with complex state
        this.laRoomyDevicePropertyList.forEach {
            // start with the first complex state data element
            if(it.propertyType >= COMPLEX_PROPERTY_START_INDEX){
                this.complexStatePropertyIDs.add(it.propertyIndex)
            }
        }


        if(this.complexStatePropertyIDs.isNotEmpty()) {
            if(verboseLog) {
                Log.d(
                    "M:startCompDataLoop",
                    "Found ${this.complexStatePropertyIDs.size} Elements with ComplexStateData -> start collecting from device"
                )
            }

            this.currentStateRetrievingIndex = 0
            this.complexStateLoopActive = true

            this.requestPropertyState(
                this.complexStatePropertyIDs.elementAt(this.currentStateRetrievingIndex)
            )
            this.currentStateRetrievingIndex++
        }
        else {
            // if there are no complex types, the retrieving-process is finished here, so set the device time, otherwise it must be done at the end of the complex-state-loop
            //this.setDeviceTime()
        }
    }

    private fun requestPropertyState(ID: Int){
        val value = a8BitValueToString(ID)
        val requestString = "D$value$"
        this.sendData(requestString)
    }

    private fun groupIDFromStartEntry(data: String) : Int {
        var groupID = ""

        data.forEachIndexed { index, c ->
            when (index) {
                0 -> if (c != 'G')return -1
                1 -> if (c != 'I')return -1
                2 -> if (c != ':')return -1
                3 -> if (c != 'S')return -1
                4 -> groupID += c
                5 -> groupID += c
                6 -> groupID += c
            }
        }
        // convert group ID to Int and check the value
        val id =
            groupID.toInt()

        return if(id < 256 && id > -1) { id } else -1
    }

    private fun propertyIDFromStartEntry(data: String) : Int {
        var propertyID = ""

        data.forEachIndexed { index, c ->
            when (index) {
                0 -> if (c != 'P') return -1
                1 -> if (c != 'D') return -1
                2 -> if (c != ':') return -1
                3 -> if (c != 'S') return -1
                4 -> propertyID += c
                5 -> propertyID += c
                6 -> propertyID += c
            }
        }
        // convert property ID to Int and check the value
        val id =
            propertyID.toInt()

        return if (id < 256 && id > -1) { id } else -1
    }

    private fun setDeviceTime(){
        // get the time and send the client command to the device
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)// 24 hour format!
        val min = calendar.get(Calendar.MINUTE)
        val sec = calendar.get(Calendar.SECOND)
        val outString = "ScT&${a8BitValueAsTwoCharString(hour)}${a8BitValueAsTwoCharString(min)}${a8BitValueAsTwoCharString(sec)}$"
        if(verboseLog) {
            Log.d(
                "M:setDeviceTime",
                "Sending current local time to the device. Output Data is: $outString"
            )
        }
        this.sendData(outString)
    }

    fun doComplexPropertyStateRequestForID(ID: Int) {
        val str = a8BitValueToString(ID)
        this.sendData("D$str$")
    }

    private fun sendBindingRequest(useCustomKey: Boolean){

        val passKey: String

        // at first look for a shared key for the device
        val bindingPairManager = BindingPairManager(this.activityContext)
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
            this.callback.onComponentError(this.activityContext.getString(R.string.Error_SavedBindingKeyIsEmpty))
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


    private fun startUpdateStackProcessing(){
        if(this.elementUpdateList.isNotEmpty()) {
            if(!this.updateStackProcessActive) {

                if(verboseLog) {
                    Log.d("M:startUSP", "Update stack processing started..")
                }
                this.updateStackProcessActive = true

                val updateElement = this.elementUpdateList.elementAt(0)

                when (updateElement.elementType) {
                    PROPERTY_ELEMENT -> {
                        when (updateElement.updateType) {
                            UPDATE_TYPE_ELEMENT_DEFINITION -> {
                                sendSinglePropertyRequest(updateElement.elementIndex)
                            }
                            UPDATE_TYPE_DETAIL_DEFINITION -> {
                                sendSinglePropertyResolveRequest(updateElement.elementID)
                            }
                        }
                    }
                    GROUP_ELEMENT -> {
                        when (updateElement.updateType) {
                            UPDATE_TYPE_ELEMENT_DEFINITION -> {
                                //sendSinglePropertyGroupRequest(updateElement.elementIndex)
                            }
                            UPDATE_TYPE_DETAIL_DEFINITION -> {
                                sendSingleGroupDetailRequest(updateElement.elementID)
                            }
                        }
                    }
                }
            }
        } else {
            this.updateStackProcessActive = false
        }
    }

//    private fun processElementUpdateStack(data: String): Boolean {
//        return if(this.updateStackProcessActive) {
//            if(verboseLog) {
//                Log.d("M:USP", "ProcessElementUpdateStack invoked. Check transmission..")
//            }
//
//            val singleAction = this.checkSingleAction(data)
//            val processed = singleAction != SINGLEACTION_NOT_PROCESSED
//
//            // check if the first element was processed
//            if (singleAction == SINGLEACTION_PROCESSING_COMPLETE) {
//                if(verboseLog) {
//                    Log.d(
//                        "M:USP",
//                        "ProcessElementUpdateStack - Element processed - remove the last and look for other elements in the array"
//                    )
//                }
//
//                // remove the element
//                this.elementUpdateList.removeAt(0)
//                // check if there are elements left in the array
//                if(this.elementUpdateList.isNotEmpty()){
//
//                    val updateElement = this.elementUpdateList.elementAt(0)
//
//                    if(verboseLog) {
//                        Log.d(
//                            "M:USP",
//                            "ProcessElementUpdateStack - Request next element: ID: ${updateElement.elementID} Index: ${updateElement.elementIndex}"
//                        )
//                    }
//
//                    when (updateElement.elementType) {
//                        PROPERTY_ELEMENT -> {
//                            when (updateElement.updateType) {
//                                UPDATE_TYPE_ELEMENT_DEFINITION -> {
//                                    sendSinglePropertyRequest(updateElement.elementIndex)
//                                }
//                                UPDATE_TYPE_DETAIL_DEFINITION -> {
//                                    sendSinglePropertyResolveRequest(updateElement.elementID)
//                                }
//                            }
//                        }
//                        GROUP_ELEMENT -> {
//                            when (updateElement.updateType) {
//                                UPDATE_TYPE_ELEMENT_DEFINITION -> {
// //                                   sendSinglePropertyGroupRequest(updateElement.elementIndex)
//                                }
//                                UPDATE_TYPE_DETAIL_DEFINITION -> {
//                                    sendSingleGroupDetailRequest(updateElement.elementID)
//                                }
//                            }
//                        }
//                    }
//                } else {
//                    if(verboseLog) {
//                        Log.d(
//                            "M:USP",
//                            "ProcessElementUpdateStack - NO MORE ELEMENTS LEFT - Stop update process"
//                        )
//                    }
//                    this.updateStackProcessActive = false
//                }
//            }
//            processed
//        } else {
//            false
//        }
//
//    }

    fun isMultiComplexProperty(propertyID: Int) : Boolean{

        return when(this.propertyTypeFromID(propertyID)){
            COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY -> true
            // MARK: add all multicomplex properties here
            else -> false
        }
    }

    fun notifyBackNavigationToDeviceMainPage() {
        this.multiComplexPageID = -1
        this.multiComplexPropertyPageOpen = false
        sendData(this.navigatedToDeviceMainPageNotification)
        // TODO: !!
    }

    fun notifyMultiComplexPropertyPageInvoked(propertyID: Int) {
        this.multiComplexPageID = propertyID
        this.multiComplexPropertyPageOpen = true
        this.multiComplexTypeID = this.propertyTypeFromID(propertyID)

        if(verboseLog) {
            Log.d(
                "M:NotifyMCPPI",
                "Multi-Complex-Property-Page invoked notification send for property-ID: ${this.multiComplexPageID} and type-ID: ${this.multiComplexTypeID}"
            )
        }
        sendData("${this.multiComplexPropertyPageInvokedStartEntry}${a8BitValueToString(propertyID)}$")
    }

    fun enableDeviceBinding(passKey: String){
        this.sendData("$enableBindingSetterCommandEntry$passKey$")
        this.isBindingRequired = true
    }

    fun releaseDeviceBinding(){
        this.sendData(releaseDeviceBindingCommand)
        this.isBindingRequired = false
    }

//    private fun tryUseSavedPassKeyForSpecificMacAddress(macAddress: String) : String {
//        val bindingPairManager = BindingPairManager(this.activityContext)
//        return bindingPairManager.lookUpForPassKeyWithMacAddress(macAddress)
//    }

//    fun formatIncomingData(data: String) : String {
//
//        var dOut = ""
//
//        data.forEach {
//            if((it != '\r') && (it != '\n')){
//                dOut += it
//            }
//        }
//        return dOut
//    }

    private fun stopAllPendingLoopsAndResetParameter(){

        // TODO: make sure to cover all parameter

        this.propertyLoopActive = false
        this.propertyNameResolveLoopActive = false
        this.groupLoopActive = false
        this.groupInfoLoopActive = false
        this.complexStateLoopActive = false
        this.deviceHeaderRecordingActive = false

        this.complexStatePropertyIDs.clear()
        this.deviceInfoHeaderData.clear()

        this.currentPropertyResolveID = -1
        this.currentGroupResolveIndex = -1
        this.groupRequestIndexCounter = -1
        this.currentStateRetrievingIndex = -1

        this.propertyNameResolveSingleAction = false
        this.propertyGroupNameResolveSingleAction = false
        this.propertyConfirmationModeActive = false
        this.groupDetailChangedOnConfirmation = false
        this.propertyDetailChangedOnConfirmation = false
        this.propertyUpToDate = false // TODO: is this right???
    }

    // new helpers

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



    // the callback definition for the event handling in the calling class
    interface BleEventCallback : Serializable{
        fun onConnectionStateChanged(state: Boolean){}
        fun onConnectionAttemptFailed(message: String){}
        fun onDataReceived(data: String?){}
        fun onDataSent(data: String?){}
        fun onComponentError(message: String){}
        fun onAuthenticationSuccessful(){}
        fun onBindingPasskeyRejected(){}
        fun onDeviceReadyForCommunication(){}
        fun onConnectionTestSuccess(){}
    }

    interface PropertyCallback: Serializable {
        fun onPropertyDataRetrievalCompleted(properties: ArrayList<LaRoomyDeviceProperty>){}
        fun onGroupDataRetrievalCompleted(groups: ArrayList<LaRoomyDevicePropertyGroup>){}
        fun onPropertyDataChanged(propertyIndex: Int, propertyID: Int){}// if the index is -1 the whole data changed or the changes are not indexed -> iterate the array and check the .hasChanged -Parameter
        fun onPropertyGroupDataChanged(groupIndex: Int, groupID: Int){}// if the index is -1 the whole data changed or the changes are not indexed -> iterate the array and check the .hasChanged -Parameter
        fun onCompletePropertyInvalidated(){}
        fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>){}
        fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation){}
        fun onUIAdaptableArrayItemChanged(index: Int){}
        fun onSimplePropertyStateChanged(UIAdapterElementIndex: Int, newState: Int){}
        fun onComplexPropertyStateChanged(UIAdapterElementIndex: Int, newState: ComplexPropertyState){}
        fun onDeviceHeaderChanged(deviceHeaderData: DeviceInfoHeaderData){}
        fun onMultiComplexPropertyDataUpdated(data: MultiComplexPropertyData){}
        fun onDeviceNotification(notificationID: Int){}
    }
}