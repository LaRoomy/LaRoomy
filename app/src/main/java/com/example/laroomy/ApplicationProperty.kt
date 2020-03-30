package com.example.laroomy

import android.app.Application
import java.util.*

const val ERROR_MESSAGE = 0
const val WARNING_MESSAGE = 1
const val INFO_MESSAGE = 2

const val UNDEFINED_ELEMENT = 0
const val PROPERTY_ELEMENT = 1
const val GROUP_ELEMENT = 2
const val SEPARATOR_ELEMENT = 3
const val NO_CONTENT_ELEMENT = 4

class ApplicationProperty : Application() {

    companion object {
        lateinit var bluetoothConnectionManger: BLEConnectionManager
    }

    lateinit var systemLanguage: String

    //var userNavigatedFromCommActivity = false

    override fun onCreate() {
        super.onCreate()
       bluetoothConnectionManger = BLEConnectionManager()
        systemLanguage = Locale.getDefault().displayLanguage
    }
}

fun resourceIdForImageId(imageID: Int): Int {
    return when (imageID) {
        -1 -> R.drawable.image_error_state
        0 -> R.drawable.image_error_state
        1 -> R.drawable.placeholder_blue_white
        2 -> R.drawable.rgb_control_colored
        3 -> R.drawable.lightbulb_darkblue
        4 -> R.drawable.lightbulb_lightblue
        5 -> R.drawable.lightbulb_monocrom
        6 -> R.drawable.lightbulb_scheme
        7 -> R.drawable.sun_yellow
        8 -> R.drawable.sun_white
        9 -> R.drawable.sun_blue
        10 -> R.drawable.sun_blue_monocrom
        else -> 4
    }
}

class DevicePropertyListContentInformation{
    var canNavigateForward = false
    var elementType = SEPARATOR_ELEMENT
    var elementIndex = -1
    var elementText = ""
    var elementID = -1
    var imageID = -1
}

interface OnItemClickListener {
    fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel)
}

interface OnPropertyClickListener{
    fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation)
}
