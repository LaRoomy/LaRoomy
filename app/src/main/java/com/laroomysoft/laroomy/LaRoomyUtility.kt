package com.laroomysoft.laroomy

import android.graphics.Color
import kotlin.math.floor

const val COMMON_PASSKEY_LENGHT = 10

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

fun get8BitValueAsPercent(value: Int) : Int {
    var ret = -1

    if(value in 0..255){
        ret = when {
            value > 0 -> ((value*100)/255)
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
    val value = 255*percent
    return if(value == 0) 0
    else value/100
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
        '0' -> 'Z'
        '1' -> 'Y'
        '2' -> 'X'
        '3' -> 'W'
        '4' -> 'V'
        '5' -> 'U'
        '6' -> 'T'
        '7' -> 'S'
        '8' -> 'R'
        '9' -> 'Q'
        'a' -> 'P'
        'b' -> 'O'
        'c' -> 'N'
        'd' -> 'M'
        'e' -> 'L'
        'f' -> 'K'
        'g' -> 'J'
        'h' -> 'I'
        'i' -> 'H'
        'j' -> 'G'
        'k' -> 'F'
        'l' -> 'E'
        'm' -> 'D'
        'n' -> 'C'
        'o' -> 'B'
        'p' -> 'A'
        'q' -> '0'
        'r' -> '1'
        's' -> '2'
        't' -> '3'
        'u' -> '4'
        'v' -> '5'
        'w' -> '6'
        'x' -> '7'
        'y' -> '8'
        'z' -> '9'

        'A' -> 'r'
        'B' -> 'q'
        'C' -> 'p'
        'D' -> 'o'
        'E' -> 'n'
        'F' -> 'm'
        'G' -> 'l'
        'H' -> 'k'
        'I' -> 'j'
        'J' -> 'i'
        'K' -> 'h'
        'L' -> 'g'
        'M' -> 'f'
        'N' -> 'e'
        'O' -> 'd'
        'P' -> 'c'
        'Q' -> 'b'
        'R' -> 'a'

        'S' -> 'z'
        'T' -> 'y'
        'U' -> 'x'
        'V' -> 'w'
        'W' -> 'v'
        'X' -> 'u'
        'Y' -> 't'
        'Z' -> 's'
//        '!' -> 'r'
//        '?' -> 'q'
//        '=' -> 'p'
//        ')' -> 'o'
//        '(' -> 'n'
//        '/' -> 'm'
//        '&' -> 'l'
//        '%' -> 'k'
//        '$' -> 'j'
//        '§' -> 'i'
//        '*' -> 'h'
//        '+' -> 'g'
//        '#' -> 'f'
//        '-' -> 'e'
//        '.' -> 'd'
//        ',' -> 'c'
//        ':' -> 'b'
//        ';' -> 'a'
        else -> c
    }
}

fun reSwapChar(c: Char): Char {
    return when(c){
        'Z' -> '0'
        'Y' -> '1'
        'X' -> '2'
        'W' -> '3'
        'V' -> '4'
        'U' -> '5'
        'T' -> '6'
        'S' -> '7'
        'R' -> '8'
        'Q' -> '9'
        'P' -> 'a'
        'O' -> 'b'
        'N' -> 'c'
        'M' -> 'd'
        'L' -> 'e'
        'K' -> 'f'
        'J' -> 'g'
        'I' -> 'h'
        'H' -> 'i'
        'G' -> 'j'
        'F' -> 'k'
        'E' -> 'l'
        'D' -> 'm'
        'C' -> 'n'
        'B' -> 'o'
        'A' -> 'p'
        '0' -> 'q'
        '1' -> 'r'
        '2' -> 's'
        '3' -> 't'
        '4' -> 'u'
        '5' -> 'v'
        '6' -> 'w'
        '7' -> 'x'
        '8' -> 'y'
        '9' -> 'z'

        'r' -> 'A'
        'q' -> 'B'
        'p' -> 'C'
        'o' -> 'D'
        'n' -> 'E'
        'm' -> 'F'
        'l' -> 'G'
        'k' -> 'H'
        'j' -> 'I'
        'i' -> 'J'
        'h' -> 'K'
        'g' -> 'L'
        'f' -> 'M'
        'e' -> 'N'
        'd' -> 'O'
        'c' -> 'P'
        'b' -> 'Q'
        'a' -> 'R'

        'z' -> 'S'
        'y' -> 'T'
        'x' -> 'U'
        'w' -> 'V'
        'v' -> 'W'
        'u' -> 'X'
        't' -> 'Y'
        's' -> 'Z'
//        'r' -> '!'
//        'q' -> '?'
//        'p' -> '='
//        'o' -> ')'
//        'n' -> '('
//        'm' -> '/'
//        'l' -> '&'
//        'k' -> '%'
//        'j' -> '$'
//        'i' -> '§'
//        'h' -> '*'
//        'g' -> '+'
//        'f' -> '#'
//        'e' -> '-'
//        'd' -> '.'
//        'c' -> ','
//        'b' -> ':'
//        'a' -> ';'
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

    val percentageString: String? by lazy (LazyThreadSafetyMode.NONE){
        "$percentAsInt%"
    }
    val colorID: Int by lazy(LazyThreadSafetyMode.NONE){
        colorForPercentageLevel(percentAsInt)
    }
}