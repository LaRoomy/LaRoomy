package com.example.laroomy

import android.graphics.Color

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

class PercentageLevelPropertyGenerator(E8bit_level: Int){

    private val percentAsInt = get8BitValueAsPercent(E8bit_level)

    val percentageString: String? by lazy (LazyThreadSafetyMode.NONE){
        "$percentAsInt%"
    }
    val colorID: Int by lazy(LazyThreadSafetyMode.NONE){
        colorForPercentageLevel(percentAsInt)
    }
}