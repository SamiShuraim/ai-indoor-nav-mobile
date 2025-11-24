package com.KFUPM.ai_indoor_nav_mobile.localization

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps beacon names to their MAC addresses by scanning BLE devices
 * Now with local caching support to avoid expensive re-scanning
 */
class BeaconNameMapper(private val context: Context) {
    private val TAG = "BeaconNameMapper"
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val cache = BeaconMappingCache(context)
    
    /**
     * Scan for beacons and create a mapping of beacon names to MAC addresses
     * Uses cached mappings first, then scans only for unmapped beacons
     * 
     * @param beaconNames List of beacon names to look for (e.g., ["Beacon A", "Beacon B"])
     * @param scanDurationMs How long to scan (default 5 seconds)
     * @param saveToCache Whether to save new mappings to cache (default true)
     * @return Map of beacon name to MAC address
     */
    suspend fun mapBeaconNamesToMacAddresses(
        beaconNames: List<String>,
        scanDurationMs: Long = 5000,
        saveToCache: Boolean = true
    ): Map<String, String> {
        // First, load cached mappings
        val cachedMappings = cache.getMappings()
        val resultMap = ConcurrentHashMap<String, String>()
        
        // Add all cached mappings for requested beacons
        beaconNames.forEach { name ->
            cachedMappings[name]?.let { mac ->
                resultMap[name] = mac
                Log.d(TAG, "Using cached mapping: '$name' -> $mac")
            }
        }
        
        // Determine which beacons still need mapping
        val unmappedBeacons = beaconNames.filter { !resultMap.containsKey(it) }
        
        if (unmappedBeacons.isEmpty()) {
            Log.d(TAG, "All ${beaconNames.size} beacons found in cache")
            return resultMap.toMap()
        }
        
        Log.d(TAG, "Found ${resultMap.size}/${beaconNames.size} beacons in cache, scanning for ${unmappedBeacons.size} unmapped beacons")
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return resultMap.toMap()
        }
        
        // Scan for unmapped beacons
        val newMappings = ConcurrentHashMap<String, String>()
        
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val device = result.device
                    val deviceName = device.name
                    val macAddress = device.address
                    
                    // Check if this device name matches any of our unmapped beacon names
                    if (deviceName != null && deviceName in unmappedBeacons && !newMappings.containsKey(deviceName)) {
                        val mac = macAddress.uppercase()
                        newMappings[deviceName] = mac
                        resultMap[deviceName] = mac
                        
                        // Save to cache immediately
                        if (saveToCache) {
                            cache.saveMapping(deviceName, mac)
                        }
                        
                        Log.d(TAG, "Mapped beacon: '$deviceName' -> $mac (RSSI: ${result.rssi})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing scan result", e)
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Beacon scan failed: $errorCode")
            }
        }
        
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            Log.d(TAG, "Starting beacon name mapping scan for: $unmappedBeacons")
            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            
            // Wait for scan to complete
            withTimeout(scanDurationMs) {
                while (newMappings.size < unmappedBeacons.size) {
                    delay(100)
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Beacon mapping scan completed with timeout or error", e)
        } finally {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan", e)
            }
        }
        
        Log.d(TAG, "Beacon mapping complete: ${resultMap.size}/${beaconNames.size} beacons mapped (${newMappings.size} newly found)")
        if (newMappings.isNotEmpty()) {
            newMappings.forEach { (name, mac) ->
                Log.d(TAG, "  NEW: $name -> $mac")
            }
        }
        
        return resultMap.toMap()
    }
    
    /**
     * Get cached mappings
     */
    fun getCachedMappings(): Map<String, String> {
        return cache.getMappings()
    }
    
    /**
     * Check if all beacons are mapped
     */
    fun areAllMapped(beaconNames: List<String>): Boolean {
        return cache.areAllMapped(beaconNames)
    }
    
    /**
     * Get unmapped beacon names
     */
    fun getUnmappedBeacons(beaconNames: List<String>): List<String> {
        return cache.getUnmappedBeacons(beaconNames)
    }
    
    /**
     * Clear all cached mappings
     */
    fun clearCache() {
        cache.clear()
    }
}
