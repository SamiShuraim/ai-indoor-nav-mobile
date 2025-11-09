package com.KFUPM.ai_indoor_nav_mobile.localization

import android.util.Log
import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.ImuData
import kotlin.math.exp
import kotlin.math.ln

/**
 * HMM Engine with online Viterbi and hysteresis
 */
class HmmEngine(
    private val graphModel: GraphModel,
    private val observationModel: ObservationModel,
    private val transitionModel: TransitionModel,
    private val hysteresisK: Int = 2,
    private val searchRadiusM: Double = 25.0
) {
    private val TAG = "HmmEngine"
    
    // Previous tick's log posteriors
    private var logPosteriorPrev = mutableMapOf<String, Double>()
    
    // Hysteresis tracking
    private var currentNodeId: String? = null
    private var candidateNodeId: String? = null
    private var candidateCount = 0
    
    // Path history
    private val pathHistory = mutableListOf<String>()
    private val maxPathHistory = 10
    
    /**
     * Initialize with uniform distribution or specific node
     */
    fun initialize(initialNodeId: String? = null) {
        if (initialNodeId != null && graphModel.getNode(initialNodeId) != null) {
            // Start at specific node
            logPosteriorPrev.clear()
            logPosteriorPrev[initialNodeId] = 0.0
            currentNodeId = initialNodeId
            pathHistory.clear()
            pathHistory.add(initialNodeId)
            Log.d(TAG, "Initialized at node: $initialNodeId")
        } else {
            // Uniform distribution over all nodes
            val allNodes = graphModel.getAllNodes()
            val logUniform = -ln(allNodes.size.toDouble())
            logPosteriorPrev = allNodes.associate { it.id to logUniform }.toMutableMap()
            currentNodeId = null
            pathHistory.clear()
            Log.d(TAG, "Initialized with uniform distribution over ${allNodes.size} nodes")
        }
        
        candidateNodeId = null
        candidateCount = 0
    }
    
    /**
     * Update HMM with new observations and IMU data
     */
    fun update(
        rssiMap: Map<String, Double>,
        imuData: ImuData
    ): HmmResult {
        val startTime = System.currentTimeMillis()
        
        // Get candidate nodes to consider
        val candidateNodes = getCandidateNodes()
        
        if (candidateNodes.isEmpty()) {
            Log.w(TAG, "No candidate nodes available")
            return HmmResult(
                nodeId = currentNodeId,
                confidence = 0.0,
                posteriors = emptyMap(),
                visibleBeaconCount = rssiMap.size,
                tickDurationMs = System.currentTimeMillis() - startTime
            )
        }
        
        // Compute new posteriors
        val logPosteriorNew = mutableMapOf<String, Double>()
        
        for (node in candidateNodes) {
            val nodeId = node.id
            
            // Observation likelihood
            val logLObs = observationModel.computeLogLikelihood(node, rssiMap)
            
            // Find best predecessor
            var maxLogTransitionPosterior = Double.NEGATIVE_INFINITY
            
            // Self-transition and neighbor transitions
            val possiblePredecessors = getPossiblePredecessors(nodeId)
            
            for (predId in possiblePredecessors) {
                val logPosteriorPred = logPosteriorPrev[predId] ?: continue
                val logTransitions = transitionModel.computeLogTransitions(predId, imuData)
                val logTransition = logTransitions[nodeId] ?: Double.NEGATIVE_INFINITY
                
                val logTransitionPosterior = logPosteriorPred + logTransition
                if (logTransitionPosterior > maxLogTransitionPosterior) {
                    maxLogTransitionPosterior = logTransitionPosterior
                }
            }
            
            // Combine observation and transition
            val logPosterior = logLObs + maxLogTransitionPosterior
            logPosteriorNew[nodeId] = logPosterior
        }
        
        // Normalize posteriors
        val normalizedPosteriors = normalizeLogPosteriors(logPosteriorNew)
        
        // Update previous posteriors for next tick
        logPosteriorPrev = normalizedPosteriors.toMutableMap()
        
        // Find best node with hysteresis
        val bestNode = applyHysteresis(normalizedPosteriors)
        
        // Update path history
        if (bestNode != null && (pathHistory.isEmpty() || pathHistory.last() != bestNode)) {
            pathHistory.add(bestNode)
            if (pathHistory.size > maxPathHistory) {
                pathHistory.removeAt(0)
            }
        }
        
        // Compute confidence
        val confidence = computeConfidence(normalizedPosteriors)
        
        val tickDuration = System.currentTimeMillis() - startTime
        
        return HmmResult(
            nodeId = bestNode,
            confidence = confidence,
            posteriors = normalizedPosteriors,
            visibleBeaconCount = rssiMap.size,
            tickDurationMs = tickDuration
        )
    }
    
    /**
     * Get candidate nodes to consider (for large graphs, restrict search)
     */
    private fun getCandidateNodes(): List<GraphNode> {
        val allNodes = graphModel.getAllNodes()
        
        // If graph is small, consider all nodes
        if (allNodes.size <= 500) {
            return allNodes
        }
        
        // For large graphs, only consider nodes within radius of current node
        return if (currentNodeId != null) {
            graphModel.getNodesWithinRadiusOfNode(currentNodeId!!, searchRadiusM)
        } else {
            // If no current node, take all (first iteration)
            allNodes
        }
    }
    
    /**
     * Get possible predecessors for a node
     */
    private fun getPossiblePredecessors(nodeId: String): List<String> {
        val predecessors = mutableSetOf<String>()
        
        // Self-transition
        predecessors.add(nodeId)
        
        // All nodes that have an edge TO this node
        val allNodes = graphModel.getAllNodes()
        for (node in allNodes) {
            val neighbors = graphModel.getNeighbors(node.id)
            if (neighbors.any { it.first == nodeId }) {
                predecessors.add(node.id)
            }
        }
        
        return predecessors.toList()
    }
    
    /**
     * Normalize log posteriors to probabilities
     */
    private fun normalizeLogPosteriors(logPosteriors: Map<String, Double>): Map<String, Double> {
        if (logPosteriors.isEmpty()) return emptyMap()
        
        // Filter out invalid log values (NaN or infinite)
        val validLogPosteriors = logPosteriors.filterValues { !it.isNaN() && it.isFinite() }
        if (validLogPosteriors.isEmpty()) return emptyMap()
        
        val maxLog = validLogPosteriors.values.maxOrNull() ?: return emptyMap()
        
        // Shift and exponentiate
        val expPosteriors = validLogPosteriors.mapValues { 
            val expValue = exp(it.value - maxLog)
            if (expValue.isNaN() || !expValue.isFinite()) 0.0 else expValue
        }
        
        val sum = expPosteriors.values.sum()
        
        // If sum is too small or invalid, return empty
        if (sum < 1e-10 || sum.isNaN() || !sum.isFinite()) {
            return emptyMap()
        }
        
        return expPosteriors.mapValues { 
            val normalized = it.value / sum
            if (normalized.isNaN() || !normalized.isFinite()) 0.0 else normalized
        }
    }
    
    /**
     * Apply hysteresis: require K consecutive ticks before committing node change
     */
    private fun applyHysteresis(posteriors: Map<String, Double>): String? {
        if (posteriors.isEmpty()) return currentNodeId
        
        val maxEntry = posteriors.maxByOrNull { it.value } ?: return currentNodeId
        val bestNodeId = maxEntry.key
        
        // If this is the first decision, commit immediately
        if (currentNodeId == null) {
            currentNodeId = bestNodeId
            candidateNodeId = bestNodeId
            candidateCount = hysteresisK
            return bestNodeId
        }
        
        // If best node matches current, reset candidate tracking
        if (bestNodeId == currentNodeId) {
            candidateNodeId = null
            candidateCount = 0
            return currentNodeId
        }
        
        // If best node matches candidate, increment counter
        if (bestNodeId == candidateNodeId) {
            candidateCount++
            if (candidateCount >= hysteresisK) {
                // Commit to new node
                currentNodeId = bestNodeId
                candidateNodeId = null
                candidateCount = 0
                Log.d(TAG, "Node changed to: $bestNodeId")
            }
        } else {
            // New candidate
            candidateNodeId = bestNodeId
            candidateCount = 1
        }
        
        return currentNodeId
    }
    
    /**
     * Compute confidence as max posterior probability
     */
    private fun computeConfidence(posteriors: Map<String, Double>): Double {
        if (posteriors.isEmpty()) return 0.0
        val maxConfidence = posteriors.values.maxOrNull() ?: 0.0
        // Ensure confidence is valid
        return if (maxConfidence.isNaN() || !maxConfidence.isFinite()) 0.0 else maxConfidence
    }
    
    /**
     * Get current node
     */
    fun getCurrentNode(): String? = currentNodeId
    
    /**
     * Get path history
     */
    fun getPathHistory(): List<String> = pathHistory.toList()
    
    /**
     * Get top N posteriors
     */
    fun getTopPosteriors(posteriors: Map<String, Double>, n: Int = 3): List<Pair<String, Double>> {
        return posteriors.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }
    
    /**
     * Reset engine
     */
    fun reset() {
        logPosteriorPrev.clear()
        currentNodeId = null
        candidateNodeId = null
        candidateCount = 0
        pathHistory.clear()
    }
}

/**
 * HMM update result
 */
data class HmmResult(
    val nodeId: String?,
    val confidence: Double,
    val posteriors: Map<String, Double>,
    val visibleBeaconCount: Int,
    val tickDurationMs: Long
)
