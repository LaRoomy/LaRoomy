package com.example.laroomy

import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.os.*
import android.util.Log
import android.widget.SeekBar
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList

private const val MAX_CONNECTION_ATTEMPTS = 10

const val LAROOMYDEVICETYPE_NONE = 0
const val LAROOMYDEVICETYPE_XNG = 1
const val LAROOMYDEVICETYPE_CTX = 2

class LaRoomyDeviceProperty{

    var propertyIndex: Int = -1 // invalid marker == -1
    var propertyID: Int = -1
    var propertyType: Int = -1
    var propertyDescriptor: String = "unset"
    var isGroupMember = false
    var groupID = -1
    var imageID = -1
    var hasChanged = false

    override fun equals(other: Any?): Boolean {
        // check if this is the same reference
        if(this === other)return true
        // check if other is an invalid type
        if(other !is LaRoomyDeviceProperty)return false
        // check data equality
        if(other.propertyIndex != this.propertyIndex)return false
        if(other.propertyID != this.propertyID)return false
        if(other.propertyType != this.propertyType)return false
        //if(other.propertyDescriptor != this.propertyDescriptor)return false // the comparison of this member is not reasonable, because the element is not defined in the property-string
        if(other.isGroupMember != this.isGroupMember)return false
        if(other.groupID != this.groupID)return false
        if(other.imageID != this.imageID)return false
        // all is the same so return true
        return true
    }

    fun needNavigation() : Boolean{
        // if the property-type does not need navigation -> return false
        return when(this.propertyType){
            PROPERTY_TYPE_BUTTON -> false
            PROPERTY_TYPE_SWITCH -> false
            PROPERTY_TYPE_LEVEL_SELECTOR -> false
            PROPERTY_TYPE_LEVEL_INDICATOR -> false
            PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> false
            else -> true
        }
    }

    fun fromString(string: String){
        // generate member content from string:
        try {
            if (string.isNotEmpty()) {
                var propID = ""
                var propType = ""
                var propIndex = ""
                var grID = ""
                var imgID = ""

                string.forEachIndexed { index, c ->
                    when (index) {
                        0 -> if (c != 'I') return
                        1 -> if (c != 'P') return
                        2 -> if (c != 'R') return
                        3 -> if (c != '0') propID += c
                        4 -> if (c != '0') propID += c
                        5 -> propID += c
                        6 -> if (c != '0') propType += c
                        7 -> if (c != '0') propType += c
                        8 -> propType += c
                        9 -> if (c != '0') propIndex += c
                        10 -> if (c != '0') propIndex += c
                        11 -> propIndex += c
                        12 -> if(isNumber(c)){
                            if(c != '0')
                                grID += c
                        }
                        13 -> if(isNumber(c)){
                            if(c != '0')
                                grID += c
                        }
                        14 -> if(isNumber(c)) grID += c
                        15 -> if(c != '0') imgID += c
                        16 -> if(c != '0') imgID += c
                        17 -> imgID += c
                    }
                }
                Log.d("M:DevProp:fromString", "Data Recorded - Results:")
                Log.d("M:DevProp:fromString", "PropertyID: $propID")
                Log.d("M:DevProp:fromString", "PropertyType: $propType")
                Log.d("M:DevProp:fromString", "PropertyIndex: $propIndex")
                Log.d("M:DevProp:fromString", "PropertyImageID: $imgID")

                this.propertyID = propID.toInt()
                this.propertyType = propType.toInt()
                this.propertyIndex = propIndex.toInt()
                this.imageID = imgID.toInt()

                if(grID.isNotEmpty()){
                    this.groupID = grID.toInt()
                    this.isGroupMember = true
                    Log.d("M:DevProp:fromString", "isGroupMember: $isGroupMember / GroupID: $groupID")
                }
            }
            Log.d(
                "M:LRDevice:FromString",
                "LaRoomy device property string read:\n -PropertyID: ${this.propertyID}\n -PropertyType: ${this.propertyType}\n - PropertyIndex: ${this.propertyIndex}\n - PropertyImageID: ${this.imageID}"
            )
        }
        catch(except: Exception){
            Log.d("M:LDP:Prop:fromString", "Exception occurred: ${except.message}")
        }
     }

    fun checkRawEquality(ldp:LaRoomyDeviceProperty) :Boolean {
        return ((ldp.propertyType == this.propertyType)&&(ldp.propertyID == this.propertyID)&&(ldp.propertyIndex == this.propertyIndex)&&(ldp.imageID == this.imageID))
    }

    private fun isNumber(char: Char): Boolean {
        return when(char){
            '0' -> true
            '1' -> true
            '2' -> true
            '3' -> true
            '4' -> true
            '5' -> true
            '6' -> true
            '7' -> true
            '8' -> true
            '9' -> true
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = propertyIndex
        result = 31 * result + propertyID
        result = 31 * result + propertyType
        result = 31 * result + propertyDescriptor.hashCode()
        result = 31 * result + isGroupMember.hashCode()
        result = 31 * result + groupID
        result = 31 * result + imageID
        result = 32 * result + hasChanged.hashCode()
        return result
    }
}

class LaRoomyDevicePropertyGroup{

    var groupIndex = -1
    var groupID = -1
    var groupName = "unset"
    var memberCount = 0
    var memberIDs = ArrayList<Int>()
    var imageID = -1
    var hasChanged = false

    override fun equals(other: Any?): Boolean {
        // check reference equality
        if(other === this)return true
        // check for invalid type
        if(other !is LaRoomyDevicePropertyGroup)return false
        // check data
        if(other.groupIndex != this.groupIndex)return false
        if(other.groupID != this.groupID)return false
        //if(other.groupName != this.groupName)return false // the comparison of this member is not reasonable, because the element is not defined in the group-string
        if(other.memberCount != this.memberCount)return false
        if(other.memberIDs != this.memberIDs)return false
        if(other.imageID != this.imageID)return false
        // all data is the same -> return true
        return true
    }

    fun fromString(groupString: String){
        // generate member content from string:
        try {
            if (groupString.isNotEmpty()) {
                var localGroupID = ""
                var localGroupIndex = ""
                var memberAmount = ""
                var imgID = ""

                groupString.forEachIndexed { index, c ->
                    when (index) {
                        0 -> if (c != 'I') return
                        1 -> if (c != 'P') return
                        2 -> if (c != 'G') return
                        3 -> if (c != '0') localGroupID += c
                        4 -> if (c != '0') localGroupID += c
                        5 -> localGroupID += c
                        6 -> if (c != '0') localGroupIndex += c
                        7 -> if (c != '0') localGroupIndex += c
                        8 -> localGroupIndex += c
                        9 -> if (c != '0') memberAmount += c
                        10 -> if (c != '0') memberAmount += c
                        11 -> memberAmount += c
                        12 -> if(c != '0') imgID += c
                        13 -> if(c != '0') imgID += c
                        14 -> imgID += c
                    }
                }
                Log.d("M:PropGroup:fromString", "Data Recorded - Results:")
                Log.d("M:PropGroup:fromString", "GroupID: $localGroupID")
                Log.d("M:PropGroup:fromString", "GroupIndex: $localGroupIndex")
                Log.d("M:PropGroup:fromString", "MemberAmount: $memberAmount")
                Log.d("M:PropGroup:fromString", "GroupImageID: $imgID")

                this.groupID = localGroupID.toInt()
                this.groupIndex = localGroupIndex.toInt()
                this.memberCount = memberAmount.toInt()
                this.imageID = imgID.toInt()
            }
            Log.d(
                "M:PropGroup:fromString",
                "LaRoomy device property GROUP string read:\n -GroupID: ${this.groupID}\n -GroupIndex: ${this.groupIndex}\n - MemberAmount: ${this.memberCount}\n - GroupImageID: ${this.imageID}"
            )
        }
        catch(except: Exception){
            Log.d("M:LDP:Group:fromString", "Exception occurred: ${except.message}")
        }
    }

    fun checkRawEquality(ldpg: LaRoomyDevicePropertyGroup) : Boolean {
        return ((this.groupIndex == ldpg.groupIndex)&&(this.groupID == ldpg.groupID)&&(this.imageID == ldpg.imageID)&&(this.memberCount == ldpg.memberCount))
    }

    fun setMemberIDs(id1: Int, id2: Int, id3: Int, id4: Int, id5: Int){
        this.memberIDs.clear()
        this.memberIDs.add(id1)
        this.memberIDs.add(id2)
        this.memberIDs.add(id3)
        this.memberIDs.add(id4)
        this.memberIDs.add(id5)
    }

    override fun hashCode(): Int {
        var result = groupIndex
        result = 31 * result + groupID
        result = 31 * result + groupName.hashCode()
        result = 31 * result + memberCount
        result = 31 * result + memberIDs.hashCode()
        result = 31 * result + imageID
        result = 31 * result + hasChanged.hashCode()
        return result
    }
}

class LaRoomyDevicePresentationModel {
    var name = ""
    var address = ""
    var type = 0
}

class DevicePropertyListContentInformation : SeekBar.OnSeekBarChangeListener{

    var handler: OnPropertyClickListener? = null

    var canNavigateForward = false
    var isGroupMember = false
    var elementType = SEPARATOR_ELEMENT
    var indexInsideGroup = -1
    var globalIndex = -1
    var elementText = ""
    var elementID = -1
    var imageID = -1
    var propertyType = -1
    var initialElementValue = -1

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        this.handler?.onSeekBarPositionChange(this.globalIndex, progress, SEEK_BAR_PROGRESS_CHANGING)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        this.handler?.onSeekBarPositionChange(this.globalIndex, -1, SEEK_BAR_START_TRACK)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        this.handler?.onSeekBarPositionChange(this.globalIndex, -1, SEEK_BAR_STOP_TRACK)
    }
}

class BLEConnectionManager {

    // callback implementation for BluetoothGatt
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            when(newState){
                BluetoothProfile.STATE_CONNECTED -> {

                    //currentDevice = gatt?.device ??????

                    isConnected = true

                    Log.d("M:CB:ConStateChanged", "Connection-state changed to: CONNECTED")
                    callback.onConnectionStateChanged(true)
                    Log.d("M:CB:ConStateChanged", "Invoking discoverServices()")
                    gatt?.discoverServices()// start to discover the services of the device
                }
                BluetoothProfile.STATE_DISCONNECTED -> {

                    isConnected = false

                    Log.d("M:CB:ConStateChanged", "Connection-state changed to: DISCONNECTED")
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
                        Log.d("M:CB:onServicesDisc", "Service discovered. Index: $index ServiceUUID: ${bluetoothGattService.uuid} Type: ${bluetoothGattService.type}")

                        if(bluetoothGattService.uuid == serviceUUID)
                        {
                            // for my case here, we use the service with the short id 0xFFE0 (serviceUUID)
                            Log.d("M:CB:onServicesDisc", "Correct service found - retrieving characteristics for the service")

                            // iterate through the characteristics in the service
                            bluetoothGattService.characteristics.forEach {

                                Log.d("M:CB:onServicesDisc", "Characteristic found: UUID: ${it.uuid}  InstanceID: ${it.instanceId}")

                                // set the characteristic notification for the desired characteristic in the service (uuid:0xFFE1)
                                if(it.uuid == characteristicUUID) {

                                    Log.d("M:CB:onServicesDisc", "Correct characteristic found - enable notifications")
                                    // save characteristic
                                    gattCharacteristic = it
                                    //gattCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED//WRITE_TYPE_NO_RESPONSE
                                    // enable notification on the device
                                    gatt.setCharacteristicNotification(gattCharacteristic, true)

                                    Log.d("M:CB:onServicesDisc", "Set Descriptor")
                                    // set the correct descriptor for this characteristic
                                    val descriptor = gattCharacteristic.getDescriptor(clientCharacteristicConfig)
                                        .apply {
                                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        }
                                    gatt.writeDescriptor(descriptor)

                                    // report object ready
                                    callback.onDeviceReadyForCommunication()

                                    //sendData(authenticationString)          // does not work - delay???
                                }
                            }
                        }
                    }
                }
                else -> {
                    Log.d("M:CB:onServicesDisc", "Gatt-Status: $status")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            // log:
            Log.d("M:CB:CharChanged", "Characteristic changed. Value: ${characteristic?.value}")

            // get data
            val dataAsString = characteristic?.getStringValue(0)

            Log.d("M:CB:CharChanged", "Characteristic changed. String-Value: $dataAsString")

            // check authentication
            if(authRequired){

                Log.d("M:CB:CharChanged", "Data Received - Authentication Required")

                if(dataAsString == authenticationResponse){
                    Log.d("M:CB:CharChanged", "Data Received - Authentication successful - Device ID confirmed")
                    authRequired = false
                    authenticationSuccess = true
                    // save the device address (but only if the authentication was successful)
                    saveLastSuccessfulConnectedDeviceAddress(gatt?.device?.address ?: "")
                    callback.onAuthenticationSuccessful()
                }
                else
                    Log.d("M:CB:CharChanged", "!Warning: Authentication failed!")
            }
            else {
                var dataProcessed = checkDeviceNotificationEvent(dataAsString ?: "error")

                if(!dataProcessed) {

                    dataProcessed = checkSingleAction(dataAsString ?: "error")

                    if(!dataProcessed) {
                        // check if one of the property-retrieving-loops is active
                        if (propertyLoopActive) {
                            Log.d(
                                "M:CB:CharChanged",
                                "Data Received - Property Loop is active - check for property-string"
                            )
                            // try to add the device property, if the end is reached the loop will be cut off
                            dataProcessed = addDeviceProperty(dataAsString ?: "error")
                        }
                        if (propertyNameResolveLoopActive) {
                            Log.d(
                                "M:CB:CharChanged",
                                "Data Received - PropertyName resolve Loop is active - check for name-strings"
                            )
                            // check and handle property-name-resolve-request
                            dataProcessed = resolvePropertyName(dataAsString ?: "error")
                        }
                        if (groupLoopActive) {
                            Log.d(
                                "M:CB:CharChanged",
                                "Data Received - group indexing Loop is active - check for group-id for indexes"
                            )
                            // try to add the group, if the end is reached, the loop will be cut off
                            dataProcessed = addPropertyGroup(dataAsString ?: "error")
                        }
                        if (groupInfoLoopActive) {
                            Log.d(
                                "M:CB:CharChanged",
                                "Data Received - group-detail-info Loop is active - check the detailed info for the given ID"
                            )
                            // if the detailed group info loop is active, check the string for group info
                            dataProcessed = resolveGroupInfo(dataAsString ?: "error")
                        }
                    }
                }
                // TODO: important!!! enable the event-filter "dataProcessed" in the laRoomy-app
                // launch event
                if(!dataProcessed){
                    callback.onDataReceived(dataAsString)
                }

                // only temporary for the ble-command-tester-app
                //callback.onDataReceived(dataAsString)//  TODO: only execute this if the reception is not part of the retrieving loops
            }
        }
    }

    fun reAlignContextObjects(cActivity: Activity, cContext: Context, eventHandlerObject: BleEventCallback){
        this.callingActivity = cActivity
        this.activityContext = cContext
        this.callback = eventHandlerObject
    }

    private fun groupRequestLoopRequired():Boolean{
        var ret = false
        if(this.laRoomyDevicePropertyList.isNotEmpty()){
            this.laRoomyDevicePropertyList.forEach {
                if(it.isGroupMember){
                    ret = true
                }
            }
        }
        return ret
    }

    fun setPropertyEventHandler(pEvents: PropertyCallback){
        this.propertyCallback = pEvents
    }

    // constant properties (regarding the device!) ////////////////////////
    private val serviceUUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val characteristicUUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
    private val clientCharacteristicConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val authenticationString = "xPsM0-33wSp_mmT$"
    val authenticationResponse = "Auth:rsp:true"
    private val propertyLoopEndIndication = "RSP:A:PEND$"
    private val groupLoopEndIndication = "RSP:E:PGEND$"
    private val propertyChangedNotificationEntry = "DnPcx1="
    private val propertyGroupChangedNotificationEntry = "DnPGc=t"
    /////////////////////////////////////////////////

    var isConnected:Boolean = false
        private set
    var authenticationSuccess = false
        private set

    private var authRequired = true
    private var propertyLoopActive = false
    private var propertyNameResolveLoopActive = false
    private var groupLoopActive = false
    private var groupInfoLoopActive = false
    private var propertyConfirmationModeActive = false
    private var propertyNameResolveSingleAction = false
    private var propertyGroupNameResolveSingleAction = false
    private var singleRetrievingAction = false
    private var propertyUpToDate = false
    private var dataReadyToShow = false
    private var currentPropertyResolveID = -1 // initialize with invalid marker
    private var currentPropertyResolveIndex = -1 // initialize with invalid marker
    private var currentGroupResolveID = -1 // initialize with invalid marker
    private var currentGroupResolveIndex = -1 // initialize with invalid marker
    private lateinit var activityContext: Context
    private lateinit var callingActivity: Activity
    private lateinit var callback: BleEventCallback
    private lateinit var propertyCallback: PropertyCallback
    var currentDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    lateinit var gattCharacteristic: BluetoothGattCharacteristic

    var laRoomyDevicePropertyList = ArrayList<LaRoomyDeviceProperty>()
    var laRoomyPropertyGroupList = ArrayList<LaRoomyDevicePropertyGroup>()
    var uIAdapterList = ArrayList<DevicePropertyListContentInformation>()

    private val scanResultList: MutableList<ScanResult?> = ArrayList()

    private lateinit var mHandler: Handler

    val isPropertyUpToDate: Boolean
    get() = this.propertyUpToDate

    val isUIDataReady: Boolean
    get() = this.dataReadyToShow

    private val requestEnableBT: Int = 13

    private var propertyDetailChangedOnConfirmation = false
    private var groupDetailChangedOnConfirmation = false

    private var mScanning: Boolean = false

    private var preselectIndex: Int = -1

    private var connectionAttemptCounter = 0// remove!

    private var propertyRequestIndexCounter = 0
    private var groupRequestIndexCounter = 0

    fun clear(){
        this.bluetoothGatt?.close()
        this.bluetoothGatt = null
        this.preselectIndex = -1
        this.scanResultList.clear()
        this.currentDevice = null
        this.isConnected = false
        this.authRequired = true
        this.propertyUpToDate = false
        this.authenticationSuccess = false
        this.uIAdapterList.clear()
    }

    fun connectToDeviceWithInternalScanList(macAddress: String?){

        var i: Int = -1
        // search for the mac-address in the result list and try to connect
        this.scanResultList.forEachIndexed { index, scanResult ->

            if(scanResult?.device?.address == macAddress){
                i = index
            }
        }
        when(i){
            -1 -> return
            else -> {
                this.currentDevice = scanResultList[i]?.device

                this.bluetoothGatt = this.currentDevice?.connectGatt(this.activityContext, false, this.gattCallback)

            }
        }
    }

    fun getLastConnectedDeviceAddress() : String {
        val sharedPref = callingActivity.getSharedPreferences(
            callingActivity.getString(R.string.FileKey_BLEManagerData),
            Context.MODE_PRIVATE)
        val address = sharedPref.getString(
            callingActivity.getString(R.string.DataKey_LastSuccessfulConnectedDeviceAddress),
            "not found")
        return address ?: ""
    }

    fun connectToLastSuccessfulConnectedDevice() {

        val sharedPref = callingActivity.getSharedPreferences(
            callingActivity.getString(R.string.FileKey_BLEManagerData),
            Context.MODE_PRIVATE)
        val address = sharedPref.getString(
            callingActivity.getString(R.string.DataKey_LastSuccessfulConnectedDeviceAddress),
            "not found")

        if(address == "not found")
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

    fun connectToCurrentDevice(){
        // TODO: This is a mark for the use of this method in another app: checking the bond state is not necessary, so this method is obsolete
        if(this.currentDevice?.bondState == BluetoothDevice.BOND_NONE){
            // device is not bonded, the connection attempt will raise an prompt for the user to connect
            checkBondingStateWithDelay()
        }
        this.connect()
    }

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
            Log.d("M:sendData", "Unexpected error, bluetooth device not connected")
            this.callback.onComponentError("Unexpected error, bluetooth device not connected")
        }
        else {
            Log.d("M:sendData", "writing characteristic: data: $data")

            this.gattCharacteristic.setValue(data)

            if (this.bluetoothGatt == null)
                Log.d("M:sendData", "Member bluetoothGatt was null!")

            this.bluetoothGatt?.writeCharacteristic(this.gattCharacteristic)

            // raise event:
            this.callback.onDataSent(data)
        }
    }

    private fun checkBondingStateWithDelay(){
        if(this.connectionAttemptCounter < MAX_CONNECTION_ATTEMPTS) {

            Handler().postDelayed({
                // check if device is bonded now
                if ((this.currentDevice?.bondState == BluetoothDevice.BOND_NONE)
                    || (this.currentDevice?.bondState == BluetoothDevice.BOND_BONDING)
                ) {
                    // count the number of attempts
                    this.connectionAttemptCounter++
                    // call the delay again
                    checkBondingStateWithDelay()
                } else {
                    connect()
                }
            }, 4000)
        }
    }

    fun selectCurrentDeviceFromInternalList(index: Int){

        if(this.scanResultList.isNotEmpty())
            this.currentDevice = this.scanResultList[index]?.device
        else
            this.currentDevice = this.bondedList?.elementAt(index)
    }

    val bondedList: Set<BluetoothDevice>? by lazy(LazyThreadSafetyMode.NONE){
        this.bleAdapter?.bondedDevices
    }

    var bondedLaRoomyDevices = ArrayList<LaRoomyDevicePresentationModel>()
    get() {
        field.clear()
        this.bleAdapter?.bondedDevices?.forEach {
            //if(it.uuids.elementAt(0).equals(this.serviceUUID) && (it.name.startsWith("LaRoomy")))
            if(it.name.startsWith("Laroomy"))
            {
                val device = LaRoomyDevicePresentationModel()
                device.name = it.name
                device.address = it.address
                device.type = laRoomyDeviceTypeFromName(it.name)
                field.add(device)
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

    fun checkBluetoothEnabled(){

        // TODO: check if this function works!

        Log.d("M:bluetoothEnabled?", "Check if bluetooth is enabled")

        bleAdapter?.takeIf{it.isDisabled}?.apply{
            // this lambda expression will be applied to the object(bleAdapter) (but only if "isDisabled" == true)
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            callingActivity.startActivityForResult(enableBtIntent, requestEnableBT)
        }
    }

    fun close(){
        this.bluetoothGatt?.close()
        this.bluetoothGatt = null
        this.isConnected = false
    }

    private fun laRoomyDeviceTypeFromName(name: String): Int {
        return when(name){
            "LaRoomy XNG" -> LAROOMYDEVICETYPE_XNG
            "LaRoomy CTX" -> LAROOMYDEVICETYPE_CTX
            else -> LAROOMYDEVICETYPE_NONE
        }
    }

    fun saveLastSuccessfulConnectedDeviceAddress(address: String){

        Log.d("M:SaveAddress", "Saving address of successful connected device - address: $address")

        if(address.isNotEmpty()) {
            val sharedPref =
                this.callingActivity.getSharedPreferences(
                    this.callingActivity.getString(R.string.FileKey_BLEManagerData),
                    Context.MODE_PRIVATE
                )
            with(sharedPref.edit()) {
                putString(
                    callingActivity.getString(R.string.DataKey_LastSuccessfulConnectedDeviceAddress),
                    address
                )
                commit()
            }
        }
    }

    fun startDevicePropertyListing(){

        // TODO: what is if the property array is already filled? Init the confirm process! Or erase????

        if(this.isConnected){
            // only start if there is no pending process/loop
            if(!(this.propertyLoopActive || this.propertyNameResolveLoopActive || this.groupLoopActive || this.groupInfoLoopActive)) {
                Log.d("M:StartPropListing", "Device property listing started.")
                this.propertyRequestIndexCounter = 0
                this.propertyLoopActive = true
                this.propertyUpToDate = false
                // only clear the list if this is not the confirmation mode
                if(!this.propertyConfirmationModeActive) {
                    this.dataReadyToShow = false
                    this.laRoomyDevicePropertyList.clear()
                }
                // start:
                this.sendData("A000$")// request first index (0)
            }
        }
    }

    fun addDeviceProperty(property: String) :Boolean {
        var stringProcessed = false

        if(property == this.propertyLoopEndIndication){
            // log
            Log.d("M:addDeviceProperty", "Device property loop end received - stopping loop.")
            this.propertyLoopActive = false
            // set return value
            stringProcessed = true
            // start resolve loop
            this.startRetrievingPropertyNames()
        }
        else {
            if(property.startsWith("IPR")){
                // log
                Log.d("M:addDeviceProperty", "Device property string received: $property")
                // add the element (only if the confirmation-mode is inactive)
                val laRoomyDeviceProperty = LaRoomyDeviceProperty()
                laRoomyDeviceProperty.fromString(property)
                if(this.propertyConfirmationModeActive){
                    // confirmation mode active: compare the property but note: at this point we can not use
                    // the overridden member "equals(...)" to compare, because only the raw data is recorded to the class
                    // -> so use the .checkRawEquality(...) member!
                    if(!this.laRoomyDevicePropertyList.elementAt(laRoomyDeviceProperty.propertyIndex).checkRawEquality(laRoomyDeviceProperty)){
                        // log:
                        Log.d("M:addDeviceProperty", "Confirmation-Process active: This property data does not confirm with the saved structure.\nPropertyID: ${laRoomyDeviceProperty.propertyID}\nPropertyIndex: ${laRoomyDeviceProperty.propertyIndex}")
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
        Log.d("M:requestNextProperty", "Send next request index - message: $data")
        // send the string
        this.sendData(data)
    }

    private fun startRetrievingPropertyNames(){
        Log.d("M:RetrievingPropNames", "Starting to resolve the property descriptions")
        if(this.isConnected){
            if(this.laRoomyDevicePropertyList.isNotEmpty()) {

                this.currentPropertyResolveIndex = 0

                val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                    "de" -> 1
                    else -> 0
                }
                var id = this.laRoomyDevicePropertyList.elementAt(currentPropertyResolveIndex).propertyID
                var hundred = 0
                var tenth = 0
                val single: Int

                if(id > 100) hundred = (id / 100)
                id -= (hundred*100)
                if(id > 10) tenth = (id / 10)
                id -= (tenth*10)
                single = id

                val requestString = "B$languageIdentificationChar$hundred$tenth$single$"

                Log.d("M:RetrievingPropNames", "Sending Request String for the first element: $requestString")
                this.propertyNameResolveLoopActive = true
                this.sendData(requestString)
            }
        }
    }

    fun resolvePropertyName(propertyName: String) :Boolean {

        var stringProcessed = false

        Log.d("M:resolvePropNames", "Task: trying to resolve the received string to property name")

        if(currentPropertyResolveID == -1) {

            Log.d("M:resolvePropNames", "The resolve-ID was invalidated, look if this is a description-name start indicator")

            var propertyID = ""

            propertyName.forEachIndexed { index, c ->
                when (index) {
                    0 -> if (c != 'P')return false
                    1 -> if (c != 'D')return false
                    2 -> if (c != ':')return false
                    3 -> if (c != 'S')return false
                    4 -> if (c != '0')propertyID += c
                    5 -> if (c != '0')propertyID += c
                    6 -> propertyID += c
                    //7 -> if(c != '$') ...
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
            }
            Log.d("M:resolvePropNames", "This was a start indicator. The next received string should be the name for the property with ID: $propertyID")
        }
        else {
            Log.d("M:resolvePropNames", "This must be the name string or a end indicator")
            // must be the name-string or the finalization-string
            if(propertyName == "PD:E$"){
                Log.d("M:resolvePropNames", "It's a finalization string: reset necessary parameter")
                // it's a finalization string -> check for loop end
                if(this.laRoomyDevicePropertyList.last().propertyID == currentPropertyResolveID){
                    Log.d("M:resolvePropNames", "This was the last property name to resolve: close loop and reset parameter")
                    // it's the last index, close loop
                    propertyNameResolveLoopActive = false
                    // invalidate the index
                    this.currentPropertyResolveIndex = -1
                    // raise event (but only in retrieving-mode, not in confirmation mode)
                    if(!this.propertyConfirmationModeActive)
                        this.propertyCallback.onPropertyDataRetrievalCompleted(this.laRoomyDevicePropertyList)
                    // check if one of the device-properties is part of a Group -> if so, start group-retrieving loop
                    Log.d("M:resolvePropNames", "Check if some properties are part of a group")
                    if(this.groupRequestLoopRequired()){
                        if(!this.propertyNameResolveSingleAction) {
                            Log.d(
                                "M:resolvePropNames",
                                "This checkup was true. A group retrieval is required: start loop!"
                            )
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
                    }
                }
                else {
                    Log.d("M:resolvePropNames", "This was not the last property index: send next request")
                    // it's not the last index, request next propertyName
                    requestNextPropertyDescription()
                }
                // invalidate the resolve id
                this.currentPropertyResolveID = -1
                // mark the string as processed
                stringProcessed = true
            }
            else {
                Log.d("M:resolvePropNames", "This must be the name string for the property with ID: $currentPropertyResolveID")
                // must be a name-string
                if(this.propertyConfirmationModeActive){
                    // confirmation mode active: compare the property name
                    if(propertyName != this.laRoomyDevicePropertyList.elementAt(this.currentPropertyResolveIndex).propertyDescriptor){
                        // log:
                        Log.d("M:resolvePropNames", "Confirmation-Mode active: this propertyName does not equal to the saved one.\nPropertyID: $currentPropertyResolveID\nPropertyIndex: $currentGroupResolveIndex")
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
        Log.d("M:RQNextPropNames", "Requesting next property description")
        // increment the property counter and send next request
        currentPropertyResolveIndex++

        if(currentPropertyResolveIndex < laRoomyDevicePropertyList.size) {

            val languageIdentificationChar =
                when ((this.activityContext.applicationContext as ApplicationProperty).systemLanguage) {
                    "de" -> 1
                    else -> 0
                }
            var id =
                this.laRoomyDevicePropertyList.elementAt(currentPropertyResolveIndex).propertyID
            var hundred = 0
            var tenth = 0
            val single: Int

            if (id > 100) hundred = (id / 100)
            id -= (hundred*100)
            if (id > 10) tenth = (id / 10)
            id -= (tenth*10)
            single = id

            val requestString = "B$languageIdentificationChar$hundred$tenth$single$"

            Log.d("M:RQNextPropNames", "Sending next request: $requestString")
            this.sendData(requestString)
        }
        else {
            Log.d("M:RQNextPropNames", "!! unexpected end of property array found, this should not happen, because the preliminary executed method \"resolvePropertyName\" is expected to close the retrieving loop")
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
            Log.d("M:StartGroupListing", "Device property GROUP listing started.")
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
        Log.d("M:AddPropGroup", "Add Property Group Invoked - check for type of data")
        // check type of data
        if(group == this.groupLoopEndIndication){
            // log:
            Log.d("M:AddPropGroup", "This was the finalization indicator - close loop, reset parameter and start detailed-info-loop")
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
            Log.d("M:AddPropGroup", "This could be a property-group-definition string - check this!")

            // check start-character to identify the group-string
            if(group.startsWith("IPG", false)){
                // log:
                Log.d("M:AddPropGroup", "It is a group string - add to collection")
                // add group to collection
                val propertyGroup = LaRoomyDevicePropertyGroup()
                propertyGroup.fromString(group)

                if(this.propertyConfirmationModeActive){
                    // confirmation mode active: compare the groups but note: at this point we can not use
                    // the overridden member "equals(...)" to compare, because only the raw data is recorded to the class
                    // -> so use the .checkRawEquality(...) member!
                    if(this.laRoomyPropertyGroupList.elementAt(propertyGroup.groupIndex).checkRawEquality(propertyGroup)){
                        // log:
                        Log.d("M:AddPropGroup", "Confirmation-Mode active: The element differs from the saved one:\nGroupID: ${propertyGroup.groupID}\nGroupIndex: ${propertyGroup.groupIndex}")
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
        Log.d("M:requestNextGroup", "Send next property-group request index - message: $data")
        // send the string
        this.sendData(data)
    }

    private fun startDetailedGroupInfoLoop(){
        Log.d("M:RetrievingGroupNames", "Starting to resolve the detailed group info")
        if(this.isConnected){
            if(this.laRoomyPropertyGroupList.isNotEmpty()) {

                this.currentGroupResolveIndex = 0

                val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                    "de" -> 1
                    else -> 0
                }
                var id = this.laRoomyPropertyGroupList.elementAt(currentGroupResolveIndex).groupID
                var hundred = 0
                var tenth = 0
                val single: Int

                if(id > 100) hundred = (id / 100)
                id -= (hundred*100)
                if(id > 10) tenth = (id / 10)
                id -= (tenth*10)
                single = id

                val requestString = "F$languageIdentificationChar$hundred$tenth$single$"

                Log.d("M:RetrievingGroupInfo", "Sending Group-Info Request String for the first element: $requestString")
                this.groupInfoLoopActive = true
                this.sendData(requestString)
            }
        }
    }

    private fun resolveGroupInfo(groupInfoData: String) : Boolean {
        var stringProcessed = false
        // log:
        Log.d("M:resolveGroupInfo", "Task: trying to resolve the received string to group info data")
        // check invalidated condition
        if(currentGroupResolveID == -1) {
            Log.d("M:resolveGroupInfo", "The resolve-ID was invalidated, look if this is a group-info start indicator")

            var groupID = ""

            groupInfoData.forEachIndexed { index, c ->
                when (index) {
                    0 -> if (c != 'G')return false
                    1 -> if (c != 'I')return false
                    2 -> if (c != ':')return false
                    3 -> if (c != 'S')return false
                    4 -> if (c != '0')groupID += c
                    5 -> if (c != '0')groupID += c
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
            Log.d("M:resolveGroupInfo", "This was a start indicator. The next received string should be some data for the group with ID: $groupID")
        }
        else {
            Log.d("M:resolveGroupInfo", "This must be the group-info or a end indicator")
            // must be the name-string or the finalization-string
            if(groupInfoData == "GI:E$"){
                Log.d("M:resolveGroupInfo", "It's a finalization string: reset necessary parameter")
                // it's a finalization string -> check for loop end
                if(this.laRoomyPropertyGroupList.last().groupID == currentGroupResolveID){
                    Log.d("M:resolveGroupInfo", "This was the last group-info to resolve: close loop and reset parameter")
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
                            this.propertyCallback.onGroupDataRetrievalCompleted(this.laRoomyPropertyGroupList)
                            this.generateUIAdaptableArrayListFromDeviceProperties()
                        }
                    }
                }
                else {
                    Log.d("M:resolveGroupInfo", "This was not the last group index: send next request")
                    // it's not the last index, request next propertyName
                    requestNextGroupInfo()
                }
                // invalidate the resolve id
                this.currentGroupResolveID = -1
                // mark the string as processed
                stringProcessed = true
            }
            else {
                Log.d("M:resolveGroupInfo", "This must be the data for the group with ID: $currentGroupResolveID")
                // must be group data:
                // check the type of data:
                if(groupInfoData.startsWith("mI%", false)){
                    // log
                    Log.d("M:resolveGroupInfo", "This must be the member IDs for the group with ID: $currentGroupResolveID")
                    // this was the member id identification, so set the member IDs
                    return if(this.propertyConfirmationModeActive){
                        // confirmation mode is active so check the element
                        if(!this.compareMemberIDStringWithIntegerArrayList(groupInfoData, this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).memberIDs)){
                            // log:
                            Log.d("M:resolveGroupInfo", "Confirmation-Mode active: The received member IDs differ from the saved ones.\nGroupID: $currentGroupResolveID")
                            // the member ids differ from the save ones
                            this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).hasChanged = true
                            this.groupDetailChangedOnConfirmation = true
                        }
                        true
                    }
                    else setGroupMemberIDArrayForGroupID(this.currentGroupResolveID, groupInfoData)
                }
                else{
                    // log
                    Log.d("M:resolveGroupInfo", "This must be the name-string for the group with ID: $currentGroupResolveID")
                    // this should be the name of the group, so set the group name
                    if(this.propertyConfirmationModeActive){
                        // confirmation mode active: check for changes in the group-name
                        if(groupInfoData != this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).groupName){
                            // log:
                            Log.d("M:resolveGroupInfo", "Confirmation-Mode active: detected changed group-name.\nGroupID: $currentGroupResolveID")
                            // the group-name changed
                            this.laRoomyPropertyGroupList.elementAt(this.currentGroupResolveIndex).hasChanged = true
                            this.groupDetailChangedOnConfirmation = true
                        }
                    }
                    else setGroupName(this.currentGroupResolveID, groupInfoData)
                }
                // TODO: the string should be marked as processed, but are you sure at this point??
                //stringProcessed = true
            }
        }
        return stringProcessed
    }

    private fun requestNextGroupInfo(){
        // log
        Log.d("M:RQNextGroupInfo", "Requesting next group info")
        // increment the group index counter and send next request
        currentGroupResolveIndex++
        // check if the index is inside the valid scope
        if(currentGroupResolveIndex < laRoomyPropertyGroupList.size) {

            val languageIdentificationChar =
                when ((this.activityContext.applicationContext as ApplicationProperty).systemLanguage) {
                    "de" -> 1
                    else -> 0
                }
            var id =
                this.laRoomyPropertyGroupList.elementAt(currentGroupResolveIndex).groupID
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
            Log.d("M:RQNextGroup", "Sending next request: $requestString")
            // send data:
            this.sendData(requestString)
        }
        else {
            Log.d("M:RQNextGroupInfo", "!! unexpected end of group array found, this should not happen, because the preliminary executed method \"resolveGroupInfo\" is expected to close the retrieving loop")
            // !! unexpected end of group array found, this should not happen, because the preliminary executed method "resolveGroupInfo" is expected to close the retrieving loop
            // be that as it may. Force loop closing:
            groupInfoLoopActive = false
            currentGroupResolveID = -1
            currentGroupResolveIndex = -1
            propertyConfirmationModeActive = false
        }
    }

    private fun setPropertyDescriptionForID(id:Int, description: String){
        laRoomyDevicePropertyList.forEach {
            if(it.propertyID == id){
                it.propertyDescriptor = description
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
                        3 -> if (c != '0') str1 += c
                        4 -> if (c != '0') str1 += c
                        5 -> str1 += c
                        6 -> if (c != '0') str2 += c
                        7 -> if (c != '0') str2 += c
                        8 -> str2 += c
                        9 -> if (c != '0') str3 += c
                        10 -> if (c != '0') str3 += c
                        11 -> str3 += c
                        12 -> if (c != '0') str4 += c
                        13 -> if (c != '0') str4 += c
                        14 -> str4 += c
                        15 -> if (c != '0') str5 += c
                        16 -> if (c != '0') str5 += c
                        17 -> str5 += c
                    }
                }
                laRoomyPropertyGroupList.forEach {
                    if(it.groupID == id){
                        it.setMemberIDs(
                            if(str1.isNotEmpty())
                                str1.toInt()
                            else 0,
                            if(str2.isNotEmpty())
                                str2.toInt()
                            else 0,
                            if(str3.isNotEmpty())
                                str3.toInt()
                            else 0,
                            if(str4.isNotEmpty())
                                str4.toInt()
                            else 0,
                            if(str5.isNotEmpty())
                                str5.toInt()
                            else 0
                        )
                    }
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
                        3 -> if (c != '0') str1 += c
                        4 -> if (c != '0') str1 += c
                        5 -> str1 += c
                        6 -> if (c != '0') str2 += c
                        7 -> if (c != '0') str2 += c
                        8 -> str2 += c
                        9 -> if (c != '0') str3 += c
                        10 -> if (c != '0') str3 += c
                        11 -> str3 += c
                        12 -> if (c != '0') str4 += c
                        13 -> if (c != '0') str4 += c
                        14 -> str4 += c
                        15 -> if (c != '0') str5 += c
                        16 -> if (c != '0') str5 += c
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

    private fun setGroupName(id: Int, data: String){
        laRoomyPropertyGroupList.forEach {
            if(it.groupID == id){
                it.groupName = data
            }
        }
    }

    fun startPropertyConfirmationProcess(){
        // log:
        Log.d("M:StartConfirmation", "Property Listing started in Confirmation-Mode")
        // start the confirmation process (!!but only if there is no pending retrieving process!!)
        if(!(this.propertyLoopActive || this.propertyNameResolveLoopActive || this.groupLoopActive || this.groupInfoLoopActive)) {
            if(this.laRoomyDevicePropertyList.isNotEmpty()) {
                this.propertyConfirmationModeActive = true
                this.propertyDetailChangedOnConfirmation = false
                this.groupDetailChangedOnConfirmation = false
                this.startDevicePropertyListing()
            }
            else {
                // there are no properties to confirm!
                Log.d("M:StartConfirmation", "Property List empty - confirmation not possible")
            }
        }
    }

    private fun checkDeviceNotificationEvent(data: String) :Boolean {
        var dataProcessed = false
        // log:
        Log.d("M:CheckNotiEvent", "Check received string for notification")
        // check:
        if(data.startsWith(propertyChangedNotificationEntry)){
            this.updateProperty(data)
            dataProcessed = true
            Log.d("M:CheckNotiEvent", "Property-Changed Notification detected")
        }
        else if(data.startsWith(propertyGroupChangedNotificationEntry)){
            this.updatePropertyGroup(data)
            dataProcessed = true
            Log.d("M:CheckNotiEvent", "PropertyGroup-Changed Notification detected")
        }
        return dataProcessed
    }

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
                    7 -> if(c != '0')propID += c
                    8 -> if(c != '0')propID += c
                    9 -> propID += c
                    10 -> if(c != '0')propIndex += c
                    11 -> if(c != '0')propIndex += c
                    12 -> propIndex += c
                    13 -> if(c != '0')entireProperty = true
                    14 -> if(c != '0')entireDetail = true
                    15 -> if(c != '0')thisProperty = true
                    16 -> if(c != '0')thisPropertyDetail = true
                }
            }
            Log.d("M:updateProperty", "Recording of Property-Update String complete:\nUpdate: entireProperty = $entireProperty\nUpdate: entireDetail = $entireDetail\nUpdate: thisProperty = $thisProperty\nUpdate: thisPropertyDetail = $thisPropertyDetail")

            if(entireProperty){
                this.propertyCallback.onCompletePropertyInvalidated()
                return
            }
            if(entireDetail){
                this.propertyNameResolveSingleAction = true
                startRetrievingPropertyNames()
            }
            if(thisProperty){
                this.sendSinglePropertyRequest(propIndex.toInt())
            }
            if(thisPropertyDetail){
                this.sendSinglePropertyResolveRequest(propID.toInt())
            }

        }
        else Log.d("M:updateProperty", "Error: insufficient string length")
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
                    7 -> if(c != '0')groupID += c
                    8 -> if(c != '0')groupID += c
                    9 -> groupID += c
                    10 -> if(c != '0')groupIndex += c
                    11 -> if(c != '0')groupIndex += c
                    12 -> groupIndex += c
                    13 -> if(c != '0')entirePropertyGroup = true
                    14 -> if(c != '0')entireGroupDetail = true
                    15 -> if(c != '0')thisGroup = true
                    16 -> if(c != '0')thisGroupDetail = true
                }
            }
            Log.d("M:updatePropGroup", "Recording of Property Group Update String complete:\nUpdate: entirePropertyGroup = $entirePropertyGroup\nUpdate: entireGroupDetail = $entireGroupDetail\nUpdate: thisgroup = $thisGroup\nUpdate: thisGroupDetail = $thisGroupDetail")

            if(entirePropertyGroup){
                this.propertyCallback.onCompletePropertyInvalidated()
                return
            }
            if(entireGroupDetail){
                this.propertyGroupNameResolveSingleAction = true
                startDetailedGroupInfoLoop()
            }
            if(thisGroup){
                this.sendSinglePropertyGroupRequest(groupIndex.toInt())
            }
            if(thisGroupDetail){
                this.sendSingleGroupDetailRequest(groupID.toInt())
            }

        }
        else Log.d("M:updatePropGroup", "Error: insufficient string length")
    }

    private fun sendSinglePropertyRequest(propertyIndex: Int){
        Log.d("M:SendRequest", "SendSinglePropertyRequest: Invoked")
        if(this.isConnected) {
            // set marker
            this.singleRetrievingAction = true
            // build string
            val data =
                when {
                    (propertyIndex < 10) -> "A00$propertyIndex$"
                    ((propertyIndex > 9) && (propertyIndex < 100)) -> "A0$propertyIndex$"
                    else -> "A$propertyIndex$"
                }
            // send request
            this.sendData(data)
        }
    }

    private fun sendSinglePropertyGroupRequest(groupIndex: Int){
        Log.d("M:SendRequest", "SendSinglePropertyGroupRequest: Invoked")
        if(this.isConnected) {
            // set marker
            this.singleRetrievingAction = true
            // build string
            val data =
                when {
                    (groupIndex < 10) -> "E00$groupIndex$"
                    ((groupIndex > 9) && (groupIndex < 100)) -> "E0$groupIndex$"
                    else -> "E$groupIndex$"
                }
            // send request
            this.sendData(data)
        }
    }

    private fun sendSinglePropertyResolveRequest(propertyID: Int){
        Log.d("M:SendRequest", "SendSinglePropertyResolveRequest: Invoked")
        if(this.isConnected) {
            // set marker
            this.singleRetrievingAction = true
            // get language identification
            val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                "de" -> 1
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
        Log.d("M:SendRequest", "SendSingleGroupDetailRequest: Invoked")
        if(this.isConnected) {
            // set marker
            this.singleRetrievingAction = true
            // get language identification
            val languageIdentificationChar = when((this.activityContext.applicationContext as ApplicationProperty).systemLanguage){
                "de" -> 1
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

        if(this.laRoomyDevicePropertyList.size > 0) {

            var globalIndex = 0

            if (this.laRoomyPropertyGroupList.size > 0) {
                for (laRoomyDevicePropertyGroup in this.laRoomyPropertyGroupList) {
                    // create the group entry
                    val dpl = DevicePropertyListContentInformation()
                    dpl.elementType = GROUP_ELEMENT
                    dpl.canNavigateForward = false
                    dpl.elementID = laRoomyDevicePropertyGroup.groupID
                    dpl.elementText = laRoomyDevicePropertyGroup.groupName
                    dpl.imageID = laRoomyDevicePropertyGroup.imageID
                    // add the global index of the position in the array
                    dpl.globalIndex = globalIndex
                    globalIndex++
                    // add the group to the list
                    this.uIAdapterList.add(dpl)
                    // notify activity
                    this.propertyCallback.onUIAdaptableArrayListItemAdded(dpl)
                    // add the device properties to the group by their IDs
                    for (ID in laRoomyDevicePropertyGroup.memberIDs) {
                        this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
                            if (laRoomyDeviceProperty.propertyID == ID) {
                                // ID found -> add property to list
                                val propertyEntry = DevicePropertyListContentInformation()
                                propertyEntry.elementType = PROPERTY_ELEMENT
                                propertyEntry.canNavigateForward =
                                    laRoomyDeviceProperty.needNavigation()
                                propertyEntry.isGroupMember = true
                                propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
                                propertyEntry.imageID = laRoomyDeviceProperty.imageID
                                propertyEntry.elementID = laRoomyDeviceProperty.propertyID
                                propertyEntry.indexInsideGroup = index
                                propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
                                // set global index
                                propertyEntry.globalIndex = globalIndex
                                globalIndex++
                                // add it to the list
                                this.uIAdapterList.add(propertyEntry)
                                // notify activity
                                this.propertyCallback.onUIAdaptableArrayListItemAdded(propertyEntry)
                                // ID found -> further processing not necessary -> break the loop
                                return@forEachIndexed
                            }
                        }
                    }
                    // separate the groups
                    val dpl2 = DevicePropertyListContentInformation()
                    dpl2.elementType = SEPARATOR_ELEMENT
                    dpl2.canNavigateForward = false
                    // set globalIndex
                    dpl2.globalIndex = globalIndex
                    globalIndex++
                    // add the separator to the array
                    this.uIAdapterList.add(dpl2)
                    // notify activity
                    this.propertyCallback.onUIAdaptableArrayListItemAdded(dpl2)
                }
            }
            // now add the properties which are not part of a group
            this.laRoomyDevicePropertyList.forEachIndexed { index, laRoomyDeviceProperty ->
                // only add the non-group properties
                if (!laRoomyDeviceProperty.isGroupMember) {
                    // create the entry
                    val propertyEntry = DevicePropertyListContentInformation()
                    propertyEntry.elementType = PROPERTY_ELEMENT
                    propertyEntry.canNavigateForward = laRoomyDeviceProperty.needNavigation()
                    propertyEntry.elementID = laRoomyDeviceProperty.propertyID
                    propertyEntry.imageID = laRoomyDeviceProperty.imageID
                    propertyEntry.elementText = laRoomyDeviceProperty.propertyDescriptor
                    propertyEntry.indexInsideGroup = index
                    propertyEntry.propertyType = laRoomyDeviceProperty.propertyType
                    // set global index
                    propertyEntry.globalIndex = globalIndex
                    globalIndex++
                    // add it to the list
                    this.uIAdapterList.add(propertyEntry)
                    // notify activity
                    this.propertyCallback.onUIAdaptableArrayListItemAdded(propertyEntry)
                }
            }
            this.dataReadyToShow = true
            this.propertyCallback.onUIAdaptableArrayListGenerationComplete(this.uIAdapterList)
        }
    }


    private fun checkSingleAction(data: String) :Boolean {

        if(this.singleRetrievingAction){
            Log.d("M:CheckSingleAcion", "single-action-retrieving is active look for a transmission to record")

            if(data.startsWith("IPR")){
                // its a property request response
                // TODO: handle single property response
            }
            else if(data.startsWith("IGP")){
                // its a group request response
                // TODO: handle single group response
            }
            else if(data.startsWith("PD:S")){
                // its a property description request
                // TODO: listen for next reception
            }
            else if(data.startsWith("GI:S")){
                // its a group detail request
                // TODO: listen for next reception
            }

            return false
        }
        else return false
    }

    private fun stopAllPendingLoopsAndResetParameter(){
        this.propertyLoopActive = false
        this.propertyNameResolveLoopActive = false
        this.groupLoopActive = false
        this.groupInfoLoopActive = false

        this.currentPropertyResolveID = -1
        this.currentGroupResolveIndex = -1
        this.groupRequestIndexCounter = -1

        this.propertyNameResolveSingleAction = false
        this.propertyGroupNameResolveSingleAction = false
        this.propertyConfirmationModeActive = false
        this.groupDetailChangedOnConfirmation = false
        this.propertyDetailChangedOnConfirmation = false
        this.propertyUpToDate = false // TODO: is the right???
    }

    // the callback definition for the event handling in the calling class
    interface BleEventCallback : Serializable{
        fun onConnectionStateChanged(state: Boolean){}
        fun onConnectionAttemptFailed(message: String){}
        fun onDataReceived(data: String?){}
        fun onDataSent(data: String?){}
        fun onComponentError(message: String){}
        fun onAuthenticationSuccessful(){}
        fun onDeviceReadyForCommunication(){}
    }

    interface PropertyCallback: Serializable {
        fun onPropertyDataRetrievalCompleted(properties: ArrayList<LaRoomyDeviceProperty>){}
        fun onGroupDataRetrievalCompleted(groups: ArrayList<LaRoomyDevicePropertyGroup>){}
        fun onPropertyDataChanged(propertyIndex: Int, propertyID: Int){}// if the index is -1 the whole data changed or the changes are not indexed -> iterate the array and check the .hasChanged -Parameter
        fun onPropertyGroupDataChanged(groupIndex: Int, groupID: Int){}// if the index is -1 the whole data changed or the changes are not indexed -> iterate the array and check the .hasChanged -Parameter
        fun onCompletePropertyInvalidated(){}
        fun onUIAdaptableArrayListGenerationComplete(UIArray: ArrayList<DevicePropertyListContentInformation>){}
        fun onUIAdaptableArrayListItemAdded(item: DevicePropertyListContentInformation){}
    }
}