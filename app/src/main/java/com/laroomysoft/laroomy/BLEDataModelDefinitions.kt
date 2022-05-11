package com.laroomysoft.laroomy

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.appcompat.content.res.AppCompatResources

const val PASSKEY_TYPE_NONE = 0
const val PASSKEY_TYPE_SHARED = 1
const val PASSKEY_TYPE_CUSTOM = 2
const val PASSKEY_TYPE_NORM = 3

const val NAV_TOUCH_TYPE_INVALID = '0'
const val NAV_TOUCH_TYPE_DOWN = '1'
const val NAV_TOUCH_TYPE_RELEASE = '2'

const val UC_STATE_LOCKED = '1'
const val UC_STATE_UNLOCKED = '0'

const val UC_NORMAL_MODE = '0'
const val UC_PIN_CHANGE_MODE = '1'

const val USERMESSAGE_TYPE_INFO = '0'
const val USERMESSAGE_TYPE_WARNING = '1'
const val USERMESSAGE_TYPE_ERROR = '2'

const val LOOPTYPE_NONE = 0
const val LOOPTYPE_PROPERTY = 1
const val LOOPTYPE_GROUP = 2
const val LOOPTYPE_COMPLEXSTATE = 3
const val LOOPTYPE_SIMPLESTATE = 4

const val SI_INPUT_TYPE_TEXT = 0
const val SI_INPUT_TYPE_TEXT_PASSWORD = 1
const val SI_INPUT_TYPE_NUMBER = 2
const val SI_INPUT_TYPE_NUMBER_PASSWORD = 3


class LaRoomyDevicePresentationModel {
    // NOTE: This is the data-model for the DeviceListItem in the main-activity
    var name = ""
    var address = ""

    //var type = 0
    var image = 0
}

class FragmentTransmissionData(var transmissionString: String = "")

class DeviceInfoHeaderData {
    var message = ""
    var imageID = -1
    var type = USERMESSAGE_TYPE_INFO

    var displayTime: Long = 5000
    set(value) {
        field = when(value){
            0L -> 5000
            1L -> 7500
            2L -> 10000
            3L -> 12500
            4L -> 15000
            5L -> 17500
            6L -> 20000
            9L -> 0
            else -> 22500
        }
    }

    fun clear() {
        this.message = ""
        this.imageID = -1
        this.displayTime = 0
    }
}

class LoopTimeoutWatcherData {
    var timeoutFlag = false
    var isStarted = false
    var loopType = LOOPTYPE_NONE
    var currentIndex = -1
    var loopRepeatCounter = 0

    fun clear(){
        timeoutFlag = false
        isStarted = false
        loopType = LOOPTYPE_NONE
        currentIndex = -1
        loopRepeatCounter = 0
    }
}

class ComplexPropertyState {
    // shared state values (multi-purpose)
    var valueOne =
        -1      // (R-Value in RGB Selector)     // (Level-Value in ExtendedLevelSelector)   // (hour-value in SimpleTimeSelector)       // (on-time hour-value in TimeFrameSelector)        // (number of bars in bar-graph activity)
    var valueTwo =
        -1      // (G-Value in RGB Selector)     // (not used in ExtendedLevelSelector)      // (minute-value in SimpleTimeSelector)     // (on-time minute-value in TimeFrameSelector)      // (use value as bar-descriptor in bar-graph activity)
    var valueThree =
        -1    // (B-Value in RGB Selector)     // (not used in ExtendedLevelSelector)      // (??                                      // (off-time hour-value in TimeFrameSelector)       // (fixed maximum value in bar-graph activity)
    var valueFour =
        -1     // general use                   // flag-value in simple Navigator                                                       // (off-time minute-value in TimeFrameSelector)
    var valueFive = -1     // general use                   // flag value in simple Navigator
    var commandValue =
        -1  // (Command in RGB Selector)     // (not used in ExtendedLevelSelector)      // (??
    //var enabledState = true// at this time only a placeholder (not implemented yet)
    var onOffState =
        false // (used in RGB Selector)    // used in ExLevelSelector                  // not used(for on/off use extra property)  //  not used(for on/off use extra property)
    var strValue = ""
    var flags = 0           // Flag-Value in RGB Selector

    var floatValue = -1f

    // single used values (only valid in specific complex states)
    var hardTransitionFlag =
        false  // Value for hard-transition in RGB Selector (0 == SoftTransition / 1 == HardTransition)
    //var timeSetterIndex = -1        // Value to identify the time setter type
}

abstract class IComplexPropertySubTypeProtocolClass {
    abstract fun fromString(data: String): Boolean
    abstract fun toComplexPropertyState(): ComplexPropertyState
    abstract fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState)
    abstract fun isValid(): Boolean
    abstract fun toExecutionString(propertyIndex: Int): String
}

class RGBSelectorState : IComplexPropertySubTypeProtocolClass() {

    // visibility values
    var onOffButtonVisibility = true
    var singleOrTransitionButtonVisibility = true
    var intensitySliderVisibility = true
    var softOrHardTransitionSwitchVisibility = true

    var redValue = -1
    var greenValue = -1
    var blueValue = -1

    var colorTransitionValue = -1
    var hardTransitionFlag = false
    var onOffState = false

    fun getColor(): Int {
        return if ((redValue >= 0) && (greenValue >= 0) && (blueValue >= 0)) {
            Color.rgb(redValue, greenValue, blueValue)
        } else {
            Color.WHITE
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += "0d00"

        // add rgb specific data
        executionString +=
            if (this.onOffState) {
                '1'
            } else {
                '0'
            }
        var flags = 0
        if (!this.onOffButtonVisibility) {
            flags = flags or 0x01
        }
        if (!this.singleOrTransitionButtonVisibility) {
            flags = flags or 0x02
        }
        if (!this.intensitySliderVisibility) {
            flags = flags or 0x04
        }
        if (!this.softOrHardTransitionSwitchVisibility) {
            flags = flags or 0x08
        }
        executionString += a8bitValueTo2CharHexValue(flags)
        executionString += a8bitValueTo2CharHexValue(this.colorTransitionValue)
        executionString += a8bitValueTo2CharHexValue(this.redValue)
        executionString += a8bitValueTo2CharHexValue(this.greenValue)
        executionString += a8bitValueTo2CharHexValue(this.blueValue)
        executionString +=
            if (this.hardTransitionFlag) {
                "1\r"
            } else {
                "0\r"
            }
        return executionString
    }

    override fun isValid(): Boolean {
        return (redValue >= 0) && (greenValue >= 0) && (blueValue >= 0) && (colorTransitionValue >= 0)
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()
        cState.valueOne = this.redValue
        cState.valueTwo = this.greenValue
        cState.valueThree = this.blueValue
        cState.commandValue = this.colorTransitionValue
        cState.hardTransitionFlag = this.hardTransitionFlag
        cState.onOffState = this.onOffState
        cState.flags = 0
        if (!this.onOffButtonVisibility) {
            cState.flags = cState.flags or 0x01
        }
        if (!this.singleOrTransitionButtonVisibility) {
            cState.flags = cState.flags or 0x02
        }
        if (!this.intensitySliderVisibility) {
            cState.flags = cState.flags or 0x04
        }
        if (!this.softOrHardTransitionSwitchVisibility) {
            cState.flags = cState.flags or 0x08
        }
        return cState
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.onOffButtonVisibility = (complexPropertyState.flags and 0x01) == 0
        this.singleOrTransitionButtonVisibility = (complexPropertyState.flags and 0x02) == 0
        this.intensitySliderVisibility = (complexPropertyState.flags and 0x04) == 0
        this.softOrHardTransitionSwitchVisibility = (complexPropertyState.flags and 0x08) == 0

        this.redValue = complexPropertyState.valueOne
        this.greenValue = complexPropertyState.valueTwo
        this.blueValue = complexPropertyState.valueThree

        this.colorTransitionValue = complexPropertyState.commandValue
        this.hardTransitionFlag = complexPropertyState.hardTransitionFlag
        this.onOffState = complexPropertyState.onOffState
    }

    override fun fromString(data: String): Boolean {
        try {
            // 8 on-off state
            // 9+10 flag value
            // 11+12 command value
            // 13+14 red value
            // 15+16 green value
            // 17+18 blue value
            // 19 transition flag

            if (data.length < 20) {
                if (verboseLog) {
                    Log.e(
                        "RGBData:fromString",
                        "Error reading Data from RGB Selector Data Transmission. Data-length too short: Length was: ${data.length}"
                    )
                }
            }
            var valueStr = "0x"

            // read on-off state
            this.onOffState = data[8] == '1'
            // read flag value
            valueStr += data[9]
            valueStr += data[10]
            val flagV = Integer.decode(valueStr)
            // set boolean values from flag value
            this.onOffButtonVisibility = (flagV and 0x01) == 0
            this.singleOrTransitionButtonVisibility = (flagV and 0x02) == 0
            this.intensitySliderVisibility = (flagV and 0x04) == 0
            this.softOrHardTransitionSwitchVisibility = (flagV and 0x08) == 0

            // read command value
            valueStr = "0x"// reset string
            valueStr += data[11]
            valueStr += data[12]
            this.colorTransitionValue = Integer.decode(valueStr)

            // read red value
            valueStr = "0x"
            valueStr += data[13]
            valueStr += data[14]
            this.redValue = Integer.decode(valueStr)

            // read green value
            valueStr = "0x"
            valueStr += data[15]
            valueStr += data[16]
            this.greenValue = Integer.decode(valueStr)

            // read blue value
            valueStr = "0x"
            valueStr += data[17]
            valueStr += data[18]
            this.blueValue = Integer.decode(valueStr)

            // read transition flag
            this.hardTransitionFlag = (data[19] == '1')

            return true
        } catch (e: Exception) {
            Log.e(
                "RGBData:fromString",
                "Exception occurred while reading the RGB data from string. Exception: $e"
            )
            return false
        }
    }
}

class ExtendedLevelSelectorState : IComplexPropertySubTypeProtocolClass() {

    var onOffState = false
    var levelValue = -1
    var minValue = 0
    var maxValue = 100
    var showOnOffSwitch = true
    var transmitOnlyStartEndOfTracking = false
    var isEnd = false
    var isStart = false

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.onOffState = complexPropertyState.onOffState
        this.levelValue = complexPropertyState.valueOne
        this.minValue = complexPropertyState.valueTwo
        this.maxValue = complexPropertyState.valueThree
        this.showOnOffSwitch = when(complexPropertyState.valueFour){
            1 -> true
            else -> false
        }
        this.transmitOnlyStartEndOfTracking = when(complexPropertyState.valueFive){
            1 -> true
            else -> false
        }
    }

    override fun isValid(): Boolean {
        return (((this.minValue < this.maxValue)&&(this.levelValue >= this.minValue)&&(this.levelValue <= this.maxValue)))
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()
        cState.onOffState = this.onOffState
        cState.valueOne = this.levelValue
        cState.valueTwo = this.minValue
        cState.valueThree = this.maxValue
        cState.valueFour = when(this.showOnOffSwitch){
            true -> 1
            else -> 0
        }
        cState.valueFive = when(this.transmitOnlyStartEndOfTracking){
            true -> 1
            else -> 0
        }
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 23) {
            if (verboseLog) {
                Log.e(
                    "ExLevelData:fromString",
                    "Error reading Data from Extended Selector Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            this.onOffState = (data[8]) == '1'
            this.levelValue = a4CharHexValueToSignedIntValue(data[9], data[10], data[11], data[12]).toInt()
            this.minValue = a4CharHexValueToSignedIntValue(data[13], data[14], data[15], data[16]).toInt()
            this.maxValue = a4CharHexValueToSignedIntValue(data[17], data[18], data[19], data[20]).toInt()
            // check flags
            val flags = a2CharHexValueToIntValue(data[21], data[22])
            this.showOnOffSwitch = (flags and 0x01) == 0                // if the flag is NOT set the switch must be shown (true)
            this.transmitOnlyStartEndOfTracking = (flags and 0x02) != 0 // if the flag is NOT set the param must be false

            // make sure the level value is in scope:
            when {
                (this.levelValue < this.minValue) -> this.levelValue = this.minValue
                (this.levelValue > this.maxValue) -> this.levelValue = this.maxValue
            }
            true
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += "0f0"

        // set start/end flag
        executionString += when {
            this.isStart -> '3'
            this.isEnd -> '4'
            else -> '0'
        }

        // reset start/end marker
        this.isEnd = false
        this.isStart = false

        // add ex level selector specific data
        executionString +=
            if (this.onOffState) {
                '1'
            } else {
                '0'
            }
        executionString += aSigned16bitValueTo4CharHexValue(this.levelValue.toShort())

        // The transmission to the device should not exceed 20byte!!
        // transmitting the min+max value makes no sense here

        /*executionString += aSigned16bitValueTo4CharHexValue(this.minValue.toShort())
        executionString += aSigned16bitValueTo4CharHexValue(this.maxValue.toShort())
        executionString += if(this.showOnOffSwitch){
            '1'
        } else {
            '0'
        }*/
        executionString += '\r'
        return executionString
    }
}

class TimeSelectorState : IComplexPropertySubTypeProtocolClass() {

    var hour = -1
    var minute = -1

    override fun isValid(): Boolean {
        return (hour >= 0) && (minute >= 0)
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.hour = complexPropertyState.valueOne
        this.minute = complexPropertyState.valueTwo
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()
        cState.valueOne = this.hour
        cState.valueTwo = this.minute
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 13) {
            if (verboseLog) {
                Log.e(
                    "TimeSelData:fromString",
                    "Error reading Data from Time Selector Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            this.hour = a2CharHexValueToIntValue(data[8], data[9])
            this.minute = a2CharHexValueToIntValue(data[10], data[11])
            true
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += "0500"

        // add time selector specific data
        executionString += a8bitValueTo2CharHexValue(this.hour)
        executionString += a8bitValueTo2CharHexValue(this.minute)
        executionString += '\r'
        return executionString
    }
}

class TimeFrameSelectorState : IComplexPropertySubTypeProtocolClass() {

    var onTimeHour = -1
    var onTimeMinute = -1
    var offTimeHour = -1
    var offTimeMinute = -1

    override fun isValid(): Boolean {
        return (onTimeHour >= 0) && (onTimeMinute >= 0) && (offTimeHour >= 0) && (offTimeMinute >= 0)
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.onTimeHour = complexPropertyState.valueOne
        this.onTimeMinute = complexPropertyState.valueTwo
        this.offTimeHour = complexPropertyState.valueThree
        this.offTimeMinute = complexPropertyState.valueFour
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()
        cState.valueOne = this.onTimeHour
        cState.valueTwo = this.onTimeMinute
        cState.valueThree = this.offTimeHour
        cState.valueFour = this.offTimeMinute
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 17) {
            if (verboseLog) {
                Log.e(
                    "TimeFrameData:fromString",
                    "Error reading Data from Time-Frame Selector Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            this.onTimeHour = a2CharHexValueToIntValue(data[8], data[9])
            this.onTimeMinute = a2CharHexValueToIntValue(data[10], data[11])
            this.offTimeHour = a2CharHexValueToIntValue(data[12], data[13])
            this.offTimeMinute = a2CharHexValueToIntValue(data[14], data[15])
            true
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += "0900"

        // add time-frame selector specific data
        executionString += a8bitValueTo2CharHexValue(this.onTimeHour)
        executionString += a8bitValueTo2CharHexValue(this.onTimeMinute)
        executionString += a8bitValueTo2CharHexValue(this.offTimeHour)
        executionString += a8bitValueTo2CharHexValue(this.offTimeMinute)
        executionString += '\r'
        return executionString
    }
}

class NavigatorState : IComplexPropertySubTypeProtocolClass() {

    var upperButton = false
    var midButton = false
    var downButton = false
    var leftButton = false
    var rightButton = false

    var touchType = NAV_TOUCH_TYPE_INVALID

    override fun isValid(): Boolean {
        return true
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.upperButton = (complexPropertyState.valueOne != 0)
        this.rightButton = (complexPropertyState.valueTwo != 0)
        this.downButton = (complexPropertyState.valueThree != 0)
        this.leftButton = (complexPropertyState.valueFour != 0)
        this.midButton = (complexPropertyState.valueFive != 0)
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()
        cState.valueOne = if (this.upperButton) {
            1
        } else {
            0
        }
        cState.valueTwo = if (this.rightButton) {
            1
        } else {
            0
        }
        cState.valueThree = if (this.downButton) {
            1
        } else {
            0
        }
        cState.valueFour = if (this.rightButton) {
            1
        } else {
            0
        }
        cState.valueFive = if (this.midButton) {
            1
        } else {
            0
        }
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 15) {
            if (verboseLog) {
                Log.e(
                    "NavigatorData:fromString",
                    "Error reading Data from Simple Navigator Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            this.upperButton = (data[8] == '1')
            this.rightButton = (data[9] == '1')
            this.downButton = (data[10] == '1')
            this.leftButton = (data[11] == '1')
            this.midButton = (data[12] == '1')
            true
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += "0700"

        // add Navigator specific data
        executionString += if (this.upperButton) {
            '1'
        } else {
            '0'
        }
        executionString += if (this.rightButton) {
            '1'
        } else {
            '0'
        }
        executionString += if (this.downButton) {
            '1'
        } else {
            '0'
        }
        executionString += if (this.leftButton) {
            '1'
        } else {
            '0'
        }
        executionString += if (this.midButton) {
            '1'
        } else {
            '0'
        }
        executionString += this.touchType
        executionString += '\r'
        return executionString
    }
}

class BarGraphState : IComplexPropertySubTypeProtocolClass() {

    var numBars = -1
    //var flagValue = -1
    private var barValues = ArrayList<Float>()
    private var barNames = ArrayList<String>()
    private var errorFlag = false
    var useValueAsBarDescriptor = false
    var useFixedMaximumValue = false
    var fixedMaximumValue = 0f

    override fun isValid(): Boolean {
        return (numBars > 0) && (!errorFlag) && (barValues.size == barNames.size)
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.numBars = complexPropertyState.valueOne
        this.useValueAsBarDescriptor = (complexPropertyState.valueTwo and 0x01) != 0
        this.useFixedMaximumValue = (complexPropertyState.valueTwo and 0x02) != 0
        this.fixedMaximumValue = complexPropertyState.floatValue
        this.errorFlag = this.fromComplexPropertyString(complexPropertyState.strValue, false)
    }

    fun updateFromString(data: String) : Boolean {
        return if (data.length < 11) {
            if (verboseLog) {
                Log.e(
                    "BarGraphData:updateFS",
                    "Error reading Data from BarGraphState Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            // get the flag values
            val flags = data[8].toString().toInt()
            this.useValueAsBarDescriptor = (flags and 0x01) != 0
            this.useFixedMaximumValue = (flags and 0x02) != 0

            // get the number of bars
            this.numBars = data[9].toString().toInt()

            val pureData = data.removeRange(0, 11)
            this.fromComplexPropertyString(pureData, true)
        }
    }

    fun getBarGraphDataList() : ArrayList<BarGraphData> {

        val bData = ArrayList<BarGraphData>()

        for(i in 0 until this.numBars){
            var bText = ""
            var bVal = 0f

            if(i < this.barNames.size) {
                bText = this.barNames.elementAt(i)
            }
            if(i < this.barValues.size){
                bVal = this.barValues.elementAt(i)
            }

            val bgd = BarGraphData(bVal, bText)
            bData.add(bgd)
        }
        return bData
    }

    private fun fromComplexPropertyString(data: String, isUpdateMode: Boolean): Boolean {

        // the transmission data entry must be removed on transmission reception

        // "32IDxx00" + "t" (type) + "barIndex::barName::initialBarValue;;"
        // numBars ?? where?
        // single name transmission ??? not necessary, could be done trough a complex property state update
        // pipe: [x]bar-index + [...barValue ... ?

        // the bar-graph-data can be changed by a state-update but this is not efficient
        // for dynamic data setting the pipe function could be used, this is fast!

        if (data.length < 9) {
            // error, not enough data, is invalid
            return false
        }

        try {
            // first clear the data (if mode is initial-mode)
            if (!isUpdateMode) {
                this.barValues.clear()
                this.barNames.clear()
            }

            val strArray = ArrayList<String>()
            var recString = ""
            var nextValidIndex = -1

            data.forEachIndexed { index, c ->

                when (c) {
                    ';' -> {
                        // check exit condition
                        if (data[index + 1] == ';') {
                            // end of bar definition
                            if (recString.isNotEmpty()) {
                                strArray.add(recString)
                            }
                            recString = ""
                            nextValidIndex = index + 2
                        }
                    }
                    '\r' -> {
                        if(recString.isNotEmpty()){
                            strArray.add(recString)
                            recString = ""
                        }
                        return@forEachIndexed
                    }
                }

                if (index >= nextValidIndex) {
                    recString += c
                }
            }

            if(recString.isNotEmpty()){
                strArray.add(recString)
            }

            var barIndex: Int

            strArray.forEachIndexed { index, s ->
                // get the bar index
                barIndex = s[0].toString().toInt()

                // if the index is 9, this must be the fixed value definition
                if (barIndex == 9) {
                    if (s.length >= 7) {
                        if ((s[1] == ':') && (s[2] == ':') && (s[3] == '_') && (s[4] == ':') && (s[5] == ':')) {
                            var strValue = ""
                            var counter = 6

                            while (counter < s.length) {
                                if (s[counter] == '\r') {
                                    break
                                }
                                strValue += s[counter]
                                counter++
                            }
                            if(strValue != "_") {
                                this.fixedMaximumValue = strValue.toFloat()
                            }
                        }
                    }
                } else {
                    // check validity
                    if (s.length < 6) {
                        return false
                    }
                    if (isUpdateMode) {
                        if ((barIndex >= this.barNames.size) || (barIndex >= this.barValues.size)) {
                            return false
                        }
                    } else {
                        if (barIndex != index) {
                            return false
                        }
                    }

                    if ((data[1] != ':') && (data[2] != ':')) {
                        // missing delimiter
                        return false
                    } else {
                        if (data[3] == '_') {
                            // undefined placeholder char
                            if (!isUpdateMode) {
                                // initial mode
                                this.barNames.add("_")
                            }
                        } else {
                            // record the data
                            nextValidIndex = -1
                            var dataIndex = 3
                            var barNameString = ""

                            // record the bar-name
                            while (dataIndex < s.length) {
                                if ((s[dataIndex] == ':') && (s[dataIndex + 1] == ':')) {
                                    if (!isUpdateMode) {
                                        // initial mode
                                        this.barNames.add(barNameString)
                                    } else {
                                        // must be update mode, do not add, replace it
                                        this.barNames[barIndex] = barNameString
                                    }
                                    nextValidIndex = dataIndex + 2
                                    break
                                } else {
                                    barNameString += s[dataIndex]
                                }
                                dataIndex++
                            }

                            if (nextValidIndex < s.length) {

                                dataIndex = nextValidIndex

                                if (s[dataIndex] == '_') {

                                    // undefined placeholder char -> add value zero in initial mode, in update mode let the ???
                                    //if (!isUpdateMode) {
                                        // initial mode
                                        this.barValues.add(0f)
                                    //}

                                } else {
                                    var barValueString = ""

                                    // record the bar-value
                                    while (dataIndex < s.length) {
                                        if (s[dataIndex] == '\r') {
                                            break
                                        }
                                        barValueString += s[dataIndex]
                                        dataIndex++
                                    }
                                    if (!isUpdateMode) {
                                        // initial mode
                                        if (barValueString.isNotEmpty()) {
                                            this.barValues.add(
                                                barValueString.toFloat()
                                            )
                                        } else {
                                            this.barValues.add(0f)
                                            return false
                                        }
                                    } else {
                                        // must be update mode
                                        if (barValueString.isNotEmpty()) {
                                            this.barValues[barIndex] = barValueString.toFloat()
                                        }
                                    }
                                }
                            } else {
                                // error
                                return false
                            }
                        }
                    }
                }
            }
            return true

        } catch (e: Exception) {
            Log.e(
                "BarGraphData::from",
                "BarGraphState: Syntactic read error in \"fromComplexPropertyString\". Exception: $e"
            )
            return false
        }
    }

    private fun toComplexPropertyString(): String {
        var cpString = ""

        for (i in 0..numBars) {
            cpString += "$i::"

            cpString += if (i < this.barNames.size) {
                "${this.barNames.elementAt(i)}::"
            } else {
                "_::"
            }

            cpString += if (i < this.barValues.size) {
                "${this.barValues.elementAt(i)};;"
            } else {
                "_;;"
            }
        }
        cpString += '\r'
        return cpString
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()
        cState.valueOne = this.numBars

        var flags = 0
        if(this.useValueAsBarDescriptor){
            flags = flags or 0x01
        }
        if(this.useFixedMaximumValue){
            flags = flags or 0x02
        }

        cState.floatValue = this.fixedMaximumValue
        cState.valueTwo = flags
        cState.strValue = this.toComplexPropertyString()
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 11) {
            if (verboseLog) {
                Log.e(
                    "BarGraphData:fromString",
                    "Error reading Data from BarGraphState Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            // get the flag values
            val flags = a2CharHexValueToIntValue(data[8], data[9])
            this.useValueAsBarDescriptor = (flags and 0x01) != 0
            this.useFixedMaximumValue = (flags and 0x02) != 0

            // get the number of bars
            this.numBars = data[10].toString().toInt()

            val pureData = data.removeRange(0, 11)
            this.fromComplexPropertyString(pureData, false)
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // this property type has no execution
        return "invalid usage"
    }
}

class LineGraphState: IComplexPropertySubTypeProtocolClass() {

    var drawGridLines = true
    var drawAxisValues = true

    private var errorFlag = false

    var xAxisMin = 0f
    var xAxisMax = 0f
    var yAxisMin = 0f
    var yAxisMax = 0f

    var xIntersectionUnits = 0f
    var yIntersectionUnits = 0f

    val lineData = ArrayList<LineGraphData>()

    override fun isValid(): Boolean {
        // note: the error flag is inverted
        return !errorFlag
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.drawGridLines = (complexPropertyState.valueOne and 0x01) != 0
        this.drawAxisValues = (complexPropertyState.valueOne and 0x02) != 0
        // note: the error flag is inverted
        this.errorFlag = !this.fromComplexPropertyString(complexPropertyState.strValue, false)
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()

        var flags = 0
        if(this.drawGridLines){
            flags = flags or 0x01
        }
        if(this.drawAxisValues){
            flags = flags or 0x02
        }

        cState.valueOne = flags
        cState.strValue = this.toComplexPropertyString()
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 11) {
            if (verboseLog) {
                Log.e(
                    "LineGraphData:fromString",
                    "Error reading Data from LineGraphState Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            // get the flag values
            val flags = a2CharHexValueToIntValue(data[8], data[9])
            this.drawGridLines = (flags and 0x01) != 0
            this.drawAxisValues = (flags and 0x02) != 0

            val pureData = data.removeRange(0, 11)
            this.fromComplexPropertyString(pureData, false)
        }
    }

    private fun fromComplexPropertyString(data: String, isUpdateMode: Boolean) : Boolean{

        val stringArray = ArrayList<String>()
        var addString = ""

        if(!isUpdateMode){
            this.lineData.clear()
        }

        // separate the single definition string2
        data.forEach {
            if((it != ';')&&(it != '\r')){
                addString += it
            } else {
                if(addString.isNotEmpty()) {
                    stringArray.add(addString)
                    addString = ""
                }
            }
        }
        if(addString.isNotEmpty()){
            stringArray.add(addString)
        }

        // read the definitions
        stringArray.forEach {
            try {
                if ((it[0] == 'x') || (it[0] == 'y')) {
                    // must be parameter definition
                    var vName = ""
                    var vValue = ""
                    var isName = true

                    // record the parameter name and value
                    for(c in it){
                        if(c == ':'){
                            isName = false
                        } else {
                            if (isName) {
                                vName += c
                            } else {
                                vValue += c
                            }
                        }
                    }
                    // set it to the member, respectively
                    val fVal = vValue.toFloat()

                    when(vName){
                        "xmin" -> this.xAxisMin = fVal
                        "xmax" -> this.xAxisMax = fVal
                        "ymin" -> this.yAxisMin = fVal
                        "ymax" -> this.yAxisMax = fVal
                        "xisc" -> this.xIntersectionUnits = fVal
                        "yisc" -> this.yIntersectionUnits = fVal
                        else -> {
                            Log.e("LineGraphState", "Invalid parameter name in LineGraphState string: $vName")
                        }
                    }
                } else {
                    // must be point definition
                    var xValue = ""
                    var yValue = ""
                    var isX = true

                    for(c in it){
                        if(c == ':'){
                            isX = false
                        } else {
                            if(isX){
                                xValue += c
                            } else {
                                yValue += c
                            }
                        }
                    }

                    this.lineData.add(
                        LineGraphData(
                            xValue.toFloat(),
                            yValue.toFloat()
                        )
                    )
                    if(this.lineData.size > 1000){
                        Log.e("LineGraphState", "Error: too much data points, string recording skipped!")
                        return@forEach
                    }
                }
            } catch (e: Exception){
                Log.e("LineGraphState", "Error reading single value from String: $e")
            }
        }
        return true
    }

    private fun toComplexPropertyString() : String {
        var cString = ""
        // save the scope and intersection params to string
        for (i in 0 until 6) {
            val vPara =
                    when (i) {
                        0 -> this.xAxisMin
                        1 -> this.xAxisMax
                        2 -> this.yAxisMin
                        3 -> this.yAxisMax
                        4 -> this.xIntersectionUnits
                        5 -> this.yIntersectionUnits
                        else -> 0f
                    }
            val nPara =
                    when (i) {
                        0 -> "xmin"
                        1 -> "xmax"
                        2 -> "ymin"
                        3 -> "ymax"
                        4 -> "xisc"
                        5 -> "yisc"
                        else -> "err!"
                    }
            cString += "$nPara:${vPara};"
        }
        // save the line-data to string
        this.lineData.forEach {
            cString += "${it.xVal}:${it.yVal};"
        }
        return cString
    }

    fun updateFromString(data: String) : Boolean {
        return if (data.length < 11) {
            if (verboseLog) {
                Log.e(
                    "LineGraphData:updateFS",
                    "Error reading Data from LineGraphState Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            // get the flag values
            val flags = data[8].toString().toInt()
            this.drawGridLines = (flags and 0x01) != 0
            this.drawAxisValues = (flags and 0x02) != 0

            val pureData = data.removeRange(0, 11)
            this.fromComplexPropertyString(pureData, true)
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        // this property type has no execution
        return "invalid usage"
    }
}

class StringInterrogatorState: IComplexPropertySubTypeProtocolClass(){

    var buttonDescriptor = ""
    var fieldOneDescriptor = ""
    var fieldTwoDescriptor = ""
    var fieldOneHint = ""
    var fieldTwoHint = ""
    var fieldOneContent = ""
    var fieldTwoContent = ""

    var fieldOneVisible = true
    var fieldTwoVisible = true

    var fieldOneInputType = -1
    var fieldTwoInputType = -1

    var navigateBackOnButtonPress = false

    override fun isValid(): Boolean {
        return (fieldOneInputType != -1)&&(fieldTwoInputType != -1)
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {

        this.fieldOneVisible = complexPropertyState.valueOne == 1
        this.fieldTwoVisible = complexPropertyState.valueTwo == 1
        this.fieldOneInputType = complexPropertyState.valueThree
        this.fieldTwoInputType = complexPropertyState.valueFour
        this.navigateBackOnButtonPress = complexPropertyState.valueFive == 1
        this.fromComplexPropertyString(complexPropertyState.strValue)
    }

    override fun toComplexPropertyState(): ComplexPropertyState {

        val cState = ComplexPropertyState()

        cState.valueOne = if(fieldOneVisible){
            1
        } else {
            0
        }
        cState.valueTwo = if(fieldTwoVisible){
            1
        } else {
            0
        }
        cState.valueThree = this.fieldOneInputType
        cState.valueFour = this.fieldTwoInputType
        cState.valueFive = if(this.navigateBackOnButtonPress){
            1
        } else {
            0
        }

        val oneParamElement = OneParamElement()
        oneParamElement.parameterName = "B"
        oneParamElement.parameterValue = this.buttonDescriptor.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        oneParamElement.parameterName = "F1"
        oneParamElement.parameterValue = this.fieldOneDescriptor.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        oneParamElement.parameterName = "F2"
        oneParamElement.parameterValue = this.fieldTwoDescriptor.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        oneParamElement.parameterName = "H1"
        oneParamElement.parameterValue = this.fieldOneHint.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        oneParamElement.parameterName = "H2"
        oneParamElement.parameterValue = this.fieldTwoHint.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        oneParamElement.parameterName = "C1"
        oneParamElement.parameterValue = this.fieldOneContent.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        oneParamElement.parameterName = "C2"
        oneParamElement.parameterValue = this.fieldTwoContent.ifEmpty {
            "_"
        }
        cState.strValue += oneParamElement.toString()

        return cState
    }

    private fun fromComplexPropertyString(data: String){
        val parameterList =
            ComplexPropertyStateStringReader()
                .readOneParameterDefinitions(data)

        parameterList.forEach {
            when(it.parameterName){
                "B" -> {
                    this.buttonDescriptor = if (it.parameterValue == "_") {
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                "F1" -> {
                    this.fieldOneDescriptor = if(it.parameterValue == "_"){
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                "F2" -> {
                    this.fieldTwoDescriptor = if(it.parameterValue == "_"){
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                "H1" -> {
                    this.fieldOneHint = if(it.parameterValue == "_"){
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                "H2" -> {
                    this.fieldTwoHint = if(it.parameterValue == "_"){
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                "C1" -> {
                    this.fieldOneContent = if(it.parameterValue == "_"){
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                "C2" -> {
                    this.fieldTwoContent = if(it.parameterValue == "_"){
                        ""
                    } else {
                        it.parameterValue
                    }
                }
                else -> {
                    Log.e("StringInterrogator", "Unknown parameter name sequence: ${it.parameterName}")
                }
            }
        }
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 13) {
            if (verboseLog) {
                Log.e(
                    "StringInterrogator:fromString",
                    "Error reading Data from String Interrogator Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {

            // visibilities
            when(data[8]){
                '0' -> {
                    fieldOneVisible = true
                    fieldTwoVisible = true
                }
                '1' -> {
                    fieldOneVisible = true
                    fieldTwoVisible = false
                }
                '2' -> {
                    fieldOneVisible = false
                    fieldTwoVisible = true
                }
            }
            // field input types
            this.fieldOneInputType = data[9].toString().toInt()
            this.fieldTwoInputType = data[10].toString().toInt()
            // button behavior
            this.navigateBackOnButtonPress = data[11] != '0'

            // data[12] is reserved for future use !

            // string definition data
            val pureData = data.removeRange(0, 13)
            this.fromComplexPropertyString(pureData)

            true
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {

        // define data string
        var stringInterrogatorExecutionData = ""

        stringInterrogatorExecutionData += if(this.fieldOneContent.isNotEmpty()){
            "C1::${this.fieldOneContent};;"
        } else {
            "C1::_;;"
        }

        stringInterrogatorExecutionData += if(this.fieldTwoContent.isNotEmpty()){
            "C2::${this.fieldTwoContent};;"
        } else {
            "C2::_;;"
        }

        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += a8bitValueTo2CharHexValue(2 + stringInterrogatorExecutionData.length)
        executionString += "00"

        // add string interrogator specific data
        executionString += stringInterrogatorExecutionData

        // delimiter
        executionString += '\r'

        return executionString
    }
}

class TextListPresenterState : IComplexPropertySubTypeProtocolClass() {

    private var useBackgroundStack = false
    val textListBackgroundStack = ArrayList<String>()

    override fun isValid(): Boolean {
        return true
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {

        this.useBackgroundStack = complexPropertyState.valueOne == 1

        var tStr = ""
        var nextValidIndex = 0

        complexPropertyState.strValue.forEachIndexed { index, c ->
            if(c == ';'){
                if(complexPropertyState.strValue.length > (index + 1)){
                    if(complexPropertyState.strValue.elementAt(index + 1) == ';'){
                        nextValidIndex = index + 2
                        this.textListBackgroundStack.add(tStr)
                        tStr = ""
                    }
                }
            } else {
                if(index >= nextValidIndex){
                    tStr += c
                }
            }
        }
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()

        cState.valueOne = if(useBackgroundStack){
            1
        } else {
            0
        }
        this.textListBackgroundStack.forEach {
            cState.strValue += it
            cState.strValue += ";;"
        }
        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 12) {
            if (verboseLog) {
                Log.e(
                    "TextListPresenter:fromString",
                    "Error reading Data from TextListPreventer Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            val deleteStack = this.useBackgroundStack

            this.useBackgroundStack = data.elementAt(8) == '1'

            // only read the successive data if it should be saved onto the stack
            if(useBackgroundStack) {
                when (data.elementAt(9)) {
                    '1' -> {
                        // add text to stack
                        var textToAdd = ""
                        textToAdd += when (data.elementAt(10)) {
                            '0' -> {
                                "N"
                            }
                            '1' -> {
                                "I"
                            }
                            '2' -> {
                                "W"
                            }
                            '3' -> {
                                "E"
                            }
                            else -> {
                                "N"
                            }
                        }
                        textToAdd += data.removeRange(0, 11)
                        this.textListBackgroundStack.add(textToAdd)

                        if(this.textListBackgroundStack.size > 200){
                            this.textListBackgroundStack.removeAt(0)
                        }
                    }
                    '2' -> {
                        // clear the stack
                        this.textListBackgroundStack.clear()
                    }
                }
            } else {
                if(deleteStack){
                    this.textListBackgroundStack.clear()
                }
            }
            true
        }
    }

    override fun toExecutionString(propertyIndex: Int): String {
        return "not implemented"
    }
}

class UnlockControlState: IComplexPropertySubTypeProtocolClass(){

    var unLocked = false
    var mode = UC_NORMAL_MODE
    var pin = ""
    var newPin = ""
    var flags = 0

    override fun isValid(): Boolean {
        return true
    }

    override fun fromComplexPropertyState(complexPropertyState: ComplexPropertyState) {
        this.unLocked = complexPropertyState.valueOne == 1
        this.mode = if(complexPropertyState.valueTwo == 1){
            UC_PIN_CHANGE_MODE
        } else {
            UC_NORMAL_MODE
        }
        this.pin = complexPropertyState.strValue
    }

    override fun toComplexPropertyState(): ComplexPropertyState {
        val cState = ComplexPropertyState()

        cState.valueOne = if(this.unLocked){
            1
        } else {
            0
        }
        cState.valueTwo = if(this.mode == UC_NORMAL_MODE){
            0
        } else {
            1
        }
        cState.strValue = this.pin

        return cState
    }

    override fun fromString(data: String): Boolean {
        return if (data.length < 13) {
            if (verboseLog) {
                Log.e(
                    "UnlockCTRLData:fromString",
                    "Error reading Data from Unlock Control Data Transmission. Data-length too short: Length was: ${data.length}"
                )
            }
            false
        } else {
            this.unLocked = (data[8] == '2')
            this.mode = when(data[9]){
                '0' -> UC_NORMAL_MODE
                '1' -> UC_PIN_CHANGE_MODE
                else -> UC_NORMAL_MODE
            }
            this.flags = a2CharHexValueToIntValue(data[10], data[11])
            true
        }

    }

    override fun toExecutionString(propertyIndex: Int): String {
        // generate transmission header:
        var executionString = "43"
        executionString += a8bitValueTo2CharHexValue(propertyIndex)
        executionString += a8bitValueTo2CharHexValue(5 + this.pin.length)
        executionString += "00"

        // add UnlockControl specific data
        executionString += if(this.unLocked){
            '2'
        } else {
            '1'
        }
        executionString += this.mode
        executionString += a8bitValueTo2CharHexValue(this.flags)

        if(this.mode == UC_PIN_CHANGE_MODE) {
            executionString += this.pin
            executionString += "::"
            executionString += this.newPin
        } else {
            executionString += this.pin
        }

        executionString += '\r'

        return executionString
    }
}


class DevicePropertyListContentInformation(val elementType: Int) {
    // NOTE: This is the data-model for the PropertyElement in the PropertyList on the DeviceMainActivity
    private var canNavigateForward = false
    //private set

    var isEnabled = true
    set(value) {
        this.isAccessible = false
        field = value
    }

    var hasChanged = false

    var isGroupMember = false
    set(value) {
        this.isAccessible = false
        field = value
    }

    var isLastInGroup = false
    set(value) {
        this.isAccessible = false
        field = value
    }

    var isAccessible = false
    private set

    var globalIndex = -1

    // this is the main text of the always visible element-textView
    var elementText = ""
    private set
    // this is the text of the button
    var elementSubText = ""
    private set

    var elementDescriptorText = ""
    set(value) {
        this.isAccessible = false
        field = value
    }

    var internalElementIndex = -1

    var propertyType = -1
    set(value) {
        this.isAccessible = false
        field = value
    }

    var imageID = 0

//    set(value) {
//        field = if(this.isEnabled) {
//            resourceIdForImageId(value)
//        } else {
//            resourceIdForDisabledImageId(value)
//        }
//    }

    var imageResourceID = R.drawable.ic_00_dis_image_error_state_vect
    private set

    var simplePropertyState = -1
    var complexPropertyState = ComplexPropertyState()

    // resources for the visual state of the item
    lateinit var backgroundDrawable : Drawable //= R.drawable.single_property_list_element_background
    private set
    var textColorResource = 0
    private set
    var switchVisibility = View.GONE
    private set
    var buttonVisibility = View.GONE
    private set
    var levelIndicationTextViewVisibility = View.GONE
    private set
    var navigationImageVisibility = View.GONE
    private set

    // NOTE: call update after creation or when the object data changes!
    // NOTE: when creating an object, do only set the descriptor, the elementText will be set automatically

    fun update(context: Context){
        // at first set the right image resource id
        this.imageResourceID = if(this.isEnabled) {
            resourceIdForImageId(this.imageID)
        } else {
            resourceIdForDisabledImageId(this.imageID)
        }
        // set the text color in relation to the enabled param
        this.textColorResource = if(this.isEnabled){
            context.getColor(R.color.normalTextColor)
        } else {
            context.getColor(R.color.disabledTextColor)
        }
        when(this.elementType){
            GROUP_ELEMENT -> {
                // define the background
                this.backgroundDrawable = AppCompatResources.getDrawable(
                    context,
                    R.drawable.property_list_group_header_element_background
                )!!
                // define the visibility of the optional items
                this.switchVisibility = View.GONE
                this.buttonVisibility = View.GONE
                this.levelIndicationTextViewVisibility = View.GONE
                this.navigationImageVisibility = View.GONE
                // define the element-text
                this.elementText = this.elementDescriptorText
                this.elementSubText = ""
                // other values
                this.canNavigateForward = false
            }
            PROPERTY_ELEMENT -> {
                // define the background
                if(!this.isGroupMember){
                    // draw the normal property element
                    this.backgroundDrawable = AppCompatResources.getDrawable(
                        context,
                        R.drawable.single_property_list_element_background
                    )!!
                } else {
                    if(this.isLastInGroup){
                        // draw the closing group-member background
                        this.backgroundDrawable = AppCompatResources.getDrawable(
                            context,
                            R.drawable.inside_group_property_last_list_element_background
                        )!!
                    } else {
                        // draw the inside group-member background
                        this.backgroundDrawable = AppCompatResources.getDrawable(
                            context,
                            R.drawable.inside_group_property_list_element_background
                        )!!
                    }
                }

                when(this.propertyType){
                    PROPERTY_TYPE_BUTTON -> {
                        this.navigationImageVisibility = View.GONE
                        this.levelIndicationTextViewVisibility = View.GONE
                        this.buttonVisibility = View.VISIBLE
                        this.switchVisibility = View.GONE

                        this.canNavigateForward = false

                        // decrypt the descriptor and set element and sub text
                        val dd = checkForDualDescriptor(this.elementDescriptorText)
                        if(dd.isDual){
                            this.elementSubText = dd.actionText
                            this.elementText = dd.elementText
                        } else {
                            this.elementText = this.elementDescriptorText
                            this.elementSubText = "<- ->"
                        }
                    }
                    PROPERTY_TYPE_SWITCH -> {
                        this.navigationImageVisibility = View.GONE
                        this.levelIndicationTextViewVisibility = View.GONE
                        this.buttonVisibility = View.GONE
                        this.switchVisibility = View.VISIBLE

                        this.canNavigateForward = false

                        // set the text-properties
                        this.elementText = elementDescriptorText
                        this.elementSubText = ""
                    }
                    PROPERTY_TYPE_LEVEL_SELECTOR -> {
                        this.navigationImageVisibility = View.GONE
                        this.levelIndicationTextViewVisibility = View.GONE
                        this.buttonVisibility = View.VISIBLE
                        this.switchVisibility = View.GONE

                        this.canNavigateForward = false

                        // set the element-text
                        this.elementText = this.elementDescriptorText
                        // convert the state-value to the button text
                        this.elementSubText = PercentageLevelPropertyGenerator(this.simplePropertyState).percentageString ?: ""
                    }

                    PROPERTY_TYPE_LEVEL_INDICATOR -> {
                        this.navigationImageVisibility = View.GONE
                        this.levelIndicationTextViewVisibility = View.VISIBLE
                        this.buttonVisibility = View.GONE
                        this.switchVisibility = View.GONE

                        this.canNavigateForward = false

                        // set the element text
                        this.elementText = this.elementDescriptorText
                        // convert the state to the string for the level-textView
                        this.elementSubText = PercentageLevelPropertyGenerator(this.simplePropertyState).percentageString ?: ""
                    }
                    PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY ->{
                        this.navigationImageVisibility = View.GONE
                        this.levelIndicationTextViewVisibility = View.GONE
                        this.buttonVisibility = View.GONE
                        this.switchVisibility = View.GONE

                        this.canNavigateForward = false

                        // set the element text
                        this.elementText = this.elementDescriptorText
                        this.elementSubText = ""
                    }
                    PROPERTY_TYPE_OPTION_SELECTOR -> {
                        this.navigationImageVisibility = View.GONE
                        this.levelIndicationTextViewVisibility = View.GONE
                        this.buttonVisibility = View.VISIBLE
                        this.switchVisibility = View.GONE

                        this.canNavigateForward = false

                        val oss = decryptOptionSelectorString(this.elementDescriptorText)
                        // set the element text
                        if(oss.size > 0) {
                            // the first element is the element text
                            this.elementText = oss.elementAt(0)
                            // remove it to get the pure options
                            oss.removeAt(0)
                            // get the selected index (state) and set the appropriate string
                            if(this.simplePropertyState < oss.size){
                                this.elementSubText = oss.elementAt(this.simplePropertyState)
                            } else {
                                this.elementSubText = "invalid index"
                            }
                        } else {
                            this.elementText = ""
                            this.elementSubText = ""
                        }
                    }
                    else -> {
                        // must be complex type, show the nav-arrow and hide the remaining
                        this.navigationImageVisibility = View.VISIBLE
                        this.levelIndicationTextViewVisibility = View.GONE
                        this.buttonVisibility = View.GONE
                        this.switchVisibility = View.GONE

                        this.canNavigateForward = true

                        this.elementText = this.elementDescriptorText
                        this.elementSubText = ""
                    }
                }
            }
            else -> {
                Log.e("updatePGItemData", "Error: unknown element type")
                // draw error state background
                this.backgroundDrawable = AppCompatResources.getDrawable(
                    context,
                    R.drawable.error_property_list_element_background
                )!!
            }
        }
        this.isAccessible = true
    }


//    fun clear() {
//        this.handler = null
//        this.canNavigateForward = false
//        this.isGroupMember = false
//        this.isLastInGroup = false
//        this.elementType = -1
//        this.globalIndex = -1
//        this.elementText = ""
//        this.internalElementIndex = -1
//        this.imageID = -1
//        this.propertyType = -1
//        this.simplePropertyState = -1
//        this.complexPropertyState = ComplexPropertyState()
//    }
}

class BLEDeviceData {
    var isBindingRequired = false
    var hasCachingPermission = false
    var authenticationSuccess = false
    var fragmentedTransmissionRequired = false

    var propertyCount = 0
    var groupCount = 0
    var passKeyTypeUsed = PASSKEY_TYPE_NONE

    fun clear() {
        isBindingRequired = false
        hasCachingPermission = false
        authenticationSuccess = false
        fragmentedTransmissionRequired = false

        propertyCount = 0
        groupCount = 0
        passKeyTypeUsed = PASSKEY_TYPE_NONE
    }
}

class LaRoomyDeviceProperty {

    var propertyIndex: Int = -1 // invalid marker == -1
    var propertyType: Int = -1
    var propertyDescriptor: String = "unset"
    var isGroupMember = false
    var groupIndex = -1
    var imageID = -1
    var propertyState = -1
    var flags = -1
    var complexPropertyState = ComplexPropertyState()
    var isEnabled = true

    override fun equals(other: Any?): Boolean {

        // TODO: update

        // check if this is the same reference
        if (this === other) return true
        // check if other is an invalid type
        if (other !is LaRoomyDeviceProperty) return false
        // check data equality
        if (other.propertyIndex != this.propertyIndex) return false
        if (other.propertyType != this.propertyType) return false
        //if(other.propertyDescriptor != this.propertyDescriptor)return false // the comparison of this member is not reasonable, because the element is not defined in the property-string
        if (other.isGroupMember != this.isGroupMember) return false
        if (other.groupIndex != this.groupIndex) return false
        if (other.imageID != this.imageID) return false
        // all is the same so return true
        return true
    }

/*
    fun needNavigation(): Boolean {
        // if the property-type does not need navigation -> return false
        return when (this.propertyType) {
            PROPERTY_TYPE_BUTTON -> false
            PROPERTY_TYPE_SWITCH -> false
            PROPERTY_TYPE_LEVEL_SELECTOR -> false
            PROPERTY_TYPE_LEVEL_INDICATOR -> false
            PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY -> false
            PROPERTY_TYPE_OPTION_SELECTOR -> false
            else -> true
        }
    }
*/

    fun fromString(string: String) {
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

                // TODO: decode descriptor (unicode!)

                // decode hex values
                this.propertyIndex = Integer.decode(propIndex)
                this.propertyType = Integer.decode(propType)
                this.imageID = Integer.decode(imgID)
                this.groupIndex = Integer.decode(grID)
                this.flags = Integer.decode(fgs)
                this.propertyState = Integer.decode(propState)

                if((this.flags and 0x01) != 0) {
                    this.isGroupMember = true
                }
                if((this.flags and 0x02) != 0) {
                    this.isEnabled = false
                }

                if (verboseLog) {
                    Log.d("M:DevProp:fromString", "Data Recorded - Results:")
                    Log.d("M:DevProp:fromString", "PropertyIndex: ${this.propertyIndex}")
                    Log.d("M:DevProp:fromString", "PropertyType: ${this.propertyType}")
                    Log.d("M:DevProp:fromString", "GroupIndex: ${this.groupIndex}")
                    Log.d("M:DevProp:fromString", "PropertyImageID: ${this.imageID}")
                    Log.d("M:DevProp:fromString", "PropertyState: ${this.propertyState}")
                    Log.d("M:DevProp:fromString", "Descriptor: ${this.propertyDescriptor}")
                }
            }
        } catch (except: Exception) {
            Log.e("M:LDP:Prop:fromString", "Exception occurred: ${except.message}")
        }
    }

    override fun toString(): String {

        var outString = "P0"
        outString += a8bitValueTo2CharHexValue(this.propertyIndex)
        outString += "0000"
        outString += a8bitValueTo2CharHexValue(this.propertyType)
        outString += a8bitValueTo2CharHexValue(this.imageID)
        outString += a8bitValueTo2CharHexValue(this.groupIndex)
        outString += a8bitValueTo2CharHexValue(this.flags)
        outString += a8bitValueTo2CharHexValue(this.propertyState)
        outString += this.propertyDescriptor

        // it is more efficient to use string-templates in this case here!!!

        return outString
    }

    override fun hashCode(): Int {
        var result = propertyIndex
        result = 31 * result + propertyType
        result = 31 * result + propertyDescriptor.hashCode()
        result = 31 * result + isGroupMember.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + imageID
        //result = 32 * result + hasChanged.hashCode()
        return result
    }
}

class LaRoomyDevicePropertyGroup {

    var groupIndex = -1
    var groupName = "unset"
    var memberCount = 0
    var imageID = -1
    //var hasChanged = false

    override fun equals(other: Any?): Boolean {
        // check reference equality
        if (other === this) return true
        // check for invalid type
        if (other !is LaRoomyDevicePropertyGroup) return false
        // check data
        if (other.groupIndex != this.groupIndex) return false
        //if(other.groupName != this.groupName)return false // the comparison of this member is not reasonable, because the element is not defined in the group-string
        if (other.memberCount != this.memberCount) return false
        //if (other.memberIDs != this.memberIDs) return false
        if (other.imageID != this.imageID) return false
        // all data is the same -> return true
        return true
    }

    fun fromString(groupString: String) {
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


                // get the descriptor
                this.groupName = groupString.removeRange(0, 12)
                this.groupName = this.groupName.removeSuffix("\r")

                // TODO: decode groupName (unicode!) do it also on all visible strings!

                if (verboseLog) {
                    Log.d("M:PropGroup:fromString", "Data Recorded - Results:")
                    Log.d("M:PropGroup:fromString", "GroupIndex: ${this.groupIndex}")
                    Log.d("M:PropGroup:fromString", "MemberAmount: ${this.memberCount}")
                    Log.d("M:PropGroup:fromString", "GroupImageID: ${this.imageID}")
                }
            }
        } catch (except: Exception) {
            Log.e("M:LDP:Group:fromString", "Exception occurred: ${except.message}")
        }
    }

    override fun toString(): String {

        var outString = "G0"
        outString += a8bitValueTo2CharHexValue(this.groupIndex)
        outString += "0000"
        outString += a8bitValueTo2CharHexValue(this.memberCount)
        outString += a8bitValueTo2CharHexValue(this.imageID)
        outString += groupName
        outString += '\r'

        // it is more efficient to use string-templates in this case here!!!

        return outString
    }

    override fun hashCode(): Int {
        var result = groupIndex
        result = 31 * result + groupName.hashCode()
        result = 31 * result + memberCount
        result = 31 * result + imageID
        return result
    }
}

class DualDescriptor {
    var elementText = ""
    var actionText = ""
    var isDual = false
}
