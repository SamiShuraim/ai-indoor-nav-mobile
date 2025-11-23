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
        private const val KEY_BUILDINGS_ETAG = "buildings_etag"
        private const val KEY_FLOORS = "floors"
        private const val KEY_FLOORS_ETAG = "floors_etag"
        private const val KEY_BEACONS_PREFIX = "beacons_floor_"
        private const val KEY_BEACONS_ETAG_PREFIX = "beacons_etag_floor_"
        private const val KEY_ROUTE_NODES_PREFIX = "route_nodes_floor_"
        private const val KEY_ROUTE_NODES_ETAG_PREFIX = "route_nodes_etag_floor_"
        private const val KEY_POIS_PREFIX = "pois_floor_"
        private const val KEY_POIS_ETAG_PREFIX = "pois_etag_floor_"
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
     * Cache buildings data with ETag
     */
    fun <T> cacheBuildings(buildings: List<T>, etag: String? = null) {
        val json = gson.toJson(buildings)
        val editor = prefs.edit()
        editor.putString(KEY_BUILDINGS, json)
        etag?.let { editor.putString(KEY_BUILDINGS_ETAG, it) }
        editor.apply()
        updateCacheTimestamp()
        Log.d(TAG, "Cached ${buildings.size} buildings" + if (etag != null) " with ETag: $etag" else "")
    }
    
    /**
     * Get ETag for buildings
     */
    fun getBuildingsETag(): String? {
        return prefs.getString(KEY_BUILDINGS_ETAG, null)
    }
    
    /**
     * Get cached buildings
     */
    fun <T> getCachedBuildings(typeToken: TypeToken<List<T>>): List<T>? {
        val json = prefs.getString(KEY_BUILDINGS, null) ?: return null
        return try {
            gson.fromJson(json, typeToken.type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached buildings", e)
            null
        }
    }
    
    /**
     * Cache floors data with ETag
     */
    fun <T> cacheFloors(floors: List<T>, etag: String? = null) {
        val json = gson.toJson(floors)
        val editor = prefs.edit()
        editor.putString(KEY_FLOORS, json)
        etag?.let { editor.putString(KEY_FLOORS_ETAG, it) }
        editor.apply()
        updateCacheTimestamp()
        Log.d(TAG, "Cached ${floors.size} floors" + if (etag != null) " with ETag: $etag" else "")
    }
    
    /**
     * Get ETag for floors
     */
    fun getFloorsETag(): String? {
        return prefs.getString(KEY_FLOORS_ETAG, null)
    }
    
    /**
     * Get cached floors
     */
    fun <T> getCachedFloors(typeToken: TypeToken<List<T>>): List<T>? {
        val json = prefs.getString(KEY_FLOORS, null) ?: return null
        return try {
            gson.fromJson(json, typeToken.type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached floors", e)
            null
        }
    }
    
    /**
     * Cache beacons for a specific floor with ETag
     */
    fun <T> cacheBeacons(floorId: Int, beacons: List<T>, etag: String? = null) {
        val json = gson.toJson(beacons)
        val editor = prefs.edit()
        editor.putString("$KEY_BEACONS_PREFIX$floorId", json)
        etag?.let { editor.putString("$KEY_BEACONS_ETAG_PREFIX$floorId", it) }
        editor.apply()
        Log.d(TAG, "Cached ${beacons.size} beacons for floor $floorId" + if (etag != null) " with ETag" else "")
    }
    
    /**
     * Get ETag for beacons of a floor
     */
    fun getBeaconsETag(floorId: Int): String? {
        return prefs.getString("$KEY_BEACONS_ETAG_PREFIX$floorId", null)
    }
    
    /**
     * Get cached beacons for a floor
     */
    fun <T> getCachedBeacons(floorId: Int, typeToken: TypeToken<List<T>>): List<T>? {
        val json = prefs.getString("$KEY_BEACONS_PREFIX$floorId", null) ?: return null
        return try {
            gson.fromJson(json, typeToken.type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached beacons for floor $floorId", e)
            null
        }
    }
    
    /**
     * Cache route nodes for a specific floor with ETag
     */
    fun <T> cacheRouteNodes(floorId: Int, routeNodes: List<T>, etag: String? = null) {
        val json = gson.toJson(routeNodes)
        val editor = prefs.edit()
        editor.putString("$KEY_ROUTE_NODES_PREFIX$floorId", json)
        etag?.let { editor.putString("$KEY_ROUTE_NODES_ETAG_PREFIX$floorId", it) }
        editor.apply()
        Log.d(TAG, "Cached ${routeNodes.size} route nodes for floor $floorId" + if (etag != null) " with ETag" else "")
    }
    
    /**
     * Get ETag for route nodes of a floor
     */
    fun getRouteNodesETag(floorId: Int): String? {
        return prefs.getString("$KEY_ROUTE_NODES_ETAG_PREFIX$floorId", null)
    }
    
    /**
     * Get cached route nodes for a floor
     */
    fun <T> getCachedRouteNodes(floorId: Int, typeToken: TypeToken<List<T>>): List<T>? {
        val json = prefs.getString("$KEY_ROUTE_NODES_PREFIX$floorId", null) ?: return null
        return try {
            gson.fromJson(json, typeToken.type)
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
