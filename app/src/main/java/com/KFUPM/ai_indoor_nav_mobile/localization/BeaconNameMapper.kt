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
 */
class BeaconNameMapper(private val context: Context) {
    private val TAG = "BeaconNameMapper"
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    
    /**
     * Scan for beacons and create a mapping of beacon names to MAC addresses
     * 
     * @param beaconNames List of beacon names to look for (e.g., ["Beacon A", "Beacon B"])
     * @param scanDurationMs How long to scan (default 5 seconds)
     * @return Map of beacon name to MAC address
     */
    suspend fun mapBeaconNamesToMacAddresses(
        beaconNames: List<String>,
        scanDurationMs: Long = 5000
    ): Map<String, String> {
        val nameToMacMap = ConcurrentHashMap<String, String>()
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return emptyMap()
        }
        
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val device = result.device
                    val deviceName = device.name
                    val macAddress = device.address
                    
                    // Check if this device name matches any of our beacon names
                    if (deviceName != null && deviceName in beaconNames) {
                        nameToMacMap[deviceName] = macAddress.uppercase()
                        Log.d(TAG, "Mapped beacon: '$deviceName' -> $macAddress (RSSI: ${result.rssi})")
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
            
            Log.d(TAG, "Starting beacon name mapping scan for: $beaconNames")
            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            
            // Wait for scan to complete
            withTimeout(scanDurationMs) {
                while (nameToMacMap.size < beaconNames.size) {
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
        
        Log.d(TAG, "Beacon mapping complete: ${nameToMacMap.size}/${beaconNames.size} beacons found")
        nameToMacMap.forEach { (name, mac) ->
            Log.d(TAG, "  $name -> $mac")
        }
        
        return nameToMacMap.toMap()
    }
}
