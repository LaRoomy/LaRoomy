package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log
import java.io.FileNotFoundException
import java.lang.Exception

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
        if (macAddress.isNotEmpty()) {
            try {
                val dataToSave = devicePropertyCacheData.toFileData()
                if (dataToSave.isNotEmpty()) {
                    appContext.openFileOutput(
                        "$propertyCacheNameEntry$macAddress",
                        Context.MODE_PRIVATE
                    ).use {
                        it.write(dataToSave.toByteArray())
                    }
                } else {
                    Log.e("PropCacheManager", "Unexpected error in PropertyCacheManager. DataToSave was empty!")
                }
            } catch (e: Exception) {
                Log.e("PropCacheManager", "Unexpected error in PropertyCacheManager. Info: $e")
            }
        } else {
            Log.e(
                "PropCacheManager",
                "Invalid operation. MacAddress to save was empty. Skip save operation!"
            )
        }
    }

    fun loadPCacheData(macAddress: String) : DevicePropertyCacheData {

        var sBuffer = ""
        val devicePropertyCacheData = DevicePropertyCacheData()

        try {
            appContext.openFileInput("$propertyCacheNameEntry$macAddress").bufferedReader().use {
                sBuffer = it.readText()
            }
        } catch (e: FileNotFoundException){
            if(verboseLog){
                Log.d("PropCacheManager", "Lookup for file failed. File not found. For Mac-Address: $macAddress Exception: $e")
            }
        }

        if(sBuffer.isNotEmpty()){
            devicePropertyCacheData.fromFileData(sBuffer)
        }
        return devicePropertyCacheData
    }

    fun clearCache() {
        appContext.filesDir.listFiles()?.forEach {
            if (it.isFile && it.name.startsWith(propertyCacheNameEntry)) {
                it.delete()
            }
        }
    }
}