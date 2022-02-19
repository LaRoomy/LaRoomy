package com.laroomysoft.laroomy

import android.app.Application
import android.content.Context
import android.util.Log
import java.util.*
import kotlin.collections.ArrayList

const val ERROR_MESSAGE = 0
const val WARNING_MESSAGE = 1
const val INFO_MESSAGE = 2

const val UNDEFINED_ELEMENT = 0
const val PROPERTY_ELEMENT = 1
const val GROUP_ELEMENT = 2

// the numbers must conform to the types of the device!
const val PROPERTY_TYPE_BUTTON = 1
const val PROPERTY_TYPE_SWITCH = 2
const val PROPERTY_TYPE_LEVEL_SELECTOR = 3
const val PROPERTY_TYPE_LEVEL_INDICATOR = 4
const val PROPERTY_TYPE_SIMPLE_TEXT_DISPLAY = 5
const val PROPERTY_TYPE_OPTION_SELECTOR = 6

const val COMPLEX_PROPERTY_START_INDEX = 7

// complex property type IDs
const val COMPLEX_PROPERTY_TYPE_ID_RGB_SELECTOR = 7
const val COMPLEX_PROPERTY_TYPE_ID_EX_LEVEL_SELECTOR = 8
const val COMPLEX_PROPERTY_TYPE_ID_TIME_SELECTOR = 9
const val COMPLEX_PROPERTY_TYPE_ID_TIME_FRAME_SELECTOR = 10
const val COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL = 11
const val COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR = 12
const val COMPLEX_PROPERTY_TYPE_ID_BARGRAPH = 13
const val COMPLEX_PROPERTY_TYPE_ID_LINEGRAPH = 14

const val DEVICE_SETTINGS_ACTIVITY_ELEMENT_INDEX_DUMMY = 1025

// change this to true to enable an internal verbose log output (this means not the user-log!)
const val verboseLog = true

// default value
const val COMPLEX_PROPERTY_STANDALONE_MODE_DEFAULT_VALUE = false

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
const val ERROR_INVALID_FORMAT = "error - invalid format"
const val ERROR_INVALID_PARAMETER = "error - invalid character"

// links
const val LAROOMY_WEBAPI_BASIS_LINK = "https://epl2-datatransmission.blogspot.com/p/redirect.html?"

// times
const val TIMEFRAME_PROPERTY_STATE_UPDATE_ON_RECONNECT:Long = 1000

class ApplicationProperty : Application() {

    companion object {
        lateinit var bluetoothConnectionManager: BLEConnectionManager
    }

    lateinit var systemLanguage: String

    // navigation control parameter:
    var navigatedFromPropertySubPage = false
    var noConnectionKillOnPauseExecution = false
    var uiAdapterChanged = false
    var uiAdapterInvalidatedOnPropertySubPage = false
    var complexPropertyUpdateRequired = false
    var appSettingsResetDone = false
    var eventLogEnabled = false
    var mainActivityListElementWasAdded = false
    var complexUpdateIndex = -1
    var propertyInvalidatedOnSubPage = false


    //var userNavigatedFromCommActivity = false

    lateinit var uuidManager: UUIDManager
    lateinit var addedDevices: AddedDevices

    var connectionLog = ArrayList<String>()
    var logRecordingTime = ""

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager = BLEConnectionManager(this)


        //systemLanguage = Locale.getDefault().displayLanguage


        systemLanguage = Locale.getDefault().language

        eventLogEnabled = this.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)

        // add the initial placeholder value
        //this.connectionLog.add(getString(R.string.ConnectionLog_NoContent))
    }

    fun resetControlParameter(){
        this.noConnectionKillOnPauseExecution = false
        this.uiAdapterChanged = false
        this.complexPropertyUpdateRequired = false
        this.complexUpdateIndex = -1
        this.navigatedFromPropertySubPage = false
    }

    fun logControl(message: String){
        if(eventLogEnabled){
            this.connectionLog.add(message)
        }
    }

    fun getCurrentUsedPasskey() : String {

        val useCustomKey =
            this.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_UseCustomBindingKey)

        return if(useCustomKey){
            this.loadSavedStringData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_CustomBindingPasskey
            )
        } else {
            this.loadSavedStringData(
                R.string.FileKey_AppSettings,
                R.string.DataKey_DefaultRandomBindingPasskey
            )
        }
    }

    fun loadSavedStringData(fileKeyID: Int, dataKeyID: Int) : String {

        if(verboseLog) {
            Log.d(
                "M:LoadSavedStringData",
                "Loading String-Data. FileKey: ${getString(fileKeyID)} / DataKey: ${
                    getString(dataKeyID)
                }"
            )
        }

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

        if(verboseLog) {
            Log.d("M:SaveStringData", "Saving data to memory - Data: $data")
        }

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

    fun saveBooleanData(value: Boolean, fileKeyID: Int, dataKeyID: Int){
        val stringValue = when(value){
            true -> "true"
            else -> "false"
        }
        this.saveStringData(stringValue, fileKeyID, dataKeyID)
    }

    fun loadBooleanData(fileKeyID: Int, dataKeyID: Int): Boolean{
        return when(this.loadSavedStringData(fileKeyID, dataKeyID)){
            "true" -> true
            "false" -> false
            else -> false
        }
    }

    fun loadBooleanData(fileKeyID: Int, dataKeyID: Int, defaultValue: Boolean): Boolean{
        return when(this.loadSavedStringData(fileKeyID, dataKeyID)){
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }

    fun deleteData(fileKeyID: Int, dataKeyID: Int) {
        val sharedPref =
            getSharedPreferences(
                getString(fileKeyID),
                Context.MODE_PRIVATE
            )
        with(sharedPref.edit()) {
            remove(
                getString(dataKeyID)
            )
            commit()
        }
    }

    fun deleteFileWithFileKey(fileKeyID: Int){
        val sharedPref =
            getSharedPreferences(
                getString(fileKeyID),
                Context.MODE_PRIVATE
            )
        with(sharedPref.edit()) {
            clear()
            commit()
        }
    }
}

fun resourceIdForImageId(imageID: Int): Int {
    return when (imageID) {
        -1 -> R.drawable.ic_00_image_error_state_vect       // done
        0 -> R.drawable.ic_00_image_error_state_vect        // done
        1 -> R.drawable.ic_01_placeholder_image_vect        // done
        2 -> R.drawable.ic_02_adjust_vect                   // done
        3 -> R.drawable.lightbulb_darkblue
        4 -> R.drawable.lightbulb_lightblue
        5 -> R.drawable.lightbulb_monocrom
        6 -> R.drawable.ic_06_lightbulb_blue_white_vect     // done
        7 -> R.drawable.ic_07_sun_yellow_vect               // done - with errors
        8 -> R.drawable.ic_08_sun_white_vect                // done - with errors
        9 -> R.drawable.ic_09_sun_blue_vect                 // done - with errors
        10 -> R.drawable.ic_10_clock_running_blue_white_vect// done
        11 -> R.drawable.ic_11_clock_reload_blue_white_vect // done
        12 -> R.drawable.ic_12_time_setup_white_vect        // done
        13 -> R.drawable.ic_13_property_group_blue_white_vect// done - with errors
        14 -> R.drawable.ic_14_reload_turnleft_blue_vect    // done
        15 -> R.drawable.ic_15_reload_turnleft_white_vect   // done
        16 -> R.drawable.ic_16_reload_turnright_white_vect  // done - with errors
        17 -> R.drawable.ic_17_segmented_circle_blue_white_vect // done
        18 -> R.drawable.ic_18_sync_white_vect              // done
        19 -> R.drawable.ic_19_sync_blue_white_vect         // done
        20 -> R.drawable.ic_20_scale_up_blue_white_vect     // done
        21 -> R.drawable.ic_21_bars_indifferent_white_vect  // done
        22 -> R.drawable.ic_22_increasing_bars_blue_vect    // done
        23 -> R.drawable.ic_23_level_100p_white_vect        // done
        24 -> R.drawable.ic_24_level_75p_vect               // done
        25 -> R.drawable.ic_25_level_50p_vect               // done
        26 -> R.drawable.ic_26_level_25p_vect               // done
        27 -> R.drawable.ic_27_level_0p_vect                // done
        28 -> R.drawable.ic_28_warning_blue_white_vect      // done
        29 -> R.drawable.ic_29_warning_white_vect           // done
        30 -> R.drawable.ic_30_warning_yellow_vect          // done
        31 -> R.drawable.ic_31_warning_red_vect             // done
        32 -> R.drawable.ic_32_settings_blue_white_vect     // done
        33 -> R.drawable.ic_33_settings1_white_vect         // done
        34 -> R.drawable.ic_34_settings1_blue_vect          // done
        35 -> R.drawable.ic_35_settings2_blue_white_vect    // done
        36 -> R.drawable.ic_36_settings3_blue_white_vect    // done
        37 -> R.drawable.ic_37_conjunction_blue_white_vect  // done
        38 -> R.drawable.ic_38_human_setup1_blue_white_vect // done
        39 -> R.drawable.ic_39_human_setup2_blue_white_vect // done
        40 -> R.drawable.ic_40_star_blue_vect               // done
        41 -> R.drawable.ic_41_checkmark_blue_vect          // done
        42 -> R.drawable.ic_42_star_white_vect              // done
        43 -> R.drawable.ic_43_checkmark_white_vect         // done
        44 -> R.drawable.ic_44_question_circle_white_vect   // done
        45 -> R.drawable.ic_45_question_circle_blue_vect    // done
        46 -> R.drawable.ic_46_trash_blue_white_vect        // done
        47 -> R.drawable.ic_47_lock_opened_blue_white_vect  // done
        48 -> R.drawable.ic_48_lock_closed_blue_white_vect  // done
        49 -> R.drawable.ic_49_location_blue_white_vect     // done
        50 -> R.drawable.ic_50_printer_blue_white_vect      // done
        51 -> R.drawable.ic_51_listing_items_blue_white_vect// done
        52 -> R.drawable.ic_52_world_blue_white_vect        // done
        53 -> R.drawable.ic_53_globe_blue_white_vect        // done
        54 -> R.drawable.ic_54_battery_loading_blue_white_vect// done
        55 -> R.drawable.ic_55_battery_100p_blue_white_vect // done
        56 -> R.drawable.ic_56_battery_75p_blue_white_vect  // done
        57 -> R.drawable.ic_57_battery_50p_blue_white_vect  // done
        58 -> R.drawable.ic_58_battery_25p_blue_white_vect  // done
        59 -> R.drawable.ic_59_battery_empty_white_vect     // done
        60 -> R.drawable.ic_60_battery_dead_red_vect        // done
        61 -> R.drawable.ic_61_rectangle_empty_white_vect   // done
        62 -> R.drawable.ic_62_rectangle_checked_blue_white_vect// done
        63 -> R.drawable.ic_63_rectangle_crossed_blue_white_vect// done
        64 -> R.drawable.ic_64_circle_empty_white_vect      // done
        65 -> R.drawable.ic_65_circle_checked_blue_white_vect// done
        66 -> R.drawable.ic_66_circle_crossed_blue_white_vect// done
        67 -> R.drawable.ic_67_cloud_white_vect             // done
        68 -> R.drawable.ic_68_cloud_blue_white_vect        // done
        69 -> R.drawable.ic_69_home_blue_vect               // done
        70 -> R.drawable.ic_70_home_white_vect              // done
        71 -> R.drawable.ic_71_home_blue_white_vect         // done
        72 -> R.drawable.ic_72_share_blue_white_vect        // done
        73 -> R.drawable.ic_73_wifi_blue_white_vect         // done
        74 -> R.drawable.ic_74_calculator_blue_white_vect   // done
        75 -> R.drawable.ic_75_people_blue_white_vect       // done
        76 -> R.drawable.ic_76_search_blue_white_vect       // done
        77 -> R.drawable.ic_77_hierachy_blue_white_vect     // done
        78 -> R.drawable.ic_78_doublehelix_blue_white_vect  // done
        79 -> R.drawable.ic_79_at_blue_vect                 // done
        80 -> R.drawable.ic_80_at_white_vect                // done
        81 -> R.drawable.ic_81_circle_1_blue_vect           // done -
        82 -> R.drawable.ic_82_circle_2_blue_vect           // done
        83 -> R.drawable.ic_83_circle_3_blue_vect           // done
        84 -> R.drawable.ic_84_circle_1_white_vect          // done
        85 -> R.drawable.ic_85_circle_2_white_vect          // done
        86 -> R.drawable.ic_86_circle_3_white_vect          // done
        87 -> R.drawable.ic_87_arrow_up_white_vect          // done
        88 -> R.drawable.ic_88_arrow_right_white_vect       // done
        89 -> R.drawable.ic_89_arrow_down_white_vect        // done
        90 -> R.drawable.ic_90_arrow_left_white_vect        // done
        91 -> R.drawable.ic_91_arrow_up_blue_vect           // done
        92 -> R.drawable.ic_92_arrow_right_blue_vect        // done
        93 -> R.drawable.ic_93_arrow_down_blue_vect         // done
        94 -> R.drawable.ic_94_arrow_left_blue_vect         // done
        95 -> R.drawable.tv_white
        96 -> R.drawable.tv_white_blue
        97 -> R.drawable.arrow_up_down_blue_white
        98 -> R.drawable.arrow_left_right_blue_white
        99 -> R.drawable.hand_blue_white
        100 -> R.drawable.info_white_blue
        101 -> R.drawable.stop_sign
        102 -> R.drawable.shield_power
        103 -> R.drawable.shield_ok
        104 -> R.drawable.shield_attention
        105 -> R.drawable.lock_unlocked_blue_white
        106 -> R.drawable.key_white
        107 -> R.drawable.key_blue
        108 -> R.drawable.face_id_white
        109 -> R.drawable.undo_white
        110 -> R.drawable.redo_white
        111 -> R.drawable.flash_blue
        112 -> R.drawable.flash_yellow
        113 -> R.drawable.add_white_sq48_simple
        114 -> R.drawable.add_blue_sq48_simple
        115 -> R.drawable.ic_115_rgb_circles_vect               // done - with errors
        116 -> R.drawable.ic_116_rgb_bars_vect
        117 -> R.drawable.ic_117_rgb_point_circle_vect          // done - with errors

        118 -> R.drawable.ic_118_tool_circle_blue_white_vect    // done
        119 -> R.drawable.ic_119_tools_blue_white_vect          // done

        // TODO: implement all new image resources here...

        else -> R.drawable.ic_00_image_error_state_vect
    }
}


interface OnDeviceListItemClickListener {
    fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel)
}

interface OnAddDeviceListItemClickListener {
    fun onItemClicked(index: Int)
}

interface OnUUIDProfileListItemClickListener {
    fun onItemClicked(index: Int, data: UUIDProfile)
}

interface OnPropertyClickListener {
    fun onPropertyElementButtonClick(index: Int)
    fun onPropertyElementSwitchClick(index: Int, state: Boolean)
    fun onPropertyLevelSelectButtonClick(index: Int)
    fun onPropertyOptionSelectButtonClick(index: Int)
    fun onSeekBarPositionChange(index: Int, newValue: Int, changeType: Int)
    fun onNavigatableElementClick(index: Int)
}
