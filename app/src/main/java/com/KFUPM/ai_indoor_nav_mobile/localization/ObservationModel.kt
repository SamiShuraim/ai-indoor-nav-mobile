package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.LocalizationBeacon
import kotlin.math.*

/**
 * Observation model using rank and pairwise RSSI consistency
 */
class ObservationModel(
    private val beacons: List<LocalizationBeacon>,
    private val rankWeight: Double = 3.0,        // α
    private val pairwiseWeight: Double = 1.0,    // β
    private val distanceRatioSlope: Double = 8.0 // κ
) {
    private val TAG = "ObservationModel"
    
    // Beacon lookup
    private val beaconMap = beacons.associateBy { it.id }
    
    // Optional: calibration biases (beacon-specific)
    private val beaconBias = mutableMapOf<String, Double>()
    private var globalSlope = 1.0
    
    /**
     * Compute log-likelihood for a candidate node given RSSI observations
     */
    fun computeLogLikelihood(
        node: GraphNode,
        rssiMap: Map<String, Double>
    ): Double {
        if (rssiMap.isEmpty()) return Double.NEGATIVE_INFINITY
        
        // Filter to beacons we know about
        // Note: In fallback mode (no MAC addresses), beaconMap may have placeholder IDs
        // so we match beacons by proximity instead
        val validRssiMap = if (beaconMap.keys.any { it.matches(Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$", RegexOption.IGNORE_CASE)) }) {
            // We have real MAC addresses, use exact matching
            rssiMap.filter { it.key in beaconMap }
        } else {
            // Fallback mode: use all RSSI data
            rssiMap
        }
        
        if (validRssiMap.isEmpty()) {
            // No matching beacons, use distance-based fallback
            return computeFallbackLogLikelihood(node, rssiMap)
        }
        
        val logLRank = computeRankLogLikelihood(node, validRssiMap)
        val logLPair = computePairwiseLogLikelihood(node, validRssiMap)
        
        return logLRank + logLPair
    }
    
    /**
     * Fallback likelihood when we don't have beacon mapping
     * Uses RSSI strength as proxy for proximity
     */
    private fun computeFallbackLogLikelihood(node: GraphNode, rssiMap: Map<String, Double>): Double {
        // Use average RSSI as a simple proximity measure
        // Higher RSSI = closer = better likelihood
        val avgRssi = rssiMap.values.average()
        // Convert RSSI to log-likelihood (rough approximation)
        return (avgRssi + 100.0) / 10.0 // Scale -100 to -30 dBm to 0 to 7
    }
    
    /**
     * Compute rank-based log-likelihood (Spearman's ρ)
     */
    private fun computeRankLogLikelihood(
        node: GraphNode,
        rssiMap: Map<String, Double>
    ): Double {
        // Compute distances from node to each beacon
        val beaconDistances = rssiMap.keys.mapNotNull { beaconId ->
            val beacon = beaconMap[beaconId] ?: return@mapNotNull null
            beaconId to euclideanDistance(node.x, node.y, beacon.x, beacon.y)
        }.toMap()
        
        if (beaconDistances.isEmpty()) return 0.0
        
        // Rank beacons by distance (ascending)
        val distanceRanks = beaconDistances.entries
            .sortedBy { it.value }
            .mapIndexed { index, entry -> entry.key to (index + 1) }
            .toMap()
        
        // Rank beacons by RSSI (descending - stronger signal = closer)
        val rssiRanks = rssiMap.entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry -> entry.key to (index + 1) }
            .toMap()
        
        // Compute Spearman's ρ
        val rho = computeSpearmanCorrelation(distanceRanks, rssiRanks)
        
        // Convert to log-likelihood
        return rankWeight * rho
    }
    
    /**
     * Compute pairwise consistency log-likelihood
     */
    private fun computePairwiseLogLikelihood(
        node: GraphNode,
        rssiMap: Map<String, Double>
    ): Double {
        val beaconIds = rssiMap.keys.toList()
        if (beaconIds.size < 2) return 0.0
        
        var logL = 0.0
        var pairCount = 0
        
        // For all ordered pairs (i, j)
        for (i in beaconIds.indices) {
            for (j in beaconIds.indices) {
                if (i == j) continue
                
                val beaconI = beaconMap[beaconIds[i]] ?: continue
                val beaconJ = beaconMap[beaconIds[j]] ?: continue
                
                val rssiI = rssiMap[beaconIds[i]]!!
                val rssiJ = rssiMap[beaconIds[j]]!!
                
                val distI = euclideanDistance(node.x, node.y, beaconI.x, beaconI.y)
                val distJ = euclideanDistance(node.x, node.y, beaconJ.x, beaconJ.y)
                
                // Expected: if distI < distJ, then rssiI > rssiJ
                // Score with logistic function
                val rssiDiff = rssiI - rssiJ
                val distRatio = if (distI > 1e-6) distJ / distI else 1.0
                val expectedDiff = distanceRatioSlope * ln(distRatio) / ln(10.0)
                
                val score = rssiDiff - expectedDiff
                val logistic = 1.0 / (1.0 + exp(-score))
                
                logL += ln(logistic + 1e-10) // Add small epsilon for numerical stability
                pairCount++
            }
        }
        
        return if (pairCount > 0) pairwiseWeight * logL else 0.0
    }
    
    /**
     * Compute Spearman's rank correlation coefficient
     */
    private fun computeSpearmanCorrelation(
        ranks1: Map<String, Int>,
        ranks2: Map<String, Int>
    ): Double {
        val commonKeys = ranks1.keys.intersect(ranks2.keys)
        if (commonKeys.size < 2) return 0.0
        
        val n = commonKeys.size
        var sumDiffSquared = 0.0
        
        for (key in commonKeys) {
            val r1 = ranks1[key]!!
            val r2 = ranks2[key]!!
            val diff = r1 - r2
            sumDiffSquared += diff * diff
        }
        
        // Spearman's ρ = 1 - (6 * Σd²) / (n(n²-1))
        val rho = 1.0 - (6.0 * sumDiffSquared) / (n * (n * n - 1))
        return rho
    }
    
    /**
     * Update calibration (online learning)
     */
    fun updateCalibration(
        node: GraphNode,
        rssiMap: Map<String, Double>,
        learningRate: Double = 0.01
    ) {
        for ((beaconId, rssi) in rssiMap) {
            val beacon = beaconMap[beaconId] ?: continue
            val dist = euclideanDistance(node.x, node.y, beacon.x, beacon.y)
            
            // Bias update: b_b ← (1-η)*b_b + η*(RSSI + 10n*log10(d))
            val currentBias = beaconBias[beaconId] ?: 0.0
            val expectedRssi = rssi + 10 * globalSlope * log10(dist + 1e-6)
            val newBias = (1 - learningRate) * currentBias + learningRate * expectedRssi
            
            // Cap bias to reasonable range
            beaconBias[beaconId] = newBias.coerceIn(-50.0, 50.0)
        }
    }
    
    /**
     * Euclidean distance
     */
    private fun euclideanDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get beacon position
     */
    fun getBeaconPosition(beaconId: String): Pair<Double, Double>? {
        val beacon = beaconMap[beaconId] ?: return null
        return Pair(beacon.x, beacon.y)
    }
}
