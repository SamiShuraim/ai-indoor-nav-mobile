package com.KFUPM.ai_indoor_nav_mobile.localization.models

import com.google.gson.annotations.SerializedName

/**
 * Beacon metadata for localization (lightweight version)
 */
data class LocalizationBeacon(
    val id: String,
    val x: Double,
    val y: Double
)

/**
 * Graph node
 */
data class GraphNode(
    val id: String,
    val x: Double,
    val y: Double
)

/**
 * Graph edge
 */
data class GraphEdge(
    val from: String,
    val to: String,
    val lengthM: Double,
    val forwardBias: Double = 0.5 // 0..1, 0.5 means symmetric
)

/**
 * Graph data structure
 */
data class IndoorGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

/**
 * Localization configuration
 */
data class LocalizationConfig(
    val version: String,
    
    // BLE parameters
    val bleWindowSize: Int = 5,
    val bleEmaGamma: Double = 0.5,
    
    // Observation model parameters
    val rankWeight: Double = 3.0, // α
    val pairwiseWeight: Double = 1.0, // β
    val distanceRatioSlope: Double = 8.0, // κ
    
    // Transition model parameters
    val forwardBiasLambda: Double = 1.5, // λ
    val maxWalkingSpeed: Double = 1.8, // m/s
    
    // HMM parameters
    val hysteresisK: Int = 2, // consecutive ticks
    val tickRateHz: Double = 0.5, // Once per 2 seconds
    val searchRadiusM: Double = 25.0, // for large graphs
    
    // Calibration parameters
    val calibrationLearningRate: Double = 0.01, // η
    val calibrationConfidenceThreshold: Double = 0.9
)

/**
 * BLE scan result
 */
data class BleScanResult(
    val beaconId: String,
    val rssi: Double,
    val timestampMs: Long
)

/**
 * IMU data
 */
data class ImuData(
    val stepsSinceLastTick: Int,
    val headingRad: Double? // null if unavailable
)

/**
 * Localization state output
 */
data class LocalizationState(
    val currentNodeId: String?,
    val confidence: Double,
    val pathHistory: List<String> = emptyList(),
    val debug: DebugInfo? = null
)

/**
 * Debug information
 */
data class DebugInfo(
    val topPosteriors: List<Pair<String, Double>>, // (nodeId, posterior)
    val visibleBeaconCount: Int,
    val speedGateApplied: Boolean,
    val hysteresisHold: Boolean,
    val junctionAmbiguity: Boolean,
    val tickDurationMs: Long
)

/**
 * Server response for graph data
 */
data class GraphResponse(
    @SerializedName("version")
    val version: String,
    
    @SerializedName("nodes")
    val nodes: List<GraphNodeResponse>,
    
    @SerializedName("edges")
    val edges: List<GraphEdgeResponse>
)

data class GraphNodeResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("x")
    val x: Double,
    
    @SerializedName("y")
    val y: Double
)

data class GraphEdgeResponse(
    @SerializedName("from")
    val from: String,
    
    @SerializedName("to")
    val to: String,
    
    @SerializedName("length_m")
    val lengthM: Double,
    
    @SerializedName("forward_bias")
    val forwardBias: Double? = null
)

/**
 * Server response for config
 */
data class ConfigResponse(
    @SerializedName("version")
    val version: String,
    
    @SerializedName("config")
    val config: LocalizationConfig? = null
)
