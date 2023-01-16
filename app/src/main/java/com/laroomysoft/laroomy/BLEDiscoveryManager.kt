package com.laroomysoft.laroomy

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    
    val isScanning
    get() = scanning

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // report event
            discoveryCallback.onDeviceFound(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // report event
            discoveryCallback.onScanFail(errorCode)
        }
    }

    fun startScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // report error
                discoveryCallback.onPermissionError()
            } else {
                if (!this.scanning) {
                    // schedule the stop of scanning
                    Executors.newSingleThreadScheduledExecutor().schedule({
                        // stop scan
                        bluetoothLEScanner.stopScan(leScanCallback)
                        scanning = false
                        // report event
                        discoveryCallback.onScanStopped()
                    }, 10, TimeUnit.SECONDS)
                    // start scan and set param
                    this.bluetoothLEScanner.startScan(this.leScanCallback)
                    this.scanning = true
                    discoveryCallback.onScanStarted()
                }
            }

        } else {
            if (!this.scanning) {
                // schedule the stop of scanning
                Executors.newSingleThreadScheduledExecutor().schedule({
                    // stop scan
                    bluetoothLEScanner.stopScan(leScanCallback)
                    scanning = false
                    // report event
                    discoveryCallback.onScanStopped()
                }, 10, TimeUnit.SECONDS)
                // start scan and set param
                this.bluetoothLEScanner.startScan(this.leScanCallback)
                this.scanning = true
                discoveryCallback.onScanStarted()
            }
        }
    }

    fun stopScan(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // report error
                discoveryCallback.onPermissionError()
            } else {
                if(this.scanning) {
                    // stop scan
                    bluetoothLEScanner.stopScan(leScanCallback)
                    this.scanning = false
                    // report event
                    discoveryCallback.onScanStopped()
                }
            }
        } else {
            if(this.scanning){
                // stop scan
                bluetoothLEScanner.stopScan(leScanCallback)
                this.scanning = false
                // report event
                discoveryCallback.onScanStopped()
            }
        }
    }
}

interface BLEDiscoveryCallback : Serializable {
    fun onScanStarted(){}
    fun onScanStopped(){}
    fun onDeviceFound(device: BluetoothDevice){}
    fun onPermissionError(){}
    fun onScanFail(errorCode: Int){}
}