package com.KFUPM.ai_indoor_nav_mobile.localization

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.KFUPM.ai_indoor_nav_mobile.localization.models.BleScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * BLE Beacon Scanner with RSSI smoothing
 */
class BeaconScanner(
    private val context: Context,
    private val windowSize: Int = 5,
    private val emaGamma: Double = 0.5
) {
    private val TAG = "BeaconScanner"
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Raw RSSI windows per beacon
    private val rssiWindows = ConcurrentHashMap<String, MutableList<Double>>()
    
    // EMA smoothed RSSI per beacon
    private val emaRssi = ConcurrentHashMap<String, Double>()
    
    // Smoothed RSSI map exposed to consumers
    private val _smoothedRssiMap = MutableStateFlow<Map<String, Double>>(emptyMap())
    val smoothedRssiMap: StateFlow<Map<String, Double>> = _smoothedRssiMap
    
    // Last update timestamp
    private var lastUpdateMs = 0L
    
    private var isScanning = false
    private var updateJob: Job? = null
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { processScanResult(it) }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processScanResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }
    
    /**
     * Start scanning for beacons
     */
    fun startScanning() {
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }
        
        if (!checkPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE scanning started")
            
            // Start periodic update job (1 Hz default)
            startUpdateJob()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting scan", e)
        }
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning() {
        if (!isScanning) return
        
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            updateJob?.cancel()
            Log.d(TAG, "BLE scanning stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping scan", e)
        }
    }
    
    /**
     * Process individual scan result
     */
    private fun processScanResult(result: ScanResult) {
        val device = result.device ?: return
        val rssi = result.rssi.toDouble()
        
        // Use MAC address as beacon ID (or parse from scan record if available)
        val beaconId = device.address ?: return
        
        // Add to window
        val window = rssiWindows.getOrPut(beaconId) { mutableListOf() }
        synchronized(window) {
            window.add(rssi)
            if (window.size > windowSize) {
                window.removeAt(0)
            }
        }
    }
    
    /**
     * Start periodic update job
     */
    private fun startUpdateJob() {
        updateJob = scope.launch {
            while (isActive) {
                updateSmoothedRssi()
                delay(1000) // 1 Hz
            }
        }
    }
    
    /**
     * Update smoothed RSSI values
     */
    private fun updateSmoothedRssi() {
        val currentTimeMs = System.currentTimeMillis()
        val updatedMap = mutableMapOf<String, Double>()
        
        // Clean up stale beacons (not seen in 5 seconds)
        val staleThresholdMs = 5000
        rssiWindows.keys.forEach { beaconId ->
            val window = rssiWindows[beaconId] ?: return@forEach
            
            synchronized(window) {
                if (window.isEmpty()) {
                    rssiWindows.remove(beaconId)
                    emaRssi.remove(beaconId)
                    return@forEach
                }
                
                // Compute median
                val sortedWindow = window.sorted()
                val median = if (sortedWindow.size % 2 == 0) {
                    (sortedWindow[sortedWindow.size / 2 - 1] + sortedWindow[sortedWindow.size / 2]) / 2.0
                } else {
                    sortedWindow[sortedWindow.size / 2]
                }
                
                // Apply EMA
                val previousEma = emaRssi[beaconId]
                val newEma = if (previousEma == null) {
                    median
                } else {
                    emaGamma * median + (1 - emaGamma) * previousEma
                }
                
                emaRssi[beaconId] = newEma
                updatedMap[beaconId] = newEma
            }
        }
        
        // Update state flow
        _smoothedRssiMap.value = updatedMap
        lastUpdateMs = currentTimeMs
        
        if (updatedMap.isNotEmpty()) {
            Log.d(TAG, "Updated RSSI for ${updatedMap.size} beacons")
        }
    }
    
    /**
     * Get current smoothed RSSI map
     */
    fun getCurrentRssiMap(): Map<String, Double> {
        return _smoothedRssiMap.value
    }
    
    /**
     * Clear all data
     */
    fun clear() {
        rssiWindows.clear()
        emaRssi.clear()
        _smoothedRssiMap.value = emptyMap()
    }
    
    /**
     * Check required permissions
     */
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 and below
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopScanning()
        scope.cancel()
    }
}
