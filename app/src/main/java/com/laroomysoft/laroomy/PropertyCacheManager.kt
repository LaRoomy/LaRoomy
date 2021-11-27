package com.laroomysoft.laroomy

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

        return "TODO!"
    }
}

class PropertyCacheManager {

    fun savePCacheData(devicePropertyCacheData: DevicePropertyCacheData){

    }



//    fun lookupForPCacheDataWithMacAddress(macAddress: String) : DevicePropertyCacheData{
//
//    }



}