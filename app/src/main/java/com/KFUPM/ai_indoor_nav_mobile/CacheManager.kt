package com.KFUPM.ai_indoor_nav_mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages caching of API data to speed up app startup
 */
class CacheManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("indoor_nav_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "CacheManager"
        private const val KEY_BUILDINGS = "buildings"
        private const val KEY_FLOORS = "floors"
        private const val KEY_BEACONS_PREFIX = "beacons_floor_"
        private const val KEY_ROUTE_NODES_PREFIX = "route_nodes_floor_"
        private const val KEY_POIS_PREFIX = "pois_floor_"
        private const val KEY_NODE_TO_FLOOR_MAP = "node_to_floor_map"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
        private const val KEY_DATA_VERSION = "data_version"
        
        // Cache validity period (24 hours)
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Check if cache is valid based on timestamp
     */
    fun isCacheValid(): Boolean {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
        val age = System.currentTimeMillis() - timestamp
        val isValid = age < CACHE_VALIDITY_MS
        Log.d(TAG, "Cache age: ${age / 1000}s, valid: $isValid")
        return isValid
    }
    
    /**
     * Save data version from backend (if backend provides versioning)
     */
    fun saveDataVersion(version: String) {
        prefs.edit().putString(KEY_DATA_VERSION, version).apply()
        Log.d(TAG, "Saved data version: $version")
    }
    
    /**
     * Get cached data version
     */
    fun getDataVersion(): String? {
        return prefs.getString(KEY_DATA_VERSION, null)
    }
    
    /**
     * Cache buildings data
     */
    fun <T> cacheBuildings(buildings: List<T>) {
        val json = gson.toJson(buildings)
        prefs.edit().putString(KEY_BUILDINGS, json).apply()
        updateCacheTimestamp()
        Log.d(TAG, "Cached ${buildings.size} buildings")
    }
    
    /**
     * Get cached buildings
     */
    inline fun <reified T> getCachedBuildings(): List<T>? {
        val json = prefs.getString(KEY_BUILDINGS, null) ?: return null
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached buildings", e)
            null
        }
    }
    
    /**
     * Cache floors data
     */
    fun <T> cacheFloors(floors: List<T>) {
        val json = gson.toJson(floors)
        prefs.edit().putString(KEY_FLOORS, json).apply()
        updateCacheTimestamp()
        Log.d(TAG, "Cached ${floors.size} floors")
    }
    
    /**
     * Get cached floors
     */
    inline fun <reified T> getCachedFloors(): List<T>? {
        val json = prefs.getString(KEY_FLOORS, null) ?: return null
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached floors", e)
            null
        }
    }
    
    /**
     * Cache beacons for a specific floor
     */
    fun <T> cacheBeacons(floorId: Int, beacons: List<T>) {
        val json = gson.toJson(beacons)
        prefs.edit().putString("$KEY_BEACONS_PREFIX$floorId", json).apply()
        Log.d(TAG, "Cached ${beacons.size} beacons for floor $floorId")
    }
    
    /**
     * Get cached beacons for a floor
     */
    inline fun <reified T> getCachedBeacons(floorId: Int): List<T>? {
        val json = prefs.getString("$KEY_BEACONS_PREFIX$floorId", null) ?: return null
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached beacons for floor $floorId", e)
            null
        }
    }
    
    /**
     * Cache route nodes for a specific floor
     */
    fun <T> cacheRouteNodes(floorId: Int, routeNodes: List<T>) {
        val json = gson.toJson(routeNodes)
        prefs.edit().putString("$KEY_ROUTE_NODES_PREFIX$floorId", json).apply()
        Log.d(TAG, "Cached ${routeNodes.size} route nodes for floor $floorId")
    }
    
    /**
     * Get cached route nodes for a floor
     */
    inline fun <reified T> getCachedRouteNodes(floorId: Int): List<T>? {
        val json = prefs.getString("$KEY_ROUTE_NODES_PREFIX$floorId", null) ?: return null
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached route nodes for floor $floorId", e)
            null
        }
    }
    
    /**
     * Cache POIs for a specific floor (as GeoJSON string)
     */
    fun cachePOIsGeoJSON(floorId: Int, geoJson: String) {
        prefs.edit().putString("${KEY_POIS_PREFIX}geojson_$floorId", geoJson).apply()
        Log.d(TAG, "Cached POI GeoJSON for floor $floorId")
    }
    
    /**
     * Get cached POIs GeoJSON for a floor
     */
    fun getCachedPOIsGeoJSON(floorId: Int): String? {
        return prefs.getString("${KEY_POIS_PREFIX}geojson_$floorId", null)
    }
    
    /**
     * Cache node-to-floor mapping
     */
    fun cacheNodeToFloorMap(mapping: Map<String, Int>) {
        val json = gson.toJson(mapping)
        prefs.edit().putString(KEY_NODE_TO_FLOOR_MAP, json).apply()
        Log.d(TAG, "Cached node-to-floor mapping with ${mapping.size} entries")
    }
    
    /**
     * Get cached node-to-floor mapping
     */
    fun getCachedNodeToFloorMap(): Map<String, Int>? {
        val json = prefs.getString(KEY_NODE_TO_FLOOR_MAP, null) ?: return null
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached node-to-floor map", e)
            null
        }
    }
    
    /**
     * Update cache timestamp
     */
    private fun updateCacheTimestamp() {
        prefs.edit().putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis()).apply()
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cache cleared")
    }
}
