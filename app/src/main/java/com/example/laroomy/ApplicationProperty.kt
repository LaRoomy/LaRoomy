package com.example.laroomy

import android.app.Application
import java.util.*

class ApplicationProperty : Application() {

    lateinit var bluetoothConnectionManger: BLEConnectionManager

    lateinit var systemLanguage: String

    //var userNavigatedFromCommActivity = false

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManger = BLEConnectionManager()
        systemLanguage = Locale.getDefault().displayLanguage
    }
}