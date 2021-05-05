package com.laroomysoft.laroomy

import android.content.Context

class BindingPair {
    var macAddress = ""
    var passKey = ""
}

class BindingPairManager(private val appContext: Context) {

    private var bindingPairs = ArrayList<BindingPair>()

    init {
        this.load()
    }

    fun add(pair: BindingPair){
        this.bindingPairs.add(pair)
        this.save()
    }

    private fun add(encryptedString: String){
        if(encryptedString.isNotEmpty()){
            var mac = ""
            var key = ""
            var macRecorded = false

            encryptedString.forEachIndexed { _, c ->
                if(c == '#'){
                    macRecorded = true
                } else {
                    if(macRecorded){
                        key += c
                    } else {
                        mac += c
                    }
                }
            }
            if(key.isNotEmpty() && macRecorded){
                val pair = BindingPair()
                pair.passKey = key
                pair.macAddress = mac
                this.add(pair)
            }
        }
    }

    fun clearAll(){
        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_BindingPairs),
                Context.MODE_PRIVATE
            )
            with(sharedPref.edit()){
                clear()
                commit()
            }
    }

    fun lookUpForPassKeyWithMacAddress(mac: String) : String {
        val result =
            ERROR_NOTFOUND

        this.bindingPairs.forEach{
            if(it.macAddress == mac){
                return it.passKey
            }
        }
        return result
    }

    private fun save() {

        val dataKeyStarter = appContext.getString(R.string.DataKey_BindingPairStartKey)

        val sharedPref =
            appContext.getSharedPreferences(
                appContext.getString(R.string.FileKey_BindingPairs),
                Context.MODE_PRIVATE
            )

        this.bindingPairs.forEachIndexed { index, bindingPair ->

            val dataToSave = "${bindingPair.macAddress}#${bindingPair.passKey}"
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
                this.add(data ?: "")
            }
            counter++
        }
    }
}