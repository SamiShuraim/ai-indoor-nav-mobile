package com.KFUPM.ai_indoor_nav_mobile.localization

import android.content.Context
import android.util.Log
import com.KFUPM.ai_indoor_nav_mobile.ApiConstants
import com.KFUPM.ai_indoor_nav_mobile.localization.models.*
import com.KFUPM.ai_indoor_nav_mobile.models.Beacon
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Configuration provider for localization data
 * Fetches beacons, graph, and config from API
 */
class ConfigProvider(private val context: Context) {
    private val TAG = "ConfigProvider"
    
    private val client = OkHttpClient()
    private val gson = Gson()
    
    /**
     * Fetch beacons for a floor and map names to MAC addresses
     * Uses cached mappings first, then attempts quick scan for unmapped beacons
     * Returns partial results if not all beacons are mapped yet
     */
    suspend fun fetchBeacons(floorId: Int, beaconNameMapper: BeaconNameMapper? = null): List<LocalizationBeacon>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.beaconsByFloor(floorId)}"
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            val type = object : TypeToken<List<Beacon>>() {}.type
                            val beacons = gson.fromJson<List<Beacon>>(jsonString, type)
                            
                            // If beacons don't have MAC addresses, try to map names to MACs
                            var nameToMacMap: Map<String, String> = emptyMap()
                            val beaconsWithoutMac = beacons.filter { it.uuid.isNullOrBlank() }
                            
                            if (beaconsWithoutMac.isNotEmpty() && beaconNameMapper != null) {
                                val beaconNames = beaconsWithoutMac.mapNotNull { it.name }
                                
                                // First try to get cached mappings
                                val cachedMappings = beaconNameMapper.getCachedMappings()
                                val cachedCount = beaconNames.count { cachedMappings.containsKey(it) }
                                
                                Log.d(TAG, "Beacons missing MAC addresses: ${beaconNames.size}, cached: $cachedCount")
                                
                                // Quick scan for unmapped beacons (shorter timeout for initial load)
                                nameToMacMap = beaconNameMapper.mapBeaconNamesToMacAddresses(beaconNames, scanDurationMs = 3000)
                                
                                val unmappedCount = beaconNames.size - nameToMacMap.size
                                if (unmappedCount > 0) {
                                    val unmapped = beaconNameMapper.getUnmappedBeacons(beaconNames)
                                    Log.w(TAG, "Still $unmappedCount beacons unmapped: $unmapped (will be found by background mapper)")
                                }
                            }
                            
                            // Convert to LocalizationBeacon
                            // Use MAC address (uuid) as ID if available, otherwise try name mapping
                            val locBeacons = beacons.mapNotNull { beacon ->
                                val macAddress = when {
                                    // First priority: uuid field from database
                                    !beacon.uuid.isNullOrBlank() -> beacon.uuid!!.uppercase()
                                    // Second priority: mapped MAC from beacon name (cached or scanned)
                                    beacon.name != null && nameToMacMap.containsKey(beacon.name) -> {
                                        val mappedMac = nameToMacMap[beacon.name]!!
                                        Log.d(TAG, "Using mapping: '${beacon.name}' -> $mappedMac")
                                        mappedMac
                                    }
                                    // No MAC address available yet
                                    else -> {
                                        Log.d(TAG, "Beacon ${beacon.name} not yet mapped (will be found by background mapper)")
                                        null
                                    }
                                }
                                
                                if (macAddress == null) {
                                    null
                                } else {
                                    LocalizationBeacon(
                                        id = macAddress,
                                        x = beacon.x,
                                        y = beacon.y
                                    )
                                }
                            }
                            
                            Log.d(TAG, "Fetched ${locBeacons.size}/${beacons.size} beacons for floor $floorId with MAC addresses")
                            if (locBeacons.size < beacons.size) {
                                Log.d(TAG, "Note: Background mapper will continue finding unmapped beacons")
                            }
                            
                            locBeacons
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch beacons: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching beacons", e)
                null
            }
        }
    }
    
    /**
     * Get all beacon names for a floor (for background mapping)
     */
    suspend fun fetchBeaconNames(floorId: Int): List<String>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.beaconsByFloor(floorId)}"
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            val type = object : TypeToken<List<Beacon>>() {}.type
                            val beacons = gson.fromJson<List<Beacon>>(jsonString, type)
                            
                            // Get all beacon names (for those without MAC addresses)
                            val beaconNames = beacons
                                .filter { it.uuid.isNullOrBlank() && it.name != null }
                                .mapNotNull { it.name }
                            
                            Log.d(TAG, "Found ${beaconNames.size} beacon names for floor $floorId")
                            beaconNames
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch beacon names: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching beacon names", e)
                null
            }
        }
    }
    
    /**
     * Get ALL beacon names from ALL floors (for comprehensive background mapping)
     */
    suspend fun fetchAllBeaconNames(floorIds: List<Int>): List<String> {
        return withContext(Dispatchers.IO) {
            val allBeaconNames = mutableSetOf<String>()
            
            floorIds.forEach { floorId ->
                try {
                    val beaconNames = fetchBeaconNames(floorId)
                    if (beaconNames != null) {
                        allBeaconNames.addAll(beaconNames)
                        Log.d(TAG, "Added ${beaconNames.size} beacon names from floor $floorId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching beacon names for floor $floorId", e)
                }
            }
            
            Log.d(TAG, "Total beacon names across all floors: ${allBeaconNames.size}")
            allBeaconNames.toList()
        }
    }
    
    /**
     * Fetch graph for a floor by building it from RouteNodes
     */
    suspend fun fetchGraph(floorId: Int): IndoorGraph? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.routeNodesByFloor(floorId)}"
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            val type = object : TypeToken<List<com.KFUPM.ai_indoor_nav_mobile.models.RouteNode>>() {}.type
                            val routeNodes = gson.fromJson<List<com.KFUPM.ai_indoor_nav_mobile.models.RouteNode>>(jsonString, type)
                            
                            // Build graph nodes
                            val nodes = routeNodes.map { node ->
                                GraphNode(
                                    id = node.id.toString(),
                                    x = node.x,
                                    y = node.y
                                )
                            }
                            
                            // Build graph edges from connected nodes
                            val edges = mutableListOf<GraphEdge>()
                            val nodeMap = routeNodes.associateBy { it.id }
                            
                            routeNodes.forEach { fromNode ->
                                fromNode.connectedNodeIds?.forEach { toNodeId ->
                                    val toNode = nodeMap[toNodeId]
                                    if (toNode != null) {
                                        // Calculate Euclidean distance
                                        val dx = toNode.x - fromNode.x
                                        val dy = toNode.y - fromNode.y
                                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                        
                                        edges.add(
                                            GraphEdge(
                                                from = fromNode.id.toString(),
                                                to = toNodeId.toString(),
                                                lengthM = distance,
                                                forwardBias = 0.5 // Symmetric by default
                                            )
                                        )
                                    }
                                }
                            }
                            
                            val graph = IndoorGraph(nodes = nodes, edges = edges)
                            
                            Log.d(TAG, "Built graph from ${routeNodes.size} route nodes: ${nodes.size} nodes and ${edges.size} edges")
                            graph
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch route nodes: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching graph from route nodes", e)
                null
            }
        }
    }
    
    /**
     * Fetch localization config
     * Falls back to default config if endpoint doesn't exist
     */
    suspend fun fetchConfig(): LocalizationConfig? {
        return withContext(Dispatchers.IO) {
            try {
                // TODO: Replace with actual config API endpoint when available
                val url = "${ApiConstants.API_BASE_URL}/api/LocalizationConfig"
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            val configResponse = gson.fromJson(jsonString, ConfigResponse::class.java)
                            val config = configResponse.config ?: LocalizationConfig(version = configResponse.version)
                            
                            Log.d(TAG, "Fetched config version: ${config.version}")
                            config
                        } else null
                    } else {
                        if (response.code == 404) {
                            Log.d(TAG, "Config endpoint not available, using defaults")
                        } else {
                            Log.w(TAG, "Failed to fetch config: ${response.code}")
                        }
                        // Use defaults
                        LocalizationConfig(version = "default")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Config endpoint not available, using default config")
                // Use defaults
                LocalizationConfig(version = "default")
            }
        }
    }
    
    /**
     * Check config version
     */
    suspend fun checkConfigVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${ApiConstants.API_BASE_URL}/api/LocalizationConfig/version"
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.trim()?.removeSurrounding("\"")
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking config version", e)
                null
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
    }
}
