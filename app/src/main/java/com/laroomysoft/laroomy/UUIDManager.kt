package com.laroomysoft.laroomy

import android.content.Context
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

const val ADD_SUCCESS = 0
const val PROFILE_NAME_ALREADY_EXIST = 1
const val UUID_FORMAT_INVALID = 2

const val FIRST_USERPROFILE_INDEX = 2

const val UUID_File_UserProfiles = "uuidUserProfiles"

class UUIDProfile{
    var profileName = ""
    var serviceUUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
    var characteristicUUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
}

class UUIDManager(val appContext: Context) {

    val uUIDProfileList = ArrayList<UUIDProfile>().apply {

        // add the common profiles
        val hmXXProfile = UUIDProfile()
        hmXXProfile.profileName = "Huamao HMxx - Modules"
        hmXXProfile.serviceUUID = hmxxserviceUUID
        hmXXProfile.characteristicUUID = hmxxcharacteristicUUID
        val rn48xxProfile = UUIDProfile()
        rn48xxProfile.profileName = "Microchip RN48xx Modules"
        rn48xxProfile.serviceUUID = rn4870transUartserviceUUID
        rn48xxProfile.characteristicUUID = rn4870characteristicUUID1

        this.add(rn48xxProfile)
        this.add(hmXXProfile)

        // add the saved user profiles
        loadUserProfiles()
    }

    fun changeExistingProfile(){


        this.saveUserProfiles()
    }

    fun deleteExistingProfile(){

        this.saveUserProfiles()
    }

    fun addNewProfile(profileName: String, serviceUUID: String, characteristicUUID: String) : Int {
        // first check if the name already exists
        for (uuidProfile in this.uUIDProfileList) {
            if(uuidProfile.profileName == profileName){
                return PROFILE_NAME_ALREADY_EXIST
            }
        }
        // then check the uuid-format
        if(!checkUUIDFormat(serviceUUID) || !checkUUIDFormat(characteristicUUID)){
            return UUID_FORMAT_INVALID
        }
        // everything is ok, so add the profile
        val p = UUIDProfile()
        p.profileName = profileName
        p.serviceUUID = UUID.fromString(serviceUUID)
        p.characteristicUUID = UUID.fromString(characteristicUUID)
        this.uUIDProfileList.add(p)
        // write the new profile-list to file
        this.saveUserProfiles()

        return ADD_SUCCESS
    }

    private fun saveUserProfiles(){
        // only save the user-defined profiles
        if(this.uUIDProfileList.size > FIRST_USERPROFILE_INDEX){

            val pList = ArrayList<UUIDProfile>()

            this.uUIDProfileList.forEachIndexed { index, uuidProfile ->
                if(index >= FIRST_USERPROFILE_INDEX){
                    pList.add(uuidProfile)
                }
            }

            if(pList.isNotEmpty()){
                val bufferToSave = this.profilesToBuffer(pList)

                if(bufferToSave.isNotEmpty()){
                    bufferToFile(bufferToSave)
                }
            }
        }
    }

    private fun loadUserProfiles(){
        val loadBuffer = this.bufferFromFile()

        if(loadBuffer.isNotEmpty()){
            val profiles = this.bufferToProfiles(loadBuffer)

            if(profiles.isNotEmpty()){
                profiles.forEach {
                    this.uUIDProfileList.add(it)
                }
            }
        }
    }

    private fun checkUUIDFormat(uuid: String) : Boolean {
        // TODO!
        return true
    }

    private fun profilesToBuffer(profiles: ArrayList<UUIDProfile>) : String {

        var profileBuffer = ""

        for (profile in profiles) {
            profileBuffer += "N[${profile.profileName}\nS[${profile.serviceUUID}\nC[${profile.characteristicUUID}\n"
        }
        return profileBuffer
    }

    private fun bufferToProfiles(lineList: ArrayList<String>) : ArrayList<UUIDProfile> {

        val profileList = ArrayList<UUIDProfile>()
        var nameRead = false
        var serviceUUIDRead = false
        var characteristicUUIDRead = false

        val profile = UUIDProfile()

        lineList.forEach {

            var line = ""

            when(it[0]) {
                'N' -> {
                    it.forEachIndexed { index, c ->
                        if (index > 1) {
                            line += c
                        }
                    }
                    profile.profileName = line
                    nameRead = true
                }
                'S' -> {
                    it.forEachIndexed { index, c ->
                        if (index > 1) {
                            line += c
                        }
                        profile.serviceUUID = UUID.fromString(line)
                        serviceUUIDRead = true
                    }
                }
                'C' -> {
                    it.forEachIndexed { index, c ->
                        if (index > 1) {
                            line += c
                        }
                        profile.characteristicUUID = UUID.fromString(line)
                        characteristicUUIDRead = true
                    }
                }
                else -> {
                    // must be the end !
                }
            }
            if(nameRead && serviceUUIDRead && characteristicUUIDRead){
                profileList.add(profile)
                nameRead = false
                serviceUUIDRead = false
                characteristicUUIDRead = false
            }
        }
        return profileList
    }

    private fun bufferToFile(buffer: String){
        appContext.openFileOutput(UUID_File_UserProfiles, Context.MODE_PRIVATE).use {
            it.write(buffer.toByteArray())
        }
    }

//    private fun bufferFromFile() : String {
//        var buffer: String
//
//        appContext.openFileInput(UUID_File_UserProfiles).bufferedReader().use {
//            buffer = it.readText()
//        }
//        return buffer
//    }

    private fun bufferFromFile() : ArrayList<String> {

        val lineList = ArrayList<String>()

        appContext.openFileInput(UUID_File_UserProfiles).bufferedReader().use{ bReader ->
            bReader.forEachLine {
                lineList.add(it)
            }
        }

        return lineList
    }


    //val rn4870_service_one = "00001800-0000-1000-8000-00805f9b34fb"
    //val rn4870_service_two = "00001801-0000-1000-8000-00805f9b34fb"
    //val rn4870_service_three = "0000180a-0000-1000-8000-00805f9b34fb"

    private val rn4870transUartserviceUUID: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
    private val hmxxserviceUUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")


    private val hmxxcharacteristicUUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    private val rn4870characteristicUUID1: UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")

    //private val rn4870characteristicUUID2: UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")

    private val rn4870characteristicUUID3: UUID = UUID.fromString("49535343-4c8a-39b3-2f49-511cff073b7e")

    val clientCharacteristicConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val serviceUUIDDefaultList = arrayListOf(rn4870transUartserviceUUID, hmxxserviceUUID)

    val characteristicUUIDdefaultList = arrayListOf(rn4870characteristicUUID1, hmxxcharacteristicUUID)

}