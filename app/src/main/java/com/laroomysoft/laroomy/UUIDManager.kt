package com.laroomysoft.laroomy

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

const val ADD_SUCCESS = 0
const val CHANGE_SUCCESS = 0
const val PROFILE_NAME_ALREADY_EXIST = 1
const val UUID_FORMAT_INVALID = 2
const val INVALID_INDEX = 3
const val UUID_SERVICE_INVALID = 4
const val UUID_RX_CHAR_INVALID = 5
const val UUID_TX_CHAR_INVALID = 6

const val FIRST_USERPROFILE_INDEX = 3

const val UUID_File_UserProfiles = "uuidUserProfiles"

class UUIDProfile{
    fun clear() {
        this.profileName = ""
        this.useSingleCharacteristic = false
        this.serviceUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
        this.rxCharacteristicUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
        this.txCharacteristicUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
        this.isLocked = false
    }
    
    fun fromOther(other: UUIDProfile){
        this.profileName = other.profileName
        this.useSingleCharacteristic = other.useSingleCharacteristic
        this.serviceUUID = other.serviceUUID
        this.rxCharacteristicUUID = other.rxCharacteristicUUID
        this.txCharacteristicUUID = other.txCharacteristicUUID
        this.isLocked = other.isLocked
    }
    
    var profileName = ""
    var useSingleCharacteristic = false
    var isLocked = false
    var serviceUUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
    var rxCharacteristicUUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
    var txCharacteristicUUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")
}

class UUIDManager(private var appContext: Context) {

    private val rn4870TransparentUartServiceUUID: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
    private val rxRN4870CharacteristicUUID: UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")
    private val txRN4870CharacteristicUUID: UUID = UUID.fromString("49535343-8841-43f4-a8d4-EcbE34729bb3")

    private val hmModulesServiceUUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val hmModulesCharacteristicUUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    private val commonAppAccessServiceUUID: UUID = UUID.fromString("b47f725f-5fca-45b9-998f-f828388d044f")
    private val rxCommonAppAccessCharacteristicUUID: UUID = UUID.fromString("124c0402-da98-4d2b-8492-e712f8036997")
    private val txCommonAppAccessCharacteristicUUID: UUID = UUID.fromString("124c0402-da99-4d2b-8492-e712f8036997")

    //val rn4870_service_one = "00001800-0000-1000-8000-00805f9b34fb"
    //val rn4870_service_two = "00001801-0000-1000-8000-00805f9b34fb"
    //val rn4870_service_three = "0000180a-0000-1000-8000-00805f9b34fb"
    //private val rn4870characteristicUUID2: UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")
    //private val rn4870characteristicUUID3: UUID = UUID.fromString("49535343-4c8a-39b3-2f49-511cff073b7e")
    //val clientCharacteristicConfig = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    //val serviceUUIDDefaultList = arrayListOf(rn4870TransparentUartServiceUUID, hmModulesServiceUUID)
    //val characteristicUUIDDefaultList = arrayListOf(rn4870CharacteristicUUID1, hmModulesCharacteristicUUID)

    val uUIDProfileList = ArrayList<UUIDProfile>().apply {

        // add the common profiles
        val hmXXProfile = UUIDProfile()
        hmXXProfile.profileName = "HMxx - Modules"
        hmXXProfile.serviceUUID = hmModulesServiceUUID
        hmXXProfile.useSingleCharacteristic = true
        hmXXProfile.rxCharacteristicUUID = hmModulesCharacteristicUUID
        hmXXProfile.isLocked = true
        
        val rn48xxProfile = UUIDProfile()
        rn48xxProfile.profileName = "RN48xx Modules (Microchip)"
        rn48xxProfile.serviceUUID = rn4870TransparentUartServiceUUID
        rn48xxProfile.rxCharacteristicUUID = rxRN4870CharacteristicUUID
        rn48xxProfile.txCharacteristicUUID = txRN4870CharacteristicUUID
        rn48xxProfile.isLocked = true
        
        val commonAccessProfile = UUIDProfile()
        commonAccessProfile.profileName = "Common Access Profile"
        commonAccessProfile.serviceUUID = commonAppAccessServiceUUID
        commonAccessProfile.rxCharacteristicUUID = rxCommonAppAccessCharacteristicUUID
        commonAccessProfile.txCharacteristicUUID = txCommonAppAccessCharacteristicUUID
        commonAccessProfile.isLocked = true

        this.add(commonAccessProfile)
        this.add(rn48xxProfile)
        this.add(hmXXProfile)

        // add the saved user profiles
        val userProfiles = loadUserProfiles()
        if(userProfiles.isNotEmpty()){
            for (userProfile in userProfiles) {
                this.add(userProfile)
            }
        }
    }

    fun profileIndexFromServiceUUID(serviceUUID: UUID) : Int {
        this.uUIDProfileList.forEachIndexed { index, uuidProfile ->
            if(serviceUUID == uuidProfile.serviceUUID){
                return index
            }
        }
        return -1
    }

    fun clearAllUserProfiles(){

        if(this.uUIDProfileList.size > FIRST_USERPROFILE_INDEX)
        {
            // clear the list
            this.uUIDProfileList.clear()

            // re-add the common profiles
            this.uUIDProfileList.apply {
                
                val hmXXProfile = UUIDProfile()
                hmXXProfile.profileName = "HMxx - Modules"
                hmXXProfile.serviceUUID = hmModulesServiceUUID
                hmXXProfile.useSingleCharacteristic = true
                hmXXProfile.rxCharacteristicUUID = hmModulesCharacteristicUUID
                
                val rn48xxProfile = UUIDProfile()
                rn48xxProfile.profileName = "RN48xx Modules (Microchip)"
                rn48xxProfile.serviceUUID = rn4870TransparentUartServiceUUID
                rn48xxProfile.rxCharacteristicUUID = rxRN4870CharacteristicUUID
                rn48xxProfile.txCharacteristicUUID = txRN4870CharacteristicUUID
                
                val commonAccessProfile = UUIDProfile()
                commonAccessProfile.profileName = "Common Access Profile"
                commonAccessProfile.serviceUUID = commonAppAccessServiceUUID
                commonAccessProfile.rxCharacteristicUUID = rxCommonAppAccessCharacteristicUUID
                commonAccessProfile.txCharacteristicUUID = txCommonAppAccessCharacteristicUUID

                this.add(commonAccessProfile)
                this.add(rn48xxProfile)
                this.add(hmXXProfile)
            }
            // delete the file
            File(appContext.filesDir, UUID_File_UserProfiles).apply {
                delete()
            }
        }
    }

    fun changeExistingProfile(index: Int, profile: UUIDProfile) : Int {

        return if(index < FIRST_USERPROFILE_INDEX && index >= this.uUIDProfileList.size){
            INVALID_INDEX
        } else {
            return when {
                !checkUUIDFormat(profile.serviceUUID.toString()) -> UUID_SERVICE_INVALID
                !checkUUIDFormat(profile.rxCharacteristicUUID.toString()) -> UUID_RX_CHAR_INVALID
                !checkUUIDFormat(profile.txCharacteristicUUID.toString()) -> UUID_TX_CHAR_INVALID
                else -> {
                    this.uUIDProfileList[index].profileName = profile.profileName
                    this.uUIDProfileList[index].useSingleCharacteristic =
                        profile.useSingleCharacteristic
                    this.uUIDProfileList[index].serviceUUID = profile.serviceUUID
                    this.uUIDProfileList[index].rxCharacteristicUUID = profile.rxCharacteristicUUID
                    this.uUIDProfileList[index].txCharacteristicUUID = profile.txCharacteristicUUID
            
                    this.saveUserProfiles()
                    CHANGE_SUCCESS
                }
            }
        }
    }

    fun deleteExistingProfile(index: Int){

        if((index > -1) && (index < uUIDProfileList.size)){

            if(index >= FIRST_USERPROFILE_INDEX){

                this.uUIDProfileList.removeAt(index)

                if(this.uUIDProfileList.size <= FIRST_USERPROFILE_INDEX)
                {
                    // delete the file
                    File(appContext.filesDir, UUID_File_UserProfiles).apply {
                        delete()
                    }
                } else {
                    this.saveUserProfiles()
                }
            }
        }
    }

    fun addNewProfile(profile: UUIDProfile) : Int {
        try {
            // first check if the name already exists
            for (uuidProfile in this.uUIDProfileList) {
                if (uuidProfile.profileName == profile.profileName) {
                    return PROFILE_NAME_ALREADY_EXIST
                }
            }
            // then check the uuid-format
            return when {
                !checkUUIDFormat(profile.serviceUUID.toString()) -> UUID_SERVICE_INVALID
                !checkUUIDFormat(profile.rxCharacteristicUUID.toString()) -> UUID_RX_CHAR_INVALID
                !checkUUIDFormat(profile.txCharacteristicUUID.toString()) -> UUID_TX_CHAR_INVALID
                else -> {
                    // everything is ok, so add the profile
                    this.uUIDProfileList.add(profile)
                    // write the new profile-list to file
                    this.saveUserProfiles()
        
                    ADD_SUCCESS
                }
            }
        } catch(e: Exception){
            return UUID_FORMAT_INVALID
        }
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

    private fun loadUserProfiles() : ArrayList<UUIDProfile> {
        val loadBuffer = this.bufferFromFile()

        if(loadBuffer.isNotEmpty()){
            return this.bufferToProfiles(loadBuffer)
        }
        return ArrayList()
    }

    private fun checkUUIDFormat(uuid: String) : Boolean {

        var isValid: Boolean = uuid.length == 36

        //UUID Format = "00001800-0000-1000-8000-00805f9b34fb"

        if(isValid) {

            uuid.forEachIndexed { index, c ->
                when(index){
                    8 -> {
                        if((c != '-')&&(c.code != 45)&&(c.code != 8211)){
                            isValid = false
                            return@forEachIndexed
                        }
                    }
                    13 -> {
                        if((c != '-')&&(c.code != 45)&&(c.code != 8211)){
                            isValid = false
                            return@forEachIndexed
                        }
                    }
                    18 -> {
                        if((c != '-')&&(c.code != 45)&&(c.code != 8211)){
                            isValid = false
                            return@forEachIndexed
                        }
                    }
                    23 -> {
                        if((c != '-')&&(c.code != 45)&&(c.code != 8211)){
                            isValid = false
                            return@forEachIndexed
                        }
                    }
                    else -> {
                        if(!isHexCharacter(c)){
                            isValid = false
                            return@forEachIndexed
                        }
                    }
                }
            }
        }
        return isValid
    }

    private fun profilesToBuffer(profiles: ArrayList<UUIDProfile>) : String {

        var profileBuffer = ""

        for (profile in profiles) {
            profileBuffer += "N[${profile.profileName}\nS[${profile.serviceUUID}\nC[${profile.rxCharacteristicUUID}\nD[${profile.txCharacteristicUUID}\nU[${profile.useSingleCharacteristic}\n"
        }
        return profileBuffer
    }

    private fun bufferToProfiles(lineList: ArrayList<String>) : ArrayList<UUIDProfile> {

        val profileList = ArrayList<UUIDProfile>()
        var nameRead = false
        var serviceUUIDRead = false
        var useSingleCharValueRead = false
        var characteristicUUIDRead = false
    
        val profile = UUIDProfile()

        try {
            lineList.forEach {

                var line = ""

                when (it[0]) {
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
                        }
                        profile.serviceUUID = UUID.fromString(line)
                        serviceUUIDRead = true
                    }
                    'C' -> {
                        it.forEachIndexed { index, c ->
                            if (index > 1) {
                                line += c
                            }
                        }
                        profile.rxCharacteristicUUID = UUID.fromString(line)
                        characteristicUUIDRead = true
                    }
                    'D' -> {
                        it.forEachIndexed { index, c ->
                            if (index > 1) {
                                line += c
                            }
                        }
                        profile.txCharacteristicUUID = UUID.fromString(line)
                        characteristicUUIDRead = true
                    }
                    'U' -> {
                        it.forEachIndexed { index, c ->
                            if (index > 1) {
                                line += c
                            }
                        }
                        profile.useSingleCharacteristic = when(line){
                            "false" -> false
                            else -> true
                        }
                        useSingleCharValueRead = true
                    }
                    else -> {
                        // must be the end !
                    }
                }
                if (nameRead && serviceUUIDRead && characteristicUUIDRead && useSingleCharValueRead) {
                    val pToAdd = UUIDProfile()
                    pToAdd.fromOther(profile)
                    profileList.add(pToAdd)
                    nameRead = false
                    serviceUUIDRead = false
                    useSingleCharValueRead = false
                    characteristicUUIDRead = false
                }
            }
        } catch (e: Exception){
            Log.e("UUIDManage", "Error while converting buffer to profiles")
        }
        return profileList
    }

    private fun bufferToFile(buffer: String){
        appContext.openFileOutput(UUID_File_UserProfiles, Context.MODE_PRIVATE).use {
            it.write(buffer.toByteArray())
        }
    }

    private fun bufferFromFile() : ArrayList<String> {

        val lineList = ArrayList<String>()

        try {
            appContext.openFileInput(UUID_File_UserProfiles).bufferedReader().use { bReader ->
                bReader.forEachLine {
                    lineList.add(it)
                }
            }
        } catch (fne: FileNotFoundException) {
            // that's ok!
            if(verboseLog) {
                Log.d("UUIDManager", "bufferFromFile: $fne")
            }
        }
        return lineList
    }
}