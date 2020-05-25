package com.laroomysoft.laroomy

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Switch
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

// complex property type IDs
const val COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR = 6
const val COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR = 7
const val COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR = 8
const val COMPLEX_PROPERTY_TYPE_ID_TIME_ELAPSE_SELECTOR = 9
const val COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR = 10


// seekBar handler types
const val SEEK_BAR_START_TRACK = 1
const val SEEK_BAR_STOP_TRACK = 2
const val SEEK_BAR_PROGRESS_CHANGING = 3

// rgb modes
const val RGB_MODE_OFF = 0
const val RGB_MODE_SINGLE_COLOR = 1
const val RGB_MODE_TRANSITION = 2

// error indicator
const val ERROR_NOTFOUND = "error - not found"

class ApplicationProperty : Application() {

    companion object {
        lateinit var bluetoothConnectionManger: BLEConnectionManager
    }

    lateinit var systemLanguage: String

    // navigation control parameter:
    var navigatedFromPropertySubPage = false
    var noConnectionKillOnPauseExecution = false
    var uiAdapterChanged = false

    //var userNavigatedFromCommActivity = false

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManger = BLEConnectionManager(this)
        systemLanguage = Locale.getDefault().displayLanguage
    }

    fun loadSavedStringData(fileKeyID: Int, dataKeyID: Int) : String {
        Log.d("M:LoadSavedStringData", "Loading String-Data. FileKey: ${getString(fileKeyID)} / DataKey: ${getString(dataKeyID)}")

        val sharedPref = getSharedPreferences(
            getString(fileKeyID),
            Context.MODE_PRIVATE)
        val data = sharedPref.getString(
            getString(dataKeyID),
            ERROR_NOTFOUND
        )
        return data ?: ""
    }

    fun saveStringData(data: String, fileKeyID: Int, dataKeyID: Int){
        Log.d("M:SaveStringData", "Saving data to memory - Data: $data")

        if(data.isNotEmpty()) {
            val sharedPref =
                getSharedPreferences(
                    getString(fileKeyID),
                    Context.MODE_PRIVATE
                )
            with(sharedPref.edit()) {
                putString(
                    getString(dataKeyID),
                    data)
                commit()
            }
        }
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
        44 -> R.drawable.helpcircle_white
        45 -> R.drawable.helpcircle_blue
        46 -> R.drawable.star_round_white
        47 -> R.drawable.star_round_blue
        48 -> R.drawable.lock_blue_white
        49 -> R.drawable.location_blue_white
        50 -> R.drawable.printer_blue_white

        else -> R.drawable.image_error_state
    }
}


interface OnItemClickListener {
    fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel)
}

interface OnPropertyClickListener {
    //fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation)
    fun onPropertyElementButtonClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation)
    fun onPropertyElementSwitchClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation, switch: Switch)
    fun onSeekBarPositionChange(index: Int, newValue: Int, changeType: Int)
    fun onNavigatableElementClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation)
}
