package com.KFUPM.ai_indoor_nav_mobile.localization

import android.content.Context
import android.util.Log
import com.KFUPM.ai_indoor_nav_mobile.localization.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/**
 * Main localization controller - orchestrates all components
 */
class LocalizationController(private val context: Context) {
    private val TAG = "LocalizationController"
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Components
    private var beaconScanner: BeaconScanner? = null
    private var imuTracker: ImuTracker? = null
    private var graphModel: GraphModel? = null
    private var observationModel: ObservationModel? = null
    private var transitionModel: TransitionModel? = null
    private var hmmEngine: HmmEngine? = null
    private val configProvider = ConfigProvider(context)
    
    // Configuration
    private var config: LocalizationConfig = LocalizationConfig(version = "default")
    private var currentFloorId: Int? = null
    
    // State
    private val _localizationState = MutableStateFlow<LocalizationState>(
        LocalizationState(
            currentNodeId = null,
            confidence = 0.0
        )
    )
    val localizationState: StateFlow<LocalizationState> = _localizationState
    
    private var isRunning = false
    private var updateJob: Job? = null
    
    /**
     * Initialize localization for a specific floor
     */
    suspend fun initialize(floorId: Int, initialNodeId: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing localization for floor $floorId")
                
                // Fetch configuration
                val fetchedConfig = configProvider.fetchConfig()
                if (fetchedConfig != null) {
                    config = fetchedConfig
                }
                
                // Fetch beacons
                val beacons = configProvider.fetchBeacons(floorId)
                if (beacons == null || beacons.isEmpty()) {
                    Log.e(TAG, "No beacons found for floor $floorId")
                    return@withContext false
                }
                
                // Fetch graph
                val graph = configProvider.fetchGraph(floorId)
                if (graph == null || graph.nodes.isEmpty()) {
                    Log.e(TAG, "No graph found for floor $floorId")
                    return@withContext false
                }
                
                // Initialize components
                currentFloorId = floorId
                
                graphModel = GraphModel(graph)
                observationModel = ObservationModel(
                    beacons = beacons,
                    rankWeight = config.rankWeight,
                    pairwiseWeight = config.pairwiseWeight,
                    distanceRatioSlope = config.distanceRatioSlope
                )
                transitionModel = TransitionModel(
                    graphModel = graphModel!!,
                    maxWalkingSpeed = config.maxWalkingSpeed,
                    forwardBiasLambda = config.forwardBiasLambda,
                    tickDeltaS = 1.0 / config.tickRateHz
                )
                hmmEngine = HmmEngine(
                    graphModel = graphModel!!,
                    observationModel = observationModel!!,
                    transitionModel = transitionModel!!,
                    hysteresisK = config.hysteresisK,
                    searchRadiusM = config.searchRadiusM
                )
                
                // Initialize HMM
                hmmEngine?.initialize(initialNodeId)
                
                // Initialize sensors
                beaconScanner = BeaconScanner(
                    context = context,
                    windowSize = config.bleWindowSize,
                    emaGamma = config.bleEmaGamma
                )
                
                imuTracker = ImuTracker(context)
                
                Log.d(TAG, "Localization initialized successfully")
                Log.d(TAG, "Graph: ${graph.nodes.size} nodes, ${graph.edges.size} edges")
                Log.d(TAG, "Beacons: ${beacons.size}")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing localization", e)
                false
            }
        }
    }
    
    /**
     * Start localization
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "Localization already running")
            return
        }
        
        if (graphModel == null || observationModel == null || hmmEngine == null) {
            Log.e(TAG, "Localization not initialized. Call initialize() first.")
            return
        }
        
        // Start sensors
        beaconScanner?.startScanning()
        imuTracker?.startTracking()
        
        isRunning = true
        
        // Start update loop
        startUpdateLoop()
        
        Log.d(TAG, "Localization started")
    }
    
    /**
     * Stop localization
     */
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        updateJob?.cancel()
        
        beaconScanner?.stopScanning()
        imuTracker?.stopTracking()
        
        Log.d(TAG, "Localization stopped")
    }
    
    /**
     * Update configuration (e.g., after server push)
     */
    suspend fun updateConfig(
        beacons: List<LocalizationBeacon>,
        graph: IndoorGraph,
        configVersion: String
    ) {
        withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Updating configuration to version: $configVersion")
                
                val wasRunning = isRunning
                if (wasRunning) {
                    stop()
                }
                
                // Update components
                graphModel = GraphModel(graph)
                observationModel = ObservationModel(
                    beacons = beacons,
                    rankWeight = config.rankWeight,
                    pairwiseWeight = config.pairwiseWeight,
                    distanceRatioSlope = config.distanceRatioSlope
                )
                transitionModel = TransitionModel(
                    graphModel = graphModel!!,
                    maxWalkingSpeed = config.maxWalkingSpeed,
                    forwardBiasLambda = config.forwardBiasLambda,
                    tickDeltaS = 1.0 / config.tickRateHz
                )
                
                // Reset HMM
                hmmEngine = HmmEngine(
                    graphModel = graphModel!!,
                    observationModel = observationModel!!,
                    transitionModel = transitionModel!!,
                    hysteresisK = config.hysteresisK,
                    searchRadiusM = config.searchRadiusM
                )
                hmmEngine?.initialize()
                
                if (wasRunning) {
                    start()
                }
                
                Log.d(TAG, "Configuration updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating configuration", e)
            }
        }
    }
    
    /**
     * Start the update loop
     */
    private fun startUpdateLoop() {
        val tickIntervalMs = (1000.0 / config.tickRateHz).toLong()
        
        updateJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    processTick()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing tick", e)
                }
                
                delay(tickIntervalMs)
            }
        }
    }
    
    /**
     * Process a single tick
     */
    private suspend fun processTick() {
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            // Get sensor data
            val rssiMap = beaconScanner?.getCurrentRssiMap() ?: emptyMap()
            val imuData = imuTracker?.getImuDataAndReset() ?: ImuData(0, null)
            
            // Check minimum beacon visibility
            if (rssiMap.size < 2) {
                // Insufficient beacons - freeze state
                _localizationState.value = _localizationState.value.copy(
                    confidence = max(_localizationState.value.confidence * 0.9, 0.0).coerceAtMost(0.4),
                    debug = DebugInfo(
                        topPosteriors = emptyList(),
                        visibleBeaconCount = rssiMap.size,
                        speedGateApplied = false,
                        hysteresisHold = true,
                        junctionAmbiguity = false,
                        tickDurationMs = System.currentTimeMillis() - startTime
                    )
                )
                return@withContext
            }
            
            // Update HMM
            val result = hmmEngine?.update(rssiMap, imuData)
            
            if (result != null) {
                // Optional: self-calibration
                if (result.confidence > config.calibrationConfidenceThreshold && result.nodeId != null) {
                    val node = graphModel?.getNode(result.nodeId)
                    if (node != null) {
                        observationModel?.updateCalibration(
                            node,
                            rssiMap,
                            config.calibrationLearningRate
                        )
                    }
                }
                
                // Get top posteriors for debug
                val topPosteriors = hmmEngine?.getTopPosteriors(result.posteriors, 3) ?: emptyList()
                
                // Detect junction ambiguity (if top 2 posteriors are close)
                val junctionAmbiguity = if (topPosteriors.size >= 2) {
                    val diff = topPosteriors[0].second - topPosteriors[1].second
                    diff < 0.15 // If difference < 15%, consider ambiguous
                } else false
                
                // Update state
                _localizationState.value = LocalizationState(
                    currentNodeId = result.nodeId,
                    confidence = result.confidence,
                    pathHistory = hmmEngine?.getPathHistory() ?: emptyList(),
                    debug = DebugInfo(
                        topPosteriors = topPosteriors,
                        visibleBeaconCount = result.visibleBeaconCount,
                        speedGateApplied = false, // TODO: track from TransitionModel
                        hysteresisHold = result.nodeId != topPosteriors.firstOrNull()?.first,
                        junctionAmbiguity = junctionAmbiguity,
                        tickDurationMs = result.tickDurationMs
                    )
                )
                
                // Log performance
                if (result.tickDurationMs > 50) {
                    Log.w(TAG, "Tick took ${result.tickDurationMs}ms (target: <20ms)")
                }
            }
        }
    }
    
    /**
     * Get current position (x, y coordinates)
     */
    fun getCurrentPosition(): Pair<Double, Double>? {
        val nodeId = _localizationState.value.currentNodeId ?: return null
        val node = graphModel?.getNode(nodeId) ?: return null
        return Pair(node.x, node.y)
    }
    
    /**
     * Check for config updates
     */
    suspend fun checkForUpdates(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val serverVersion = configProvider.checkConfigVersion()
                if (serverVersion != null && serverVersion != config.version) {
                    Log.d(TAG, "New config version available: $serverVersion (current: ${config.version})")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                false
            }
        }
    }
    
    /**
     * Force reload from server
     */
    suspend fun reload(): Boolean {
        val floorId = currentFloorId ?: return false
        return initialize(floorId, hmmEngine?.getCurrentNode())
    }
    
    /**
     * Reset localization
     */
    fun reset() {
        hmmEngine?.reset()
        imuTracker?.reset()
        beaconScanner?.clear()
        
        _localizationState.value = LocalizationState(
            currentNodeId = null,
            confidence = 0.0
        )
        
        Log.d(TAG, "Localization reset")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        beaconScanner?.cleanup()
        imuTracker?.cleanup()
        configProvider.cleanup()
        scope.cancel()
        
        Log.d(TAG, "Localization cleaned up")
    }
}
