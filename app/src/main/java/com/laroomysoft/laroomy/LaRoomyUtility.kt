package com.laroomysoft.laroomy

import android.graphics.Color
import kotlin.experimental.or
import kotlin.math.floor
import kotlin.math.roundToInt

const val COMMON_PASSKEY_LENGTH = 10

const val passKeyCharacter = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890"

//fun percentageFrom8BitValue(value: Int): Int{
//
//    var pLevel =
//        (value / 255) * 100
//
//    if(pLevel > 100)
//        pLevel = 100
//    else if(pLevel < 0)
//        pLevel = 0
//
//    return pLevel
//}

fun colorForPercentageLevel(level: Int): Int {
    return if((level < 35)&&(level > 12)) Color.YELLOW
    else if(level <= 12) Color.RED
    else Color.GREEN
}

fun isNumber(char: Char): Boolean {
    return when (char) {
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

fun colorForUserMessageType(type: Char) : Int {
    return when(type){
        '0' -> R.color.InfoColor
        '1' -> R.color.WarningColor
        '2' -> R.color.ErrorColor
        else -> R.color.InfoColor
    }
}

fun get8BitValueAsPercent(value: Int) : Int {
    var ret = -1

    if(value in 0..255){
        ret = when {
            value > 0 -> {
                val floatVar = value.toFloat()
                ((floatVar*100f)/255f).roundToInt()
            }
            value == 0 -> 0
            else -> ret -1
        }
    }
    return ret
}

fun get8BitValueAsPartOfOne(value: Int) : Float {
    val result = 0.01*get8BitValueAsPercent(value).toDouble()
    return when{
        (result < 0) -> 0.00
        (result > 1) -> 1.00
        else -> result
    }.toFloat()
}

fun percentTo8Bit(percent: Int) : Int {
    val value = 255f*percent
    return if(value == 0f) 0
    else (value/100f).roundToInt()
}

fun a8BitValueToString(value: Int) : String {
    var worker = value
    var hundred = 0
    var tenth = 0
    val single: Int

    if (worker > 100) hundred = (worker / 100)
    worker -= (hundred*100)
    if (worker > 10) tenth = (worker / 10)
    worker -= (tenth*10)
    single = worker
    // create the request string
    return "$hundred$tenth$single"
}

fun a8BitValueAsTwoCharString(value: Int) : String {
    var worker = value
    var tenth = 0
    val single: Int

    if (worker > 10) tenth = (worker / 10)
    worker -= (tenth*10)
    single = worker
    // create the request string
    return "$tenth$single"
}

fun macAddressToEncryptString(macAddress: String): String {
    return if(macAddress.length < 16){
        ERROR_INVALID_FORMAT
    } else {
        var formattedString = ""
        macAddress.forEach {
            if(it != ':'){
                formattedString += it
            }
        }
        formattedString
    }
}

fun encryptStringToMacAddress(string: String): String {
    return if(string.length < 12){
        ERROR_INVALID_PARAMETER
    } else {
        var formattedAddress = ""
        string.forEachIndexed { index, c ->
            if(((index % 2) == 0) && (index != 0)){
                formattedAddress += ':'
            }
            formattedAddress += c
        }
        formattedAddress
    }
}

fun encryptString(inputString: String):String {
    // encrypt the string in reverse direction
    return if(inputString.isNotEmpty()) {
        var encryptedString = ""
        val reverseLength = inputString.length - 1

        for (i in inputString.indices) {
            encryptedString += swapChar(inputString.elementAt(reverseLength - i))
        }
        encryptedString
    } else {
        ERROR_INVALID_PARAMETER
    }
}

fun decryptString(inputString: String):String {
    // decrypt the string in reverse direction
    return if(inputString.isNotEmpty()) {
        var decryptedString = ""
        val reverseLength = inputString.length - 1

        for (i in inputString.indices) {
            decryptedString += reSwapChar(inputString.elementAt(reverseLength - i))
        }
        decryptedString
    } else {
        ERROR_INVALID_PARAMETER
    }
}

fun swapChar(c: Char) : Char {
    return when(c){
        '0' -> 'N'
        '1' -> 'Y'
        '2' -> 'D'
        '3' -> 'W'
        '4' -> 'V'
        '5' -> 's'
        '6' -> 'o'
        '7' -> 'E'
        '8' -> 'i'
        '9' -> 'Q'
        'a' -> 'P'
        'b' -> 'O'
        'c' -> 'Z'
        'd' -> '6'
        'e' -> 'w'
        'f' -> 'K'
        'g' -> 'J'
        'h' -> 'I'
        'i' -> 'm'
        'j' -> 'G'
        'k' -> 'F'
        'l' -> 'S'
        'm' -> 'X'
        'n' -> 'C'
        'o' -> 'B'
        'p' -> 'A'
        'q' -> '5'
        'r' -> '1'
        's' -> 'u'
        't' -> '8'
        'u' -> '4'
        'v' -> '0'
        'w' -> 'M'
        'x' -> '7'
        'y' -> '3'
        'z' -> '9'
        'A' -> 'r'
        'B' -> 'q'
        'C' -> 'e'
        'D' -> 'T'
        'E' -> 'n'
        'F' -> 'H'
        'G' -> 'l'
        'H' -> 'k'
        'I' -> 'j'
        'J' -> 'R'
        'K' -> 'h'
        'L' -> 'b'
        'M' -> 'f'
        'N' -> 'p'
        'O' -> 'a'
        'P' -> 'c'
        'Q' -> 'g'
        'R' -> 'd'
        'S' -> 'z'
        'T' -> 'y'
        'U' -> 'x'
        'V' -> 'L'
        'W' -> 'v'
        'X' -> '2'
        'Y' -> 't'
        'Z' -> 'U'
        else -> c
    }
}

fun reSwapChar(c: Char): Char {
    return when(c){
        'N' -> '0'
        'Y' -> '1'
        'D' -> '2'
        'W' -> '3'
        'V' -> '4'
        's' -> '5'
        'o' -> '6'
        'E' -> '7'
        'i' -> '8'
        'Q' -> '9'
        'P' -> 'a'
        'O' -> 'b'
        'Z' -> 'c'
        '6' -> 'd'
        'w' -> 'e'
        'K' -> 'f'
        'J' -> 'g'
        'I' -> 'h'
        'm' -> 'i'
        'G' -> 'j'
        'F' -> 'k'
        'S' -> 'l'
        'X' -> 'm'
        'C' -> 'n'
        'B' -> 'o'
        'A' -> 'p'
        '5' -> 'q'
        '1' -> 'r'
        'u' -> 's'
        '8' -> 't'
        '4' -> 'u'
        '0' -> 'v'
        'M' -> 'w'
        '7' -> 'x'
        '3' -> 'y'
        '9' -> 'z'
        'r' -> 'A'
        'q' -> 'B'
        'e' -> 'C'
        'T' -> 'D'
        'n' -> 'E'
        'H' -> 'F'
        'l' -> 'G'
        'k' -> 'H'
        'j' -> 'I'
        'R' -> 'J'
        'h' -> 'K'
        'b' -> 'L'
        'f' -> 'M'
        'p' -> 'N'
        'a' -> 'O'
        'c' -> 'P'
        'g' -> 'Q'
        'd' -> 'R'
        'z' -> 'S'
        'y' -> 'T'
        'x' -> 'U'
        'L' -> 'V'
        'v' -> 'W'
        '2' -> 'X'
        't' -> 'Y'
        'U' -> 'Z'
        else -> c
    }
}

fun isHexCharacter(c: Char) : Boolean {
    return when(c){
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
        'a' -> true
        'b' -> true
        'c' -> true
        'd' -> true
        'e' -> true
        'f' -> true
        else -> false
    }
}

fun loopTypeToString(loopType: Int) : String {
    return when(loopType){
        LOOPTYPE_NONE -> "none"
        LOOPTYPE_PROPERTY -> "property"
        LOOPTYPE_GROUP -> "group"
        LOOPTYPE_SIMPLESTATE -> "simple property-state"
        LOOPTYPE_COMPLEXSTATE -> "complex property-state"
        else -> "unknown"
    }
}

fun isLaroomyDevice(name: String) : Boolean {
    return name.contains("laroomy", true)||(name.contains("lry", true))
}

fun deviceImageFromName(name: String): Int {
    return when {
        (isLaroomyDevice(name)) -> R.drawable.ic_laroomy_icon_sq48_vect
        else -> {
            when {
                name.contains("HME") -> {
                    R.drawable.ic_home_white_48dp
                }
                name.contains("LGB") -> {
                    R.drawable.ic_light_bulb_white_48dp
                }
                name.contains("RMT") -> {
                    R.drawable.ic_settings_remote_white_48dp
                }
                name.contains("ANL") -> {
                    R.drawable.ic_analytics_white_48dp
                }
                name.contains("WFS") -> {
                    R.drawable.ic_wifi_white_48dp
                }
                name.contains("TSP") -> {
                    R.drawable.ic_schedule_white_48dp
                }
                name.contains("TNE") -> {
                    R.drawable.ic_tune_white_48dp
                }
                name.contains("LCK") -> {
                    R.drawable.ic_lock_open_white_48dp
                }
                (name.contains("LED") || name.contains("Led")) -> {
                    R.drawable.ic_light_bulb_white_48dp
                }
                else -> {
                    R.drawable.ic_bluetooth_connected_white_48dp
                }
            }
        }
    }
}

fun makeSimplePropertyExecutionString(propertyIndex: Int, stateVal: Int) : String {
    // static part
    var executionString = "41"

    // add property id as hex string
    val propId =
        Integer.toHexString(propertyIndex)

    if(propId.length < 2){
        executionString += '0'
        executionString += propId
    } else {
        executionString += propId
    }
    // add data size and empty flag values
    executionString += "0300"

    // add the state
    val stateString =
        Integer.toHexString(stateVal)
    if(stateString.length < 2){
        executionString += '0'
        executionString += stateString
    } else {
        executionString += stateString
    }

    // add the delimiter
    executionString += '\r'

    return executionString
}

fun checkForDualDescriptor(descriptor: String) : DualDescriptor {
    val dualDescriptor = DualDescriptor()
    var firstSectionProcessing = true
    var secondSectionStartIndex = 0

    descriptor.forEachIndexed { index, c ->
        if(firstSectionProcessing){
            if((c == ';')&&(descriptor.elementAt(index + 1) == ';')){
                firstSectionProcessing = false
                secondSectionStartIndex = index + 2
                dualDescriptor.isDual = true
            } else {
                if(c != '\r') {
                    dualDescriptor.elementText += c
                }
            }
        } else {
            if(index >= secondSectionStartIndex){
                if(c != '\r'){
                    dualDescriptor.actionText += c
                }
            }
        }
    }
    return dualDescriptor
}

fun decryptOptionSelectorString(data: String) : ArrayList<String> {
    val list = ArrayList<String>()

    var str = ""
    var nextIndex = 0

    data.forEachIndexed { index, c ->

        when(c){
            ';' -> {
                if (index < (data.length - 1)) {
                    if (data.elementAt(index + 1) == ';') {
                        list.add(str)
                        str = ""
                        nextIndex = index + 2
                    }
                }
            }
            else -> {
                if(index >= nextIndex){
                    str += c
                }
            }
        }
    }
    if(str.isNotEmpty()){
        list.add(str)
    }
    return list
}

fun a8bitValueTo2CharHexValue(bVal: Int) : String {
    return if(bVal > 255){
        "EE"
    } else {
        var resultString = ""
        val hexStr = Integer.toHexString(bVal)
        if (hexStr.length > 1) {
            resultString = hexStr
        } else {
            resultString += '0'
            resultString += hexStr
        }
        resultString
    }
}

fun aSigned16bitValueTo4CharHexValue(bVal: Short): String {
    val a = bVal.toUShort()
    val hexString =
        Integer.toHexString(a.toInt())
    return when (hexString.length) {
        0 -> "eeee"
        1 -> "000$hexString"
        2 -> "00$hexString"
        3 -> "0$hexString"
        4 -> hexString
        else -> "over"
    }
}

fun a2CharHexValueToIntValue(c_left: Char, c_right: Char) : Int {
    val valueStr = "0x$c_left$c_right"
    return Integer.decode(valueStr)
}

fun a4CharHexValueToSignedIntValue(hl: Char, hr: Char, ll: Char, lr: Char) : Short {

    val highLeft = hexCharToNumber(hl)
    val highRight = hexCharToNumber(hr)
    val lowLeft = hexCharToNumber(ll)
    val lowRight = hexCharToNumber(lr)

    return  (highLeft.shl(12).or(highRight.shl(8)).or(lowLeft.shl(4).or(lowRight))).toShort()

    //val valueStr = "0x$hl$hr$ll$lr"
    //return Integer.decode(valueStr)
}

fun hexCharToNumber(c: Char) : Int {
    return when(c){
        '0' -> 0
        '1' -> 1
        '2' -> 2
        '3' -> 3
        '4' -> 4
        '5' -> 5
        '6' -> 6
        '7' -> 7
        '8' -> 8
        '9' -> 9
        'a' -> 10
        'b' -> 11
        'c' -> 12
        'd' -> 13
        'e' -> 14
        'f' -> 15
        'A' -> 10
        'B' -> 11
        'C' -> 12
        'D' -> 13
        'E' -> 14
        'F' -> 15
        else -> 0
    }
}

fun numberToHexChar(n: Int) : Char {
    return when(n){
        0 -> '0'
        1 -> '1'
        2 -> '2'
        3 -> '3'
        4 -> '4'
        5 -> '5'
        6 -> '6'
        7 -> '7'
        8 -> '8'
        9 -> '9'
        10 -> 'a'
        11 -> 'b'
        12 -> 'c'
        13 -> 'd'
        14 -> 'e'
        15 -> 'f'
        else -> 'f'
    }
}

fun createRandomPasskey(keyLength: Int): String {

    var realKeyLength = keyLength - 1
    if(realKeyLength < 0)
        realKeyLength = 0

    var randomPasskey = ""

    for(i in 0..realKeyLength){
        randomPasskey += passKeyCharacter[floor(Math.random() * passKeyCharacter.length).toInt()]
    }
    return randomPasskey
}

fun isCharAcceptedInPassKey(c: Char) : Boolean {
    passKeyCharacter.forEach {
        if(it == c){
            return true
        }
    }
    return false
}

fun validatePassKey(key: String) : Boolean{
    key.forEach {
        if(!isCharAcceptedInPassKey(it)){
            return false
        }
    }
    return true
}


class PercentageLevelPropertyGenerator(E8bit_level: Int){

    private val percentAsInt = get8BitValueAsPercent(E8bit_level)

    val percentageValue : Int
    get() = percentAsInt

    val percentageString: String? by lazy (LazyThreadSafetyMode.NONE){
        "$percentAsInt%"
    }
    val colorID: Int by lazy(LazyThreadSafetyMode.NONE){
        colorForPercentageLevel(percentAsInt)
    }
}