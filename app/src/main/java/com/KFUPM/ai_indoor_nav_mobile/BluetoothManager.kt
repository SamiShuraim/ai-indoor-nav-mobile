package com.KFUPM.ai_indoor_nav_mobile

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDeviceInfo(
    val name: String?,
    val address: String,
    val rssi: Int,
    val distance: String
)

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceInfo>> = _scannedDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private var scanJob: Job? = null
    private val scanCallback = object : BluetoothAdapter.LeScanCallback {
        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            device?.let {
                val deviceInfo = BluetoothDeviceInfo(
                    name = it.name ?: "Unknown Device",
                    address = it.address,
                    rssi = rssi,
                    distance = calculateDistance(rssi)
                )
                
                updateDeviceList(deviceInfo)
            }
        }
    }
    
    private fun updateDeviceList(newDevice: BluetoothDeviceInfo) {
        val currentList = _scannedDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == newDevice.address }
        
        if (existingIndex >= 0) {
            currentList[existingIndex] = newDevice
        } else {
            currentList.add(newDevice)
        }
        
        _scannedDevices.value = currentList.sortedByDescending { it.rssi }
    }
    
    private fun calculateDistance(rssi: Int): String {
        // Simple distance calculation based on RSSI
        // This is a rough approximation and may not be accurate in all environments
        val distance = when {
            rssi > -30 -> "Very Close"
            rssi > -50 -> "Close"
            rssi > -70 -> "Medium"
            rssi > -90 -> "Far"
            else -> "Very Far"
        }
        return distance
    }
    
    fun startScanning() {
        if (!hasBluetoothPermissions()) {
            Log.e("BluetoothManager", "Bluetooth permissions not granted")
            return
        }
        
        if (bluetoothAdapter == null) {
            Log.e("BluetoothManager", "Bluetooth not supported")
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Log.e("BluetoothManager", "Bluetooth is not enabled")
            return
        }
        
        if (_isScanning.value) {
            Log.d("BluetoothManager", "Already scanning")
            return
        }
        
        _isScanning.value = true
        _scannedDevices.value = emptyList()
        
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothAdapter!!.startLeScan(scanCallback)
                delay(10000) // Scan for 10 seconds
                stopScanning()
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Error during scanning: ${e.message}")
                _isScanning.value = false
            }
        }
    }
    
    fun stopScanning() {
        scanJob?.cancel()
        bluetoothAdapter?.stopLeScan(scanCallback)
        _isScanning.value = false
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun clearDevices() {
        _scannedDevices.value = emptyList()
    }
}