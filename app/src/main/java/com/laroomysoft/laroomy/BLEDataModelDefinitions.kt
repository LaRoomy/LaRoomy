package com.laroomysoft.laroomy

import android.util.Log
import android.widget.SeekBar

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
    var elementID = -1
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

    var propertyCount = 0
    var groupCount = 0

    fun clear(){
        isBindingRequired = false
        hasCachingPermission = false

        propertyCount = 0
        groupCount = 0
    }
}

class LaRoomyDeviceProperty{

    var propertyIndex: Int = -1 // invalid marker == -1
    var propertyID: Int = -1
    var propertyType: Int = -1
    var propertyDescriptor: String = "unset"
    var isGroupMember = false
    var groupID = -1
    var imageID = -1
    var hasChanged = false
    var propertyState = -1
    var complexPropertyState = ComplexPropertyState()
    var isEnabled = true

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
                        3 -> propID += c
                        4 -> propID += c
                        5 -> propID += c
                        6 -> propType += c
                        7 -> propType += c
                        8 -> propType += c
                        9 -> propIndex += c
                        10 -> propIndex += c
                        11 -> propIndex += c
                        12 -> if(isNumber(c)) grID += c
                        13 -> if(isNumber(c)) grID += c
                        14 -> if(isNumber(c)) grID += c
                        15 -> imgID += c
                        16 -> imgID += c
                        17 -> imgID += c
                    }
                }
                if(verboseLog) {
                    Log.d("M:DevProp:fromString", "Data Recorded - Results:")
                    Log.d("M:DevProp:fromString", "PropertyID: $propID")
                    Log.d("M:DevProp:fromString", "PropertyType: $propType")
                    Log.d("M:DevProp:fromString", "PropertyIndex: $propIndex")
                    Log.d("M:DevProp:fromString", "PropertyImageID: $imgID")
                }

                this.propertyID = propID.toInt()
                this.propertyType = propType.toInt()
                this.propertyIndex = propIndex.toInt()
                this.imageID = imgID.toInt()

                if(grID.isNotEmpty()){
                    this.groupID = grID.toInt()
                    this.isGroupMember = true

                    if(verboseLog) {
                        Log.d(
                            "M:DevProp:fromString",
                            "isGroupMember: $isGroupMember -- GroupID: $groupID"
                        )
                    }
                }
            }
            if(verboseLog) {
                Log.d(
                    "M:LRDevice:FromString",
                    "LaRoomy device property string read:\n -PropertyID: ${this.propertyID}\n -PropertyType: ${this.propertyType}\n - PropertyIndex: ${this.propertyIndex}\n - PropertyImageID: ${this.imageID}"
                )
            }
        }
        catch(except: Exception){
            Log.e("M:LDP:Prop:fromString", "Exception occurred: ${except.message}")
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
                        3 -> localGroupID += c
                        4 -> localGroupID += c
                        5 -> localGroupID += c
                        6 -> localGroupIndex += c
                        7 -> localGroupIndex += c
                        8 -> localGroupIndex += c
                        9 -> memberAmount += c
                        10 -> memberAmount += c
                        11 -> memberAmount += c
                        12 -> imgID += c
                        13 -> imgID += c
                        14 -> imgID += c
                    }
                }
                if(verboseLog) {
                    Log.d("M:PropGroup:fromString", "Data Recorded - Results:")
                    Log.d("M:PropGroup:fromString", "GroupID: $localGroupID")
                    Log.d("M:PropGroup:fromString", "GroupIndex: $localGroupIndex")
                    Log.d("M:PropGroup:fromString", "MemberAmount: $memberAmount")
                    Log.d("M:PropGroup:fromString", "GroupImageID: $imgID")
                }

                this.groupID = localGroupID.toInt()
                this.groupIndex = localGroupIndex.toInt()
                this.memberCount = memberAmount.toInt()
                this.imageID = imgID.toInt()
            }
            if(verboseLog) {
                Log.d(
                    "M:PropGroup:fromString",
                    "LaRoomy device property GROUP string read:\n -GroupID: ${this.groupID}\n -GroupIndex: ${this.groupIndex}\n - MemberAmount: ${this.memberCount}\n - GroupImageID: ${this.imageID}"
                )
            }
        }
        catch(except: Exception){
            Log.e("M:LDP:Group:fromString", "Exception occurred: ${except.message}")
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
