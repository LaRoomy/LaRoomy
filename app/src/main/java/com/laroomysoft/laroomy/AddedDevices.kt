package com.laroomysoft.laroomy

import android.content.Context

class KnownDevices(var macAddress:String, var name:String)

class AddedDevices(private val appContext: Context) {

    val devices = ArrayList<KnownDevices>()

    init {
        this.load()
    }

    fun add(mac: String, name: String){
        this.devices.add(
            KnownDevices(mac, name)
        )
        this.save()
    }


    fun add(fullDeviceString: String){
        if(fullDeviceString.isNotEmpty()){
            this.devices.add(
                this.readFullDeviceString(fullDeviceString)
            )
            this.save()
        }
    }
    
    fun getMacAddressAt(index: Int) : String {
        return if(index >= 0 && index < this.devices.size){
            this.devices.elementAt(index).macAddress
        } else {
            ""
        }
    }

/*
    fun clearAll(){
        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_AddedDevices),
                Context.MODE_PRIVATE
            )
        with(sharedPref.edit()) {
            clear()
            commit()
        }
    }
*/

    fun removeAt(index: Int){
        if(index >= 0 && index < this.devices.size) {
            val adr = devices.elementAt(index).macAddress
            this.remove(adr)
        }
    }

    fun remove(macAddress: String){

        var indexToRemove = -1

        this.devices.forEachIndexed { index, knownDevices ->
            if(knownDevices.macAddress == macAddress){
                indexToRemove = index
            }
        }

        if(indexToRemove > -1) {
            this.devices.removeAt(indexToRemove)

            val sharedPref =
                appContext.getSharedPreferences(
                    appContext.getString(R.string.FileKey_AddedDevices),
                    Context.MODE_PRIVATE
                )
            with(sharedPref.edit()) {
                clear()
                commit()
            }
            this.save()
        }
    }

    private fun save(){

        appContext.getSharedPreferences(
            appContext.getString(R.string.FileKey_AddedDevices),
            Context.MODE_PRIVATE
        ).apply {

            devices.forEachIndexed { index, knownDevices ->
                edit()
                    .putString("device$index", "${knownDevices.macAddress}#${knownDevices.name}")
                    .apply()
            }
        }
    }

    private fun load(){
        var index = 0

        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_AddedDevices),
                Context.MODE_PRIVATE
            )

        while(true){
            val lookupKey = "device$index"

            val data = sharedPref.getString(lookupKey, ERROR_NOTFOUND)
            if(data == ERROR_NOTFOUND){
                break
            } else {
                add(data ?: "")
            }

            index++
        }
    }

    private fun readFullDeviceString(data: String): KnownDevices {
        var address = ""
        var name = ""

        data.forEachIndexed { index, c ->
            when {
                (index < 17) -> {
                    address += c
                }
                (index > 17) -> {
                    name += c
                }
            }
        }
        return KnownDevices(address, name)
    }

    fun isAdded(macAddress: String) : Boolean{
        for (device in this.devices) {
            if(device.macAddress == macAddress){
                return true
            }
        }
        return false
    }
    
    fun lookupForDeviceNameWithAddress(address: String) : String {
        this.devices.forEach {
            if(it.macAddress == address){
                return it.name
            }
        }
        return ""
    }
}