package com.KFUPM.ai_indoor_nav_mobile.localization

import android.content.Context
import android.util.Log
import com.KFUPM.ai_indoor_nav_mobile.localization.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Auto-initializer for determining initial position from BLE scans
 */
class AutoInitializer(
    private val context: Context,
    private val configProvider: ConfigProvider,
    private val beaconNameMapper: BeaconNameMapper? = null
) {
    private val TAG = "AutoInitializer"
    
    /**
     * Result of auto-initialization
     */
    data class AutoInitResult(
        val floorId: Int,
        val initialNodeId: String?,
        val confidence: Double,
        val beacons: List<LocalizationBeacon>,
        val graph: IndoorGraph,
        val config: LocalizationConfig
    )
    
    /**
     * Automatically determine initial position from BLE scans
     * 
     * Process:
     * 1. Scan BLE beacons for 3-5 seconds
     * 2. Match visible beacons to floors in database
     * 3. Determine most likely floor
     * 4. Fetch graph for that floor
     * 5. Estimate initial node based on RSSI
     */
    suspend fun autoInitialize(
        availableFloorIds: List<Int>,
        scanDurationMs: Long = 5000
    ): AutoInitResult? {
        return try {
            Log.d(TAG, "Starting auto-initialization...")
            
            // Step 1: Scan for beacons
            Log.d(TAG, "Scanning for beacons (${scanDurationMs}ms)...")
            val rssiMap = scanForBeacons(scanDurationMs)
            
            if (rssiMap.isEmpty()) {
                Log.e(TAG, "No beacons detected")
                return null
            }
            
            Log.d(TAG, "Detected ${rssiMap.size} beacons: ${rssiMap.keys}")
            
            // Step 2: Fetch beacons for all floors to determine which floor we're on
            Log.d(TAG, "Fetching beacon data for ${availableFloorIds.size} floors...")
            val floorBeacons = mutableMapOf<Int, List<LocalizationBeacon>>()
            
            for (floorId in availableFloorIds) {
                val beacons = configProvider.fetchBeacons(floorId, beaconNameMapper)
                if (beacons != null && beacons.isNotEmpty()) {
                    floorBeacons[floorId] = beacons
                }
            }
            
            if (floorBeacons.isEmpty()) {
                Log.e(TAG, "No beacon data available for any floor")
                return null
            }
            
            // Step 3: Determine most likely floor
            val floorId = determineFloor(rssiMap, floorBeacons)
            if (floorId == null) {
                Log.e(TAG, "Could not determine floor from visible beacons")
                return null
            }
            
            Log.d(TAG, "✅ AUTO-INIT: Determined floor: $floorId")
            
            // Step 4: Fetch COMBINED graph and config for ALL floors
            val graph = configProvider.fetchCombinedGraph(availableFloorIds)
            if (graph == null || graph.nodes.isEmpty()) {
                Log.e(TAG, "No graph data available")
                return null
            }
            
            val config = configProvider.fetchConfig() ?: LocalizationConfig(version = "default")
            
            // Collect ALL beacons from ALL floors (not just detected floor)
            val allBeacons = floorBeacons.values.flatten()
            
            Log.d(TAG, "✅ Loaded combined graph with ${graph.nodes.size} nodes, ${graph.edges.size} edges")
            Log.d(TAG, "✅ Loaded ${allBeacons.size} beacons from ALL ${availableFloorIds.size} floors")
            
            // Step 5: Estimate initial node
            // CRITICAL: Only use beacons AND nodes from the detected floor for initial position
            val detectedFloorBeacons = floorBeacons[floorId]!!
            
            // Get graph for ONLY the detected floor (not combined graph)
            val detectedFloorGraph = configProvider.fetchGraph(floorId)
            if (detectedFloorGraph == null || detectedFloorGraph.nodes.isEmpty()) {
                Log.e(TAG, "No graph nodes for detected floor $floorId")
                return null
            }
            
            val (initialNode, confidence) = estimateInitialNode(rssiMap, detectedFloorBeacons, detectedFloorGraph)
            
            Log.d(TAG, "✅ AUTO-INIT: Initial node=${initialNode} on floor $floorId, confidence=${String.format("%.2f", confidence)}")
            
            AutoInitResult(
                floorId = floorId,
                initialNodeId = initialNode,
                confidence = confidence,
                beacons = allBeacons,  // Return ALL beacons from ALL floors
                graph = graph,  // Return combined graph from ALL floors
                config = config
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-initialization", e)
            null
        }
    }
    
    /**
     * Scan for beacons for a specified duration
     */
    private suspend fun scanForBeacons(durationMs: Long): Map<String, Double> {
        val scanner = BeaconScanner(context, windowSize = 5, emaGamma = 0.5)
        
        return try {
            scanner.startScanning()
            
            // Wait for scans to accumulate
            withTimeout(durationMs + 1000) {
                delay(durationMs)
            }
            
            scanner.getCurrentRssiMap()
        } finally {
            scanner.stopScanning()
            scanner.cleanup()
        }
    }
    
    /**
     * Determine which floor the device is on based on beacon matches
     */
    private fun determineFloor(
        rssiMap: Map<String, Double>,
        floorBeacons: Map<Int, List<LocalizationBeacon>>
    ): Int? {
        val visibleBeaconIds = rssiMap.keys
        
        // Score each floor by beacon overlap
        val floorScores = mutableMapOf<Int, FloorScore>()
        
        for ((floorId, beacons) in floorBeacons) {
            val floorBeaconIds = beacons.map { it.id }.toSet()
            
            // Count how many visible beacons belong to this floor
            val matchCount = visibleBeaconIds.count { it in floorBeaconIds }
            
            // Count how many beacons we see that DON'T belong to this floor
            val mismatchCount = visibleBeaconIds.count { it !in floorBeaconIds }
            
            // Average RSSI of matching beacons (stronger signal = closer floor)
            val avgRssi = if (matchCount > 0) {
                visibleBeaconIds
                    .filter { it in floorBeaconIds }
                    .mapNotNull { rssiMap[it] }
                    .average()
            } else {
                -100.0
            }
            
            floorScores[floorId] = FloorScore(
                matchCount = matchCount,
                mismatchCount = mismatchCount,
                avgRssi = avgRssi,
                totalBeacons = beacons.size
            )
            
            val score = score.matchCount * 10.0 + (score.avgRssi + 100) / 10.0 - score.mismatchCount * 2.0
            Log.d(TAG, "Floor $floorId: $matchCount matches, $mismatchCount mismatches, avg RSSI: ${String.format("%.1f", avgRssi)}, SCORE: ${String.format("%.2f", score)}")
        }
        
        // Select floor with best score
        val selectedFloor = floorScores.maxByOrNull { (_, score) ->
            // Prioritize floors with:
            // 1. More matching beacons (high match count)
            // 2. Stronger average signal (higher avgRssi)
            // 3. Fewer mismatches
            score.matchCount * 10.0 + (score.avgRssi + 100) / 10.0 - score.mismatchCount * 2.0
        }?.key
        
        Log.d(TAG, "🎯 SELECTED FLOOR: $selectedFloor")
        return selectedFloor
    }
    
    /**
     * Estimate initial node based on RSSI patterns
     */
    private fun estimateInitialNode(
        rssiMap: Map<String, Double>,
        beacons: List<LocalizationBeacon>,
        graph: IndoorGraph
    ): Pair<String?, Double> {
        if (rssiMap.size < 2) {
            return Pair(null, 0.0)
        }
        
        // Use a simplified observation model to score each node
        val beaconMap = beacons.associateBy { it.id }
        val nodeScores = mutableMapOf<String, Double>()
        
        for (node in graph.nodes) {
            val score = computeQuickScore(node, rssiMap, beaconMap)
            nodeScores[node.id] = score
        }
        
        // Find best node
        val bestEntry = nodeScores.maxByOrNull { it.value }
        if (bestEntry == null) {
            return Pair(null, 0.0)
        }
        
        // Compute confidence based on how much better the best node is
        val scores = nodeScores.values.sorted().reversed()
        val confidence = if (scores.size >= 2) {
            val gap = scores[0] - scores[1]
            (gap / (abs(scores[0]) + 1.0)).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
        
        return Pair(bestEntry.key, confidence)
    }
    
    /**
     * Quick scoring function for initial position estimation
     */
    private fun computeQuickScore(
        node: GraphNode,
        rssiMap: Map<String, Double>,
        beaconMap: Map<String, LocalizationBeacon>
    ): Double {
        var score = 0.0
        
        for ((beaconId, rssi) in rssiMap) {
            val beacon = beaconMap[beaconId] ?: continue
            
            // Distance from node to beacon
            val distance = euclideanDistance(node.x, node.y, beacon.x, beacon.y)
            
            // Expected RSSI based on distance (simple path loss model)
            // RSSI ≈ -50 - 20*log10(distance)
            val expectedRssi = -50.0 - 20.0 * kotlin.math.log10(distance.coerceAtLeast(1.0))
            
            // Score based on difference (smaller difference = better)
            val diff = kotlin.math.abs(rssi - expectedRssi)
            score -= diff / 10.0 // Normalize
        }
        
        return score
    }
    
    /**
     * Euclidean distance
     */
    private fun euclideanDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun abs(value: Double): Double = if (value < 0) -value else value
    
    /**
     * Floor scoring data
     */
    private data class FloorScore(
        val matchCount: Int,
        val mismatchCount: Int,
        val avgRssi: Double,
        val totalBeacons: Int
    )
}
