package com.laroomysoft.laroomy

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BLEDiscoveryManager(private val appContext: Context, private val discoveryCallback: BLEDiscoveryCallback) {

    private val bleAdapter : BluetoothAdapter
    get() {
        val bluetoothManager =
            appContext.getSystemService(Context. BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    private val bluetoothLEScanner = this.bleAdapter.bluetoothLeScanner
    private var scanning = false

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // report event
            discoveryCallback.deviceFound(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // report event
            discoveryCallback.scanFail(errorCode)
        }
    }

    fun startScan(){
        if(!this.scanning) {
            if (ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // report error
                discoveryCallback.permissionError()
            } else {
                // schedule the stop of scanning
                Executors.newSingleThreadScheduledExecutor().schedule({
                    // stop scan
                    bluetoothLEScanner.stopScan(leScanCallback)
                    // report event
                    discoveryCallback.scanStopped()
                }, 10, TimeUnit.SECONDS)
                // start scan and set param
                this.bluetoothLEScanner.startScan(this.leScanCallback)
                this.scanning = true
                discoveryCallback.scanStarted()
            }
        }
    }

    fun stopScan(){
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // report error
            discoveryCallback.permissionError()
        } else {
            // stop scan
            bluetoothLEScanner.stopScan(leScanCallback)
            // report event
            discoveryCallback.scanStopped()
        }
    }
}

interface BLEDiscoveryCallback : Serializable {
    fun scanStarted(){}
    fun scanStopped(){}
    fun deviceFound(device: BluetoothDevice){}
    fun permissionError(){}
    fun scanFail(errorCode: Int){}
}