package com.laroomysoft.laroomy

import java.util.*

class UUIDManager {


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