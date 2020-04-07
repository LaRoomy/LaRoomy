package com.example.laroomy

import android.graphics.Color

fun percentageFrom8BitValue(value: Int): Int{

    var pLevel =
        (value / 255) * 100

    if(pLevel > 100)
        pLevel = 100
    else if(pLevel < 0)
        pLevel = 0

    return pLevel
}

fun colorForPercentageLevel(level: Int): Int {
    return if((level < 35)&&(level > 12)) Color.YELLOW
    else if(level <= 12) Color.RED
    else Color.GREEN
}

class PercentageLevelPropertyGenerator(E8bit_level: Int){

    private val percentAsInt = percentageFrom8BitValue(E8bit_level)

    val percentageString: String? by lazy (LazyThreadSafetyMode.NONE){
        "$percentAsInt%"
    }
    val colorID: Int by lazy(LazyThreadSafetyMode.NONE){
        colorForPercentageLevel(percentAsInt)
    }
}