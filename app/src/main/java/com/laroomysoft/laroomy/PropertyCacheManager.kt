package com.laroomysoft.laroomy

import android.content.Context

const val propertyCacheNameEntry = "PCacheF_"

class DevicePropertyCacheData {

    private val propertyStringList = ArrayList<String>()
    private val groupStringList = ArrayList<String>()

    val isValid: Boolean
    get() = this.propertyStringList.isNotEmpty()

    val deviceProperties: ArrayList<LaRoomyDeviceProperty>
    get() {
        val list = ArrayList<LaRoomyDeviceProperty>()
        if(this.propertyStringList.isNotEmpty()){
            this.propertyStringList.forEach {
                val ldp = LaRoomyDeviceProperty()
                ldp.fromString(it)
                list.add(ldp)
            }
        }
        return list
    }

    val devicePropertyGroups: ArrayList<LaRoomyDevicePropertyGroup>
    get() {
        val list = ArrayList<LaRoomyDevicePropertyGroup>()
        if(this.groupStringList.isNotEmpty()){
            this.groupStringList.forEach {
                val ldpg = LaRoomyDevicePropertyGroup()
                ldpg.fromString(it)
                list.add(ldpg)
            }
        }
        return list
    }

    fun generate(properties: ArrayList<LaRoomyDeviceProperty>, groups: ArrayList<LaRoomyDevicePropertyGroup>){
        if(properties.isNotEmpty()){
            properties.forEach {
                propertyStringList.add(
                    it.toString()
                )
            }
        }
        if(groups.isNotEmpty()){
            groups.forEach {
                groupStringList.add(
                    it.toString()
                )
            }
        }
    }

    fun toFileData() : String {

        var fileBuffer = ""

        this.propertyStringList.forEach {
            fileBuffer += "$it\n"
        }
        this.groupStringList.forEach {
            fileBuffer += "$it\n"
        }
        return fileBuffer
    }

    fun fromFileData(data: String) : Boolean {
        var holderString = ""

        data.forEach {
            if(it == '\n'){
                if(holderString.isNotEmpty()){
                    when(holderString.elementAt(0)) {
                        'P' -> {
                            this.propertyStringList.add(holderString)
                        }
                        'G' -> {
                            this.groupStringList.add(holderString)
                        }
                        else -> {
                            return false
                        }
                    }
                }
                // reset holder
                holderString = ""
            } else {
                // record line
                holderString += it
            }
        }
        if(holderString.isNotEmpty()) {
            // should be not necessary because the last char in the file must be a line-feed
            when (holderString.elementAt(0)) {
                'P' -> {
                    this.propertyStringList.add(holderString)
                }
                'G' -> {
                    this.groupStringList.add(holderString)
                }
                else -> {
                    return false
                }
            }
        }
        return true
    }
}

class PropertyCacheManager(val appContext: Context) {

    fun savePCacheData(devicePropertyCacheData: DevicePropertyCacheData, macAddress: String) {
        appContext.openFileOutput("$propertyCacheNameEntry$macAddress", Context.MODE_PRIVATE).use {
            it.write(devicePropertyCacheData.toFileData().toByteArray())
        }
    }

    fun loadPCacheData(macAddress: String) : DevicePropertyCacheData {

        var sBuffer: String

        appContext.openFileInput("$propertyCacheNameEntry$macAddress").bufferedReader().use {
            sBuffer = it.readText()
        }

        val devicePropertyCacheData = DevicePropertyCacheData()

        if(sBuffer.isNotEmpty()){
            devicePropertyCacheData.fromFileData(sBuffer)
        }

        return devicePropertyCacheData
    }
}