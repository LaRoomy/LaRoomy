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

// the numbers must conform to the types of the device!
const val PROPERTY_TYPE_BUTTON = 1
const val PROPERTY_TYPE_SWITCH = 2
const val PROPERTY_TYPE_LEVEL_SELECTOR = 3
const val PROPERTY_TYPE_LEVEL_INDICATOR = 4
const val PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY = 5



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
        11 -> R.drawable.clock_reload
        12 -> R.drawable.time_setup_white
        13 -> R.drawable.property_group_blue_white
        14 -> R.drawable.reload_turnleft_blue
        15 -> R.drawable.reload_turnleft_white
        16 -> R.drawable.reload_turnright_white
        17 -> R.drawable.segmented_circle_blue_white
        18 -> R.drawable.sync_white
        19 -> R.drawable.sync_blue_white
        20 -> R.drawable.scale_up_white_blue
        21 -> R.drawable.bars_indef_white
        22 -> R.drawable.bars_increasing_blue
        23 -> R.drawable.bars_increasing_white
        24 -> R.drawable.level_75percent
        25 -> R.drawable.level_50percent
        26 -> R.drawable.level_25percent
        27 -> R.drawable.level_0percent
        28 -> R.drawable.warning_white_blue
        29 -> R.drawable.warning_white
        30 -> R.drawable.warning_yellow
        31 -> R.drawable.warning_red
        32 -> R.drawable.settings_blue_white
        33 -> R.drawable.settings1_white
        34 -> R.drawable.settings1_blue
        35 -> R.drawable.settings2_blue_white
        36 -> R.drawable.settings3_blue_white
        37 -> R.drawable.tool_blue_white
        38 -> R.drawable.human_setup_blue_white
        39 -> R.drawable.human_setup2_blue_white
        40 -> R.drawable.star_blue
        41 -> R.drawable.check_mark_blue
        42 -> R.drawable.star_white
        43 -> R.drawable.check_mark_white
        44 -> R.drawable.asterisk_white

        else -> R.drawable.image_error_state
    }
}

interface OnItemClickListener {
    fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel)
}

interface OnPropertyClickListener{
    fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation)
}
