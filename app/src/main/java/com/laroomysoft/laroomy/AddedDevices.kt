package com.laroomysoft.laroomy

import android.content.Context

class KnownDevices(var macAddress:String, var name:String){
    
    fun toPresentationModel(isPremium: Boolean) : LaRoomyDevicePresentationModel {
        val dev = LaRoomyDevicePresentationModel()
        dev.address = this.macAddress
        
        if(isPremium){
            // check name string for the image definition
            val imageID = checkDeviceNameForImageDefinition(this.name)
            if(imageID >= 0){
                // definition exists and is valid
                dev.image = resourceIdForImageId(imageID, DEVICE_ELEMENT, true)
                
                // remove trailing image definition
                var realName = ""
                this.name.forEachIndexed { index, c ->
                    if(index < (realName.length - 3)){
                            realName += c
                    } else {
                        return@forEachIndexed
                    }
                }
                dev.name = realName
            } else {
                // there is no valid image definition in the name string
                dev.name = this.name
                dev.image = when {
                    (isLaroomyDevice(name)) -> R.drawable.gn_laroomy_48
                    else -> R.drawable.ic_181_bluetooth
                }
            }
        } else {
            dev.name = this.name
            dev.image = when {
                (isLaroomyDevice(name)) -> R.drawable.gn_laroomy_48
                else -> R.drawable.ic_181_bluetooth
            }
        }
        return dev
    }
}

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