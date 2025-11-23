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
     * Fetch beacons for a floor and optionally map names to MAC addresses
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
                                Log.d(TAG, "Beacons missing MAC addresses, attempting to map names to MACs...")
                                val beaconNames = beaconsWithoutMac.mapNotNull { it.name }
                                nameToMacMap = beaconNameMapper.mapBeaconNamesToMacAddresses(beaconNames, 5000)
                            }
                            
                            // Convert to LocalizationBeacon
                            // Use MAC address (uuid) as ID if available, otherwise try name mapping
                            val locBeacons = beacons.mapNotNull { beacon ->
                                val macAddress = when {
                                    // First priority: uuid field from database
                                    !beacon.uuid.isNullOrBlank() -> beacon.uuid!!.uppercase()
                                    // Second priority: mapped MAC from beacon name
                                    beacon.name != null && nameToMacMap.containsKey(beacon.name) -> {
                                        val mappedMac = nameToMacMap[beacon.name]!!
                                        Log.d(TAG, "Mapped beacon '${beacon.name}' to MAC $mappedMac")
                                        mappedMac
                                    }
                                    // No MAC address available
                                    else -> {
                                        Log.w(TAG, "Beacon ${beacon.name} has no MAC address and couldn't be mapped, skipping")
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
                            
                            Log.d(TAG, "Fetched ${locBeacons.size} beacons for floor $floorId with MAC addresses: ${locBeacons.map { it.id }}")
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
