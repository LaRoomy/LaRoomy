package com.example.laroomy

import android.app.Application
import java.util.*

const val ERROR_MESSAGE = 0
const val WARNING_MESSAGE = 1
const val INFO_MESSAGE = 2

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

interface OnItemClickListener {
    fun onItemClicked(index: Int, data: LaRoomyDevicePresentationModel)
}
