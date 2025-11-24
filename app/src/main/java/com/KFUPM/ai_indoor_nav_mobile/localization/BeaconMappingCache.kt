package com.KFUPM.ai_indoor_nav_mobile.localization

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Local storage cache for beacon name to MAC address mappings
 * Persists mappings across app restarts to avoid expensive re-scanning
 */
class BeaconMappingCache(context: Context) {
    private val TAG = "BeaconMappingCache"
    
    private val prefs = context.getSharedPreferences("beacon_mappings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_MAPPINGS = "name_to_mac_mappings"
        private const val KEY_LAST_UPDATE = "last_update_timestamp"
    }
    
    /**
     * Save a single beacon name to MAC address mapping
     */
    fun saveMappings(mappings: Map<String, String>) {
        val allMappings = getMappings().toMutableMap()
        allMappings.putAll(mappings)
        
        val json = gson.toJson(allMappings)
        prefs.edit()
            .putString(KEY_MAPPINGS, json)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Saved ${mappings.size} beacon mappings (total: ${allMappings.size})")
    }
    
    /**
     * Save a single mapping
     */
    fun saveMapping(beaconName: String, macAddress: String) {
        saveMappings(mapOf(beaconName to macAddress))
    }
    
    /**
     * Get all stored beacon name to MAC address mappings
     */
    fun getMappings(): Map<String, String> {
        val json = prefs.getString(KEY_MAPPINGS, null) ?: return emptyMap()
        
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing stored mappings", e)
            emptyMap()
        }
    }
    
    /**
     * Get MAC address for a beacon name, if cached
     */
    fun getMacAddress(beaconName: String): String? {
        return getMappings()[beaconName]
    }
    
    /**
     * Check if a beacon name is mapped
     */
    fun isMapped(beaconName: String): Boolean {
        return getMappings().containsKey(beaconName)
    }
    
    /**
     * Get list of unmapped beacon names from a given list
     */
    fun getUnmappedBeacons(beaconNames: List<String>): List<String> {
        val mappings = getMappings()
        return beaconNames.filter { !mappings.containsKey(it) }
    }
    
    /**
     * Check if all beacons in a list are mapped
     */
    fun areAllMapped(beaconNames: List<String>): Boolean {
        val mappings = getMappings()
        return beaconNames.all { mappings.containsKey(it) }
    }
    
    /**
     * Get the timestamp of last update
     */
    fun getLastUpdateTimestamp(): Long {
        return prefs.getLong(KEY_LAST_UPDATE, 0L)
    }
    
    /**
     * Clear all mappings
     */
    fun clear() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all beacon mappings")
    }
    
    /**
     * Get statistics about cached mappings
     */
    fun getStats(): CacheStats {
        val mappings = getMappings()
        return CacheStats(
            totalMappings = mappings.size,
            lastUpdateTimestamp = getLastUpdateTimestamp()
        )
    }
    
    data class CacheStats(
        val totalMappings: Int,
        val lastUpdateTimestamp: Long
    )
}
