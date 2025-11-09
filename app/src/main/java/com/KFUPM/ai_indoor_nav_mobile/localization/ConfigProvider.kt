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
import java.io.File

/**
 * Configuration provider for localization data
 * Fetches beacons, graph, and config from API with local caching
 */
class ConfigProvider(private val context: Context) {
    private val TAG = "ConfigProvider"
    
    private val client = OkHttpClient()
    private val gson = Gson()
    
    private val cacheDir = File(context.cacheDir, "localization")
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Fetch beacons for a floor
     */
    suspend fun fetchBeacons(floorId: Int): List<LocalizationBeacon>? {
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
                            
                            // Convert to LocalizationBeacon
                            val locBeacons = beacons.map { beacon ->
                                LocalizationBeacon(
                                    id = beacon.uuid ?: beacon.id.toString(),
                                    x = beacon.x,
                                    y = beacon.y
                                )
                            }
                            
                            // Cache the result
                            cacheBeacons(floorId, locBeacons)
                            
                            Log.d(TAG, "Fetched ${locBeacons.size} beacons for floor $floorId")
                            locBeacons
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch beacons: ${response.code}")
                        // Try loading from cache
                        loadCachedBeacons(floorId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching beacons", e)
                // Try loading from cache
                loadCachedBeacons(floorId)
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
                            
                            // Cache the result
                            cacheGraph(floorId, graph)
                            
                            Log.d(TAG, "Built graph from ${routeNodes.size} route nodes: ${nodes.size} nodes and ${edges.size} edges")
                            graph
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch route nodes: ${response.code}")
                        // Try loading from cache
                        loadCachedGraph(floorId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching graph from route nodes", e)
                // Try loading from cache
                loadCachedGraph(floorId)
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
                            
                            // Cache the result
                            cacheConfig(config)
                            
                            Log.d(TAG, "Fetched config version: ${config.version}")
                            config
                        } else null
                    } else {
                        if (response.code == 404) {
                            Log.d(TAG, "Config endpoint not available, using defaults")
                        } else {
                            Log.w(TAG, "Failed to fetch config: ${response.code}")
                        }
                        // Try loading from cache or use defaults
                        loadCachedConfig() ?: LocalizationConfig(version = "default")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Config endpoint not available, using default config")
                // Try loading from cache or use defaults
                loadCachedConfig() ?: LocalizationConfig(version = "default")
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
    
    // Caching methods
    
    private fun cacheBeacons(floorId: Int, beacons: List<LocalizationBeacon>) {
        try {
            val file = File(cacheDir, "beacons_$floorId.json")
            file.writeText(gson.toJson(beacons))
        } catch (e: Exception) {
            Log.e(TAG, "Error caching beacons", e)
        }
    }
    
    private fun loadCachedBeacons(floorId: Int): List<LocalizationBeacon>? {
        return try {
            val file = File(cacheDir, "beacons_$floorId.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val type = object : TypeToken<List<LocalizationBeacon>>() {}.type
                gson.fromJson(jsonString, type)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached beacons", e)
            null
        }
    }
    
    private fun cacheGraph(floorId: Int, graph: IndoorGraph) {
        try {
            val file = File(cacheDir, "graph_$floorId.json")
            file.writeText(gson.toJson(graph))
        } catch (e: Exception) {
            Log.e(TAG, "Error caching graph", e)
        }
    }
    
    private fun loadCachedGraph(floorId: Int): IndoorGraph? {
        return try {
            val file = File(cacheDir, "graph_$floorId.json")
            if (file.exists()) {
                val jsonString = file.readText()
                gson.fromJson(jsonString, IndoorGraph::class.java)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached graph", e)
            null
        }
    }
    
    private fun cacheConfig(config: LocalizationConfig) {
        try {
            val file = File(cacheDir, "config.json")
            file.writeText(gson.toJson(config))
        } catch (e: Exception) {
            Log.e(TAG, "Error caching config", e)
        }
    }
    
    private fun loadCachedConfig(): LocalizationConfig? {
        return try {
            val file = File(cacheDir, "config.json")
            if (file.exists()) {
                val jsonString = file.readText()
                gson.fromJson(jsonString, LocalizationConfig::class.java)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached config", e)
            null
        }
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
    }
}
