package com.laroomysoft.laroomy

import android.app.Application
import android.content.Context
import android.util.Log

const val NOTIFICATION_TYPE_ERROR = 0
const val NOTIFICATION_TYPE_WARNING = 1
const val NOTIFICATION_TYPE_INFO = 2

const val STATUS_DISCONNECTED = 0
const val STATUS_CONNECTED = 1
const val STATUS_CONNECTING = 2


//const val UNDEFINED_ELEMENT = -1
const val PROPERTY_ELEMENT = 1
const val GROUP_ELEMENT = 2
const val DEVICE_ELEMENT = 3

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
const val COMPLEX_PROPERTY_TYPE_ID_DATE_SELECTOR = 11
const val COMPLEX_PROPERTY_TYPE_ID_UNLOCK_CONTROL = 12
const val COMPLEX_PROPERTY_TYPE_ID_NAVIGATOR = 13
const val COMPLEX_PROPERTY_TYPE_ID_BARGRAPH = 14
const val COMPLEX_PROPERTY_TYPE_ID_LINEGRAPH = 15
const val COMPLEX_PROPERTY_TYPE_ID_STRING_INTERROGATOR = 16
const val COMPLEX_PROPERTY_TYPE_ID_TEXT_LIST_PRESENTER = 17

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

// device header click actions
const val DEV_HEADER_CLICK_ACTION_NONE = 0
const val DEV_HEADER_CLICK_ACTION_COMPLEX_RELOAD = 1

// max values
const val NON_PREMIUM_MAX_DEVICE_LIST_INDEX = 1

class ApplicationProperty : Application() {

    companion object {
        lateinit var bluetoothConnectionManager: BLEConnectionManager
    }

    lateinit var systemLanguage: String

    // navigation control parameter:
    var navigatedFromPropertySubPage = false
    var delayedNavigationNotificationRequired = false
    var noConnectionKillOnPauseExecution = false
    var uiAdapterChanged = false
    var uiAdapterInvalidatedOnPropertySubPage = false
    var complexPropertyUpdateRequired = false
    var isBackNavigationToMain = false
    var appSettingsResetDone = false
    var eventLogEnabled = false
    var isNightMode = false
    var mainActivityListElementWasAdded = false
    var complexUpdateIndex = -1
    var propertyInvalidatedOnSubPage = false
    var closeDeviceRequested = false
    
    val isPremiumAppVersion
    get() = this.premiumManager.isPremiumAppVersion

    lateinit var uuidManager: UUIDManager
    lateinit var addedDevices: AddedDevices
    lateinit var premiumManager: PremiumManager

    var connectionLog = ArrayList<String>()
    var logRecordingTime = ""
    
    lateinit var cViewContext: Context

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager = BLEConnectionManager(this)
        
        premiumManager = PremiumManager(this.applicationContext)
        
        //systemLanguage = Locale.getDefault().displayLanguage - returns 'Deutsch' for example
        //systemLanguage = Locale.getDefault().language - returns the system language in iso 639-1 format 'de' for example
        systemLanguage = resources.configuration.locales.get(0).language // returns the app language in iso 639-1 format

        eventLogEnabled = this.loadBooleanData(R.string.FileKey_AppSettings, R.string.DataKey_EnableLog)
    }
    
    fun onResume(){
        systemLanguage = resources.configuration.locales.get(0).language // returns the app language in iso 639-1 format
    }

    fun resetPropertyControlParameter(){
        this.noConnectionKillOnPauseExecution = false
        this.uiAdapterChanged = false
        this.uiAdapterInvalidatedOnPropertySubPage = false
        this.complexPropertyUpdateRequired = false
        this.complexUpdateIndex = -1
        this.navigatedFromPropertySubPage = false
        this.propertyInvalidatedOnSubPage = false
        this.closeDeviceRequested = false
    }

    fun logControl(message: String){
        if(eventLogEnabled){
            if(this.connectionLog.size > 300){
                this.connectionLog.removeAt(0)
            }
            this.connectionLog.add(message)
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
    
    fun loadSavedStringData(fileKeyID: Int, dataKeyID: Int, defaultValue: String) : String {
        
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
            defaultValue
        )
        return data ?: defaultValue
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

interface OnDeviceListItemClickListener {
    fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel)
}

interface OnAddDeviceActivityListItemClickListener {
    fun onItemClicked(index: Int, type: Int)
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
