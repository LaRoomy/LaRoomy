package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log

class BindingPair {
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
                recordIndex++
            } else {
                recordString += it
            }
        }
    }
}

class BindingPairManager(private val appContext: Context) {
    
    var bindingPairs = ArrayList<BindingPair>()
    private set

    init {
        this.load()
    }

    fun add(pair: BindingPair){
        this.bindingPairs.add(pair)
        this.save()
    }
    
    fun updatePair(pair: BindingPair){
        this.bindingPairs.forEach {
            if(it.macAddress == pair.macAddress){
                it.passKey = pair.passKey
                it.deviceName = pair.deviceName
                it.generatedAsOriginator = pair.generatedAsOriginator
                return@forEach
            }
        }
        this.save()
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
        this.bindingPairs.forEach{
            if(it.macAddress == mac){
                return it.passKey
            }
        }
        return ERROR_NOTFOUND
    }

    private fun save() {

        val dataKeyStarter = appContext.getString(R.string.DataKey_BindingPairStartKey)

        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_BindingPairs),
                Context.MODE_PRIVATE
            )

        this.bindingPairs.forEachIndexed { index, bindingPair ->

            val dataToSave = bindingPair.toString()
            val dataKey = "$dataKeyStarter$index"

            sharedPref
                .edit()
                .putString(dataKey, dataToSave)
                .apply()
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
                val loadedPair = BindingPair()
                loadedPair.fromString(data ?: "")
                this.add(loadedPair)
            }
            counter++
        }
    }
}