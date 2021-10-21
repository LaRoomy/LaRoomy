package com.laroomysoft.laroomy

import android.util.Log
import android.widget.SeekBar
import kotlin.math.min

const val UNDEFINED = -1

const val PASSKEY_TYPE_NONE = 0
const val PASSKEY_TYPE_SHARED = 1
const val PASSKEY_TYPE_CUSTOM = 2
const val PASSKEY_TYPE_NORM = 3

class LaRoomyDevicePresentationModel {
    // NOTE: This is the data-model for the DeviceListItem in the main-activity
    var name = ""
    var address = ""
    //var type = 0
    var image = 0
}

class DeviceInfoHeaderData {
    var message = ""
    var imageID = -1
    var valid = false

    fun clear(){
        this.message = ""
        this.imageID = -1
        this.valid = false
    }
}

class ElementUpdateInfo{
    var elementID = -1
    var elementIndex = -1
    var elementType = -1
    var updateType = -1
}

class MultiComplexPropertyData{
    var dataIndex = -1
    var dataName = ""
    var dataValue = -1
    var isName = false
}

class ComplexPropertyState {
    // shared state values (multi-purpose)
    var valueOne = -1      // (R-Value in RGB Selector)     // (Level-Value in ExtendedLevelSelector)   // (hour-value in SimpleTimeSelector)       // (on-time hour-value in TimeFrameSelector)        // (number of bars in bar-graph activity)
    var valueTwo = -1      // (G-Value in RGB Selector)     // (not used in ExtendedLevelSelector)      // (minute-value in SimpleTimeSelector)     // (on-time minute-value in TimeFrameSelector)      // (use value as bar-descriptor in bar-graph activity)
    var valueThree = -1    // (B-Value in RGB Selector)     // (not used in ExtendedLevelSelector)      // (??                                      // (off-time hour-value in TimeFrameSelector)       // (fixed maximum value in bar-graph activity)
    var valueFour = -1     // general use                   // flag-value in simple Navigator
    var valueFive = -1     // general use                   // flag value in simple Navigator
    var commandValue = -1  // (Command in RGB Selector)     // (not used in ExtendedLevelSelector)      // (??                                      // (off-time minute-value in TimeFrameSelector)
    var enabledState = true// at this time only a placeholder (not implemented yet)
    var onOffState = false // (not used in RGB Selector)    // used in ExLevelSelector                  // not used(for on/off use extra property)  //  not used(for on/off use extra property)
    var strValue = ""

    // single used values (only valid in specific complex states)
    var hardTransitionFlag = false  // Value for hard-transition in RGB Selector (0 == SoftTransition / 1 == HardTransition)
    var timeSetterIndex = -1        // Value to identify the time setter type
}

class DevicePropertyListContentInformation : SeekBar.OnSeekBarChangeListener{
    // NOTE: This is the data-model for the PropertyElement in the PropertyList on the DeviceMainActivty

    var handler: OnPropertyClickListener? = null

    var canNavigateForward = false
    var isGroupMember = false
    var isLastInGroup = false
    var elementType = -1 //SEPARATOR_ELEMENT
    var indexInsideGroup = -1
    var globalIndex = -1
    var elementText = ""
    var internalElementIndex = -1
    var imageID = -1
    var propertyType = -1
    //var initialElementValue = -1
    var simplePropertyState = -1
    var complexPropertyState = ComplexPropertyState()

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

class BLEDeviceData {
    var isBindingRequired = false
    var hasCachingPermission = false
    var authenticationSuccess = false

    var propertyCount = 0
    var groupCount = 0
    var passKeyTypeUsed = PASSKEY_TYPE_NONE

    fun clear(){
        isBindingRequired = false
        hasCachingPermission = false
        authenticationSuccess = false

        propertyCount = 0
        groupCount = 0
        passKeyTypeUsed = PASSKEY_TYPE_NONE
    }
}

class LaRoomyDeviceProperty{

    var propertyIndex: Int = -1 // invalid marker == -1
    var propertyType: Int = -1
    var propertyDescriptor: String = "unset"
    var isGroupMember = false
    var groupIndex = -1
    var imageID = -1
    var hasChanged = false
    var propertyState = -1
    var flags = -1
    var complexPropertyState = ComplexPropertyState()

    override fun equals(other: Any?): Boolean {

        // TODO: update

        // check if this is the same reference
        if(this === other)return true
        // check if other is an invalid type
        if(other !is LaRoomyDeviceProperty)return false
        // check data equality
        if(other.propertyIndex != this.propertyIndex)return false
        if(other.propertyType != this.propertyType)return false
        //if(other.propertyDescriptor != this.propertyDescriptor)return false // the comparison of this member is not reasonable, because the element is not defined in the property-string
        if(other.isGroupMember != this.isGroupMember)return false
        if(other.groupIndex != this.groupIndex)return false
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
            PROPERTY_TYPE_OPTION_SELECTOR -> false
            else -> true
        }
    }

    fun fromString(string: String){
        // generate member content from string:
        try {
            if (string.isNotEmpty()) {
                var propType = "0x"
                var propIndex = "0x"
                var propState = "0x"
                var grID = "0x"
                var imgID = "0x"
                var fgs = "0x"

                string.forEachIndexed { index, c ->
                    when (index) {
                        2 -> propIndex += c
                        3 -> propIndex += c
                        8 -> propType += c
                        9 -> propType += c
                        10 -> imgID += c
                        11 -> imgID += c
                        12 -> grID += c
                        13 -> grID += c
                        14 -> fgs += c
                        15 -> fgs += c
                        16 -> propState += c
                        17 -> propState += c
                        18 -> return@forEachIndexed
                    }
                }
                // save descriptor
                this.propertyDescriptor = string.removeRange(0, 18)
                this.propertyDescriptor = this.propertyDescriptor.removeSuffix("\r")

                // decode hex values
                this.propertyIndex = Integer.decode(propIndex)
                this.propertyType = Integer.decode(propType)
                this.imageID = Integer.decode(imgID)
                this.groupIndex = Integer.decode(grID)
                this.flags = Integer.decode(fgs)
                this.propertyState = Integer.decode(propState)

                if(this.groupIndex != 0){
                    this.isGroupMember = true
                }

                if(verboseLog) {
                    Log.d("M:DevProp:fromString", "Data Recorded - Results:")
                    Log.d("M:DevProp:fromString", "PropertyIndex: ${this.propertyIndex}")
                    Log.d("M:DevProp:fromString", "PropertyType: ${this.propertyType}")
                    Log.d("M:DevProp:fromString", "GroupIndex: ${this.groupIndex}")
                    Log.d("M:DevProp:fromString", "PropertyImageID: ${this.imageID}")
                    Log.d("M:DevProp:fromString", "PropertyState: ${this.propertyState}")
                    Log.d("M:DevProp:fromString", "Descriptor: ${this.propertyDescriptor}")
                }
            }
        }
        catch(except: Exception){
            Log.e("M:LDP:Prop:fromString", "Exception occurred: ${except.message}")
        }
    }

    fun checkRawEquality(ldp:LaRoomyDeviceProperty) :Boolean {
        return ((ldp.propertyType == this.propertyType)&&(ldp.propertyIndex == this.propertyIndex)&&(ldp.imageID == this.imageID))
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
        result = 31 * result + propertyType
        result = 31 * result + propertyDescriptor.hashCode()
        result = 31 * result + isGroupMember.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + imageID
        result = 32 * result + hasChanged.hashCode()
        return result
    }
}

class LaRoomyDevicePropertyGroup{

    var groupIndex = -1
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
                var localGroupIndex = "0x"
                var localMemberCount = "0x"
                var imgID = "0x"

                groupString.forEachIndexed { index, c ->
                    when (index) {
                        2 -> localGroupIndex += c
                        3 -> localGroupIndex += c
                        8 -> localMemberCount += c
                        9 -> localMemberCount += c
                        10 -> imgID += c
                        11 -> imgID += c
                        12 -> return@forEachIndexed
                    }
                }
                this.groupIndex = Integer.decode(localGroupIndex)
                this.memberCount = Integer.decode(localMemberCount)
                this.imageID = Integer.decode(imgID)


                // make sure the string is long enough for all members
                val minLength = 11 + (2 * this.memberCount)

                // extract member id's
                if(groupString.length <= minLength){

                    var hexVal = "0x"

                    for(i in 0 .. this.memberCount){
                        val index = 12 + (i * 2)
                        hexVal += groupString[index]
                        hexVal += groupString[index + 1]
                        this.memberIDs.add(Integer.decode(hexVal))
                        hexVal = "0x"
                    }
                }
                // get the descriptor
                this.groupName = groupString.removeRange(0, minLength)
                this.groupName = this.groupName.removeSuffix("\r")

                if(verboseLog) {
                    Log.d("M:PropGroup:fromString", "Data Recorded - Results:")
                    Log.d("M:PropGroup:fromString", "GroupIndex: $localGroupIndex")
                    Log.d("M:PropGroup:fromString", "MemberAmount: $localMemberCount")
                    Log.d("M:PropGroup:fromString", "GroupImageID: $imgID")
                    Log.d("M:PropGroup:fromString", "Member IDs:")
                    this.memberIDs.forEachIndexed { index, i ->
                        Log.d("M:PropGroup:fromString", "Index: $index ID: $i")
                    }
                }
            }
        }
        catch(except: Exception){
            Log.e("M:LDP:Group:fromString", "Exception occurred: ${except.message}")
        }
    }

    fun checkRawEquality(ldpg: LaRoomyDevicePropertyGroup) : Boolean {
        return ((this.groupIndex == ldpg.groupIndex)&&(this.imageID == ldpg.imageID)&&(this.memberCount == ldpg.memberCount))
    }

//    fun setMemberIDs(id1: Int, id2: Int, id3: Int, id4: Int, id5: Int){
//        this.memberIDs.clear()
//        this.memberIDs.add(id1)
//        this.memberIDs.add(id2)
//        this.memberIDs.add(id3)
//        this.memberIDs.add(id4)
//        this.memberIDs.add(id5)
//    }

    override fun hashCode(): Int {
        var result = groupIndex
        result = 31 * result + groupName.hashCode()
        result = 31 * result + memberCount
        result = 31 * result + memberIDs.hashCode()
        result = 31 * result + imageID
        result = 31 * result + hasChanged.hashCode()
        return result
    }
}

class DualDescriptor{
    var elementText = ""
    var actionText = ""
    var isDual = false
}
