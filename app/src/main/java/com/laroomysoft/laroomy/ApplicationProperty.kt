package com.laroomysoft.laroomy

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.widget.SwitchCompat
import java.util.*
import kotlin.collections.ArrayList

const val ERROR_MESSAGE = 0
const val WARNING_MESSAGE = 1
const val INFO_MESSAGE = 2

const val UNDEFINED_ELEMENT = 0
const val PROPERTY_ELEMENT = 1
const val GROUP_ELEMENT = 2
//const val SEPARATOR_ELEMENT = 3
//const val NO_CONTENT_ELEMENT = 4

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
const val COMPLEX_PROPERTY_TYPE_ID_BARGRAPHDISPLAY = 13


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

class ApplicationProperty : Application() {

    companion object {
        lateinit var bluetoothConnectionManager: BLEConnectionManager
    }

    lateinit var systemLanguage: String

    // navigation control parameter:
    var navigatedFromPropertySubPage = false
    var noConnectionKillOnPauseExecution = false
    var uiAdapterChanged = false
    var complexPropertyUpdateRequired = false
    var appSettingsResetDone = false
    var eventLogEnabled = false
    var mainActivityListElementWasAdded = false
    var complexUpdateID = -1

    //var userNavigatedFromCommActivity = false

    lateinit var uuidManager: UUIDManager
    lateinit var addedDevices: AddedDevices

    var connectionLog = ArrayList<String>()
    var logRecordingTime = ""

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager = BLEConnectionManager(this)
        systemLanguage = Locale.getDefault().displayLanguage

        eventLogEnabled = this.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)

        // add the initial placeholder value
        this.connectionLog.add(getString(R.string.ConnectionLog_NoContent))
    }

    fun resetControlParameter(){
        this.noConnectionKillOnPauseExecution = false
        this.uiAdapterChanged = false
        this.complexPropertyUpdateRequired = false
        this.complexUpdateID = -1
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
        51 -> R.drawable.listing_items_white_blue
        52 -> R.drawable.world_blue_white
        53 -> R.drawable.globe_blue_white
        54 -> R.drawable.battery_loading
        55 -> R.drawable.battery_100_perc
        56 -> R.drawable.battery_75_perc
        57 -> R.drawable.battery_50_perc
        58 -> R.drawable.battery_25_perc
        59 -> R.drawable.battery_empty_white
        60 -> R.drawable.battery_empty_red
        61 -> R.drawable.rectangle_empty_white
        62 -> R.drawable.rectangle_white_checked_blue
        63 -> R.drawable.rectangle_white_crossed_blue
        64 -> R.drawable.circle_empty_white
        65 -> R.drawable.circle_checked_blue_white
        66 -> R.drawable.circle_crossed_blue_white
        67 -> R.drawable.cloud_white
        68 -> R.drawable.cloud_blue_white
        69 -> R.drawable.home
        70 -> R.drawable.house_white
        71 -> R.drawable.house_blue_white
        72 -> R.drawable.share
        73 -> R.drawable.wifi
        74 -> R.drawable.calculator
        75 -> R.drawable.people_blue_white
        76 -> R.drawable.search
        77 -> R.drawable.hierachy_blue_white
        78 -> R.drawable.double_helix_white_blue
        79 -> R.drawable.at_blue
        80 -> R.drawable.at_white
        81 -> R.drawable.one_circle_blue
        82 -> R.drawable.two_circle_blue
        83 -> R.drawable.three_circle_blue
        84 -> R.drawable.one_circle_white
        85 -> R.drawable.two_circle_white
        86 -> R.drawable.three_circle_white
        87 -> R.drawable.arrow_up_white
        88 -> R.drawable.arrow_right_white
        89 -> R.drawable.arrow_down_white
        90 -> R.drawable.arrow_left_white
        91 -> R.drawable.arrow_up_blue
        92 -> R.drawable.arrow_right_blue
        93 -> R.drawable.arrow_down_blue
        94 -> R.drawable.arrow_left_blue
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

        // TODO: implement all new image resources here...

        else -> R.drawable.image_error_state
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
    //fun onPropertyClicked(index: Int, data: DevicePropertyListContentInformation)
    fun onPropertyElementButtonClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation)
    fun onPropertyElementSwitchClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation, switch: SwitchCompat)
    fun onPropertyLevelSelectButtonClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation)
    fun onSeekBarPositionChange(index: Int, newValue: Int, changeType: Int)
    fun onNavigatableElementClick(index: Int, devicePropertyListContentInformation: DevicePropertyListContentInformation)
}
