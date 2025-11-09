package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphEdge
import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.ImuData
import kotlin.math.*

/**
 * Transition model with speed gating and forward bias
 */
class TransitionModel(
    private val graphModel: GraphModel,
    private val maxWalkingSpeed: Double = 1.8, // m/s
    private val forwardBiasLambda: Double = 1.5,
    private val tickDeltaS: Double = 1.0 // 1 second per tick
) {
    private val TAG = "TransitionModel"
    
    /**
     * Compute log transition probabilities from a node to all neighbors
     * Returns map of (neighborNodeId -> logProb)
     */
    fun computeLogTransitions(
        fromNodeId: String,
        imuData: ImuData
    ): Map<String, Double> {
        val fromNode = graphModel.getNode(fromNodeId) ?: return emptyMap()
        val neighbors = graphModel.getNeighbors(fromNodeId)
        
        val transitions = mutableMapOf<String, Double>()
        
        // Self-transition (staying at same node)
        val selfWeight = computeSelfTransitionWeight(imuData.stepsSinceLastTick)
        transitions[fromNodeId] = selfWeight
        
        // Neighbor transitions
        for ((neighborId, edge) in neighbors) {
            val neighborNode = graphModel.getNode(neighborId) ?: continue
            
            // Speed gating: disallow if too fast
            if (!isSpeedFeasible(edge.lengthM, tickDeltaS)) {
                continue
            }
            
            // Base transition weight with forward bias
            val weight = computeForwardBiasWeight(edge, imuData.headingRad)
            transitions[neighborId] = weight
        }
        
        // Normalize to log probabilities
        return normalizeToLogProbs(transitions)
    }
    
    /**
     * Compute self-transition weight based on steps
     */
    private fun computeSelfTransitionWeight(steps: Int): Double {
        // If no steps, prefer staying (higher weight)
        // If steps detected, reduce self-transition weight
        return if (steps == 0) {
            2.0 // Higher stickiness
        } else {
            0.5 // Reduce to allow progress
        }
    }
    
    /**
     * Check if transition is speed-feasible
     */
    private fun isSpeedFeasible(edgeLengthM: Double, deltaTimeS: Double): Boolean {
        val impliedSpeed = edgeLengthM / deltaTimeS
        return impliedSpeed <= maxWalkingSpeed
    }
    
    /**
     * Compute forward bias weight for an edge
     */
    private fun computeForwardBiasWeight(edge: GraphEdge, userHeading: Double?): Double {
        // Base weight
        var weight = 1.0
        
        // Apply directional bias if heading available
        if (userHeading != null) {
            val edgeHeading = graphModel.getEdgeHeading(edge)
            if (edgeHeading != null) {
                // Compute alignment: cos(headingDiff)
                val headingDiff = normalizeAngle(userHeading - edgeHeading)
                val alignment = cos(headingDiff)
                
                // Apply exponential bias: exp(λ * alignment)
                weight *= exp(forwardBiasLambda * alignment)
            }
        }
        
        // Apply edge's inherent forward bias (if asymmetric)
        // forwardBias = 0.5 means symmetric, >0.5 favors this direction
        val edgeBiasMultiplier = 0.5 + (edge.forwardBias - 0.5)
        weight *= edgeBiasMultiplier.coerceAtLeast(0.1)
        
        return weight
    }
    
    /**
     * Normalize weights to log probabilities
     */
    private fun normalizeToLogProbs(weights: Map<String, Double>): Map<String, Double> {
        val maxWeight = weights.values.maxOrNull() ?: return emptyMap()
        
        // Shift to prevent underflow
        val shiftedWeights = weights.mapValues { exp(it.value - maxWeight) }
        val sumWeights = shiftedWeights.values.sum()
        
        if (sumWeights < 1e-10) return emptyMap()
        
        return shiftedWeights.mapValues { ln(it.value / sumWeights) }
    }
    
    /**
     * Normalize angle to [-π, π]
     */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI
        while (normalized < -PI) normalized += 2 * PI
        return normalized
    }
    
    /**
     * Get max feasible distance per tick
     */
    fun getMaxFeasibleDistance(): Double {
        return maxWalkingSpeed * tickDeltaS
    }
}
