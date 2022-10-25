package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log

class BindingData {
    var macAddress = ""
    var passKey = ""
    var generatedAsOriginator = false
    var deviceName = ""
    
    override fun toString(): String {
        val macString = when(macAddress.isNotEmpty()){
            true -> macAddress
            else -> "<none>"
        }
        val keyString = when(passKey.isNotEmpty()){
            true -> passKey
            else -> "<none>"
        }
        val devString = when(deviceName.isNotEmpty()){
            true -> deviceName
            else -> "<none>"
        }
        val originString = when(generatedAsOriginator){
            true -> "true"
            else -> "false"
        }
        return "$macString#$keyString#$devString#$originString#"
    }
    
    fun fromString(string: String){
        var recordIndex = 0
        var recordString = ""
        
        string.forEach {
            if(it == '#'){
                if(recordString == "<none>"){
                    recordString = ""
                }
                when(recordIndex){
                    0 -> this.macAddress = recordString
                    1 -> this.passKey = recordString
                    2 -> this.deviceName = recordString
                    3 -> {
                        this.generatedAsOriginator = when(recordString){
                            "true" -> true
                            else -> false
                        }
                        return@forEach
                    }
                    else -> {
                        Log.e("BindingPairClass", "BindingPairClass::FromString: error invalid record index!")
                    }
                }
                recordString = ""
                recordIndex++
            } else {
                recordString += it
            }
        }
    }
}

class BindingDataManager(private val appContext: Context) {
    
    var bindingDataList = ArrayList<BindingData>()
    private set

    init {
        this.load()
    }

    fun add(data: BindingData){
        this.bindingDataList.add(data)
        this.save()
    }
    
    fun updateElement(data: BindingData){
        this.bindingDataList.forEach {
            if(it.macAddress == data.macAddress){
                it.passKey = data.passKey
                it.deviceName = data.deviceName
                it.generatedAsOriginator = data.generatedAsOriginator
                return@forEach
            }
        }
        this.save()
    }
    
    fun addOrUpdate(data: BindingData){
        var found = false
        this.bindingDataList.forEach {
            if(it.macAddress == data.macAddress){
                found = true
                it.passKey = data.passKey
                it.deviceName = data.deviceName
                it.generatedAsOriginator = data.generatedAsOriginator
                return@forEach
            }
        }
        if(!found){
            this.add(data)
        }
        this.save()
    }
    
    fun removeElement(data: BindingData){
        //this.clearAll()
        var indexToRemove = -1
        this.bindingDataList.forEachIndexed { index, bindingData ->
            if(bindingData.macAddress == data.macAddress){
                indexToRemove = index
                return@forEachIndexed
            }
        }
        if(indexToRemove != -1){
            this.clearAll()
            this.bindingDataList.removeAt(indexToRemove)
            this.save()
        }
    }
    
    fun clearAll() {
        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_BindingPairs),
                Context.MODE_PRIVATE
            )
        with(sharedPref.edit()) {
            clear()
            commit()
        }
    }

    fun lookUpForPassKeyWithMacAddress(mac: String) : String {
        this.bindingDataList.forEach{
            if(it.macAddress == mac){
                return it.passKey
            }
        }
        return ERROR_NOTFOUND
    }
    
    fun lookUpForBindingData(mac: String) : BindingData {
        this.bindingDataList.forEach {
            if(it.macAddress == mac){
                return it
            }
        }
        return BindingData()
    }

    private fun save() {
        if(this.bindingDataList.isNotEmpty()) {
    
            val dataKeyStarter = appContext.getString(R.string.DataKey_BindingPairStartKey)
    
            val sharedPref =
                appContext.getSharedPreferences(
                    appContext.getString(R.string.FileKey_BindingPairs),
                    Context.MODE_PRIVATE
                )
    
            this.bindingDataList.forEachIndexed { index, bindingPair ->
        
                val dataToSave = bindingPair.toString()
                val dataKey = "$dataKeyStarter$index"
        
                sharedPref
                    .edit()
                    .putString(dataKey, dataToSave)
                    .apply()
            }
        }
    }

    private fun load(){

        val dataKeyStarter = appContext.getString(R.string.DataKey_BindingPairStartKey)
        var counter = 0

        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_BindingPairs),
                Context.MODE_PRIVATE
            )

        while (true){
            val dataKey = "$dataKeyStarter$counter"

            val data = sharedPref.getString(dataKey, ERROR_NOTFOUND)
            if(data == ERROR_NOTFOUND){
                return
            } else {
                val loadedPair = BindingData()
                loadedPair.fromString(data ?: "")
                this.add(loadedPair)
            }
            counter++
        }
    }
}