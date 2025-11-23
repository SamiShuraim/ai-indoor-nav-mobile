package com.KFUPM.ai_indoor_nav_mobile.localization

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background service that continuously scans for unmapped beacons
 * Runs until all beacons have been mapped to MAC addresses
 * Uses local cache to persist mappings across app restarts
 */
class BackgroundBeaconMapper(private val context: Context) {
    private val TAG = "BackgroundBeaconMapper"
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val cache = BeaconMappingCache(context)
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private var scanJob: Job? = null
    private var periodicScanJob: Job? = null
    
    // Target beacon names to map
    private var targetBeaconNames: Set<String> = emptySet()
    
    // Callback for when mapping is complete
    private var onMappingComplete: (() -> Unit)? = null
    
    // Currently discovered beacons (in this session)
    private val discoveredInSession = ConcurrentHashMap<String, String>()
    
    // Scan interval (once every 2 seconds)
    private val scanIntervalMs = 2000L
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device
                val deviceName = device.name
                val macAddress = device.address
                
                // Check if this device name matches any of our target beacon names
                if (deviceName != null && deviceName in targetBeaconNames) {
                    // Check if not already cached
                    if (!cache.isMapped(deviceName)) {
                        val mac = macAddress.uppercase()
                        
                        // Save to cache
                        cache.saveMapping(deviceName, mac)
                        discoveredInSession[deviceName] = mac
                        
                        Log.d(TAG, "Background mapped beacon: '$deviceName' -> $mac (RSSI: ${result.rssi})")
                        
                        // Check if all beacons are now mapped
                        checkIfComplete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing background scan result", e)
            }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Background beacon scan failed: $errorCode")
        }
    }
    
    /**
     * Start background mapping for specified beacons
     * 
     * @param beaconNames List of beacon names to map
     * @param onComplete Callback when all beacons are mapped
     */
    fun start(beaconNames: List<String>, onComplete: (() -> Unit)? = null) {
        if (isRunning.get()) {
            Log.w(TAG, "Background mapper already running")
            return
        }
        
        if (beaconNames.isEmpty()) {
            Log.w(TAG, "No beacon names provided")
            return
        }
        
        targetBeaconNames = beaconNames.toSet()
        onMappingComplete = onComplete
        discoveredInSession.clear()
        
        // Check if already complete
        if (cache.areAllMapped(beaconNames)) {
            Log.d(TAG, "All ${beaconNames.size} beacons already mapped, no background mapping needed")
            onComplete?.invoke()
            return
        }
        
        val unmappedBeacons = cache.getUnmappedBeacons(beaconNames)
        Log.d(TAG, "Starting background mapper for ${unmappedBeacons.size} unmapped beacons: $unmappedBeacons")
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return
        }
        
        isRunning.set(true)
        
        // Start periodic scanning job (once per second)
        startPeriodicScanning()
    }
    
    /**
     * Stop background mapping
     */
    fun stop() {
        if (!isRunning.get()) return
        
        Log.d(TAG, "Stopping background mapper")
        isRunning.set(false)
        scanJob?.cancel()
        periodicScanJob?.cancel()
        stopScanning()
        
        logProgress()
    }
    
    /**
     * Check if mapping is complete
     */
    private fun checkIfComplete() {
        if (cache.areAllMapped(targetBeaconNames.toList())) {
            Log.d(TAG, "All beacons mapped! Stopping background mapper")
            stop()
            onMappingComplete?.invoke()
        }
    }
    
    /**
     * Start periodic BLE scanning (once per second)
     */
    private fun startPeriodicScanning() {
        periodicScanJob = scope.launch {
            var scanCount = 0
            while (isActive && isRunning.get()) {
                try {
                    // Start a 1-second scan
                    performSingleScan()
                    scanCount++
                    
                    // Log progress every 10 scans (every 10 seconds)
                    if (scanCount % 10 == 0) {
                        logProgress()
                    }
                    
                    // Wait 1 second before next scan
                    delay(scanIntervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic scanning", e)
                    delay(scanIntervalMs)
                }
            }
        }
        Log.d(TAG, "Periodic background scanning started (once every 2 seconds)")
    }
    
    /**
     * Perform a single scan burst
     */
    private suspend fun performSingleScan() {
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Higher power for better detection
                .build()
            
            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            
            // Scan for 500ms, then stop
            delay(500)
            
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during scan", e)
        }
    }
    
    /**
     * Stop BLE scanning
     */
    private fun stopScanning() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "Background BLE scanning stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping background scan", e)
        }
    }
    
    /**
     * Log current progress
     */
    private fun logProgress() {
        val allMappings = cache.getMappings()
        val mappedCount = targetBeaconNames.count { allMappings.containsKey(it) }
        val totalCount = targetBeaconNames.size
        
        Log.d(TAG, "Background mapping progress: $mappedCount/$totalCount beacons mapped")
        
        if (discoveredInSession.isNotEmpty()) {
            Log.d(TAG, "  Discovered in this session: ${discoveredInSession.keys}")
        }
        
        if (mappedCount < totalCount) {
            val unmapped = cache.getUnmappedBeacons(targetBeaconNames.toList())
            Log.d(TAG, "  Still unmapped: $unmapped")
        }
    }
    
    /**
     * Get current mapping status
     */
    fun getStatus(): MappingStatus {
        val allMappings = cache.getMappings()
        val mappedCount = targetBeaconNames.count { allMappings.containsKey(it) }
        val unmappedBeacons = cache.getUnmappedBeacons(targetBeaconNames.toList())
        
        return MappingStatus(
            isRunning = isRunning.get(),
            totalBeacons = targetBeaconNames.size,
            mappedBeacons = mappedCount,
            unmappedBeacons = unmappedBeacons,
            discoveredInSession = discoveredInSession.keys.toList(),
            isComplete = unmappedBeacons.isEmpty()
        )
    }
    
    /**
     * Check if all beacons are mapped
     */
    fun isComplete(): Boolean {
        return targetBeaconNames.isNotEmpty() && cache.areAllMapped(targetBeaconNames.toList())
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        scope.cancel()
    }
    
    /**
     * Mapping status data class
     */
    data class MappingStatus(
        val isRunning: Boolean,
        val totalBeacons: Int,
        val mappedBeacons: Int,
        val unmappedBeacons: List<String>,
        val discoveredInSession: List<String>,
        val isComplete: Boolean
    ) {
        val progress: Float
            get() = if (totalBeacons > 0) mappedBeacons.toFloat() / totalBeacons else 0f
    }
}
