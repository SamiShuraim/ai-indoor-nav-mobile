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
    private var backgroundMapper: BackgroundBeaconMapper? = null
    private var beaconNameMapper: BeaconNameMapper? = null
    
    // Configuration
    private var config: LocalizationConfig = LocalizationConfig(version = "default")
    private var currentFloorId: Int? = null
    private var availableFloorIds: List<Int> = emptyList()
    
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
     * Auto-initialize by detecting current position from BLE scans
     * 
     * This method:
     * 1. Scans for nearby beacons
     * 2. Determines which floor you're on
     * 3. Estimates initial position
     * 4. Initializes localization system
     * 
     * @param availableFloorIds List of floor IDs to check (get from API)
     * @param scanDurationMs How long to scan beacons (default 5s)
     * @return true if successful, false otherwise
     */
    suspend fun autoInitialize(
        availableFloorIds: List<Int>,
        scanDurationMs: Long = 5000
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Auto-initializing localization...")
                
                // Create beacon name mapper for automatic MAC address detection
                beaconNameMapper = BeaconNameMapper(context)
                val autoInit = AutoInitializer(context, configProvider, beaconNameMapper)
                val result = autoInit.autoInitialize(availableFloorIds, scanDurationMs)
                
                if (result == null) {
                    Log.e(TAG, "Auto-initialization failed")
                    return@withContext false
                }
                
                // Update config
                config = result.config
                currentFloorId = result.floorId
                availableFloorIds = availableFloorIds
                
                // Initialize components
                graphModel = GraphModel(result.graph)
                observationModel = ObservationModel(
                    beacons = result.beacons,
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
                
                // Initialize HMM with detected position
                hmmEngine?.initialize(result.initialNodeId)
                
                // Initialize sensors
                beaconScanner = BeaconScanner(
                    context = context,
                    windowSize = config.bleWindowSize,
                    emaGamma = config.bleEmaGamma
                )
                // Set known beacon IDs for filtering
                beaconScanner?.setKnownBeaconIds(result.beacons.map { it.id }.toSet())
                
                imuTracker = ImuTracker(context)
                
                // Start background mapping for ALL beacons across ALL floors
                startBackgroundMapping()
                
                Log.d(TAG, "Auto-initialization successful!")
                Log.d(TAG, "Floor: ${result.floorId}, Initial node: ${result.initialNodeId}, Confidence: ${String.format("%.2f", result.confidence)}")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-initialization", e)
                false
            }
        }
    }
    
    /**
     * Initialize localization for a specific floor (manual mode)
     * 
     * @param floorId The floor to initialize localization for
     * @param initialNodeId Optional initial node ID
     * @param allFloorIds List of all available floor IDs for comprehensive beacon mapping (optional)
     */
    suspend fun initialize(floorId: Int, initialNodeId: String? = null, allFloorIds: List<Int>? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing localization for floor $floorId")
                
                // Fetch configuration
                val fetchedConfig = configProvider.fetchConfig()
                if (fetchedConfig != null) {
                    config = fetchedConfig
                }
                
                // Create beacon name mapper for automatic MAC address detection
                beaconNameMapper = BeaconNameMapper(context)
                
                // Fetch beacons with name mapping support
                val beacons = configProvider.fetchBeacons(floorId, beaconNameMapper)
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
                availableFloorIds = allFloorIds ?: listOf(floorId)
                
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
                // Set known beacon IDs for filtering
                beaconScanner?.setKnownBeaconIds(beacons.map { it.id }.toSet())
                
                imuTracker = ImuTracker(context)
                
                // Start background mapping for ALL beacons across ALL floors
                startBackgroundMapping()
                
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
        return initialize(floorId, hmmEngine?.getCurrentNode(), availableFloorIds)
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
     * Start background mapping for unmapped beacons across ALL floors
     * This ensures that any beacon from any floor will be discovered and mapped
     */
    private fun startBackgroundMapping() {
        scope.launch {
            try {
                if (availableFloorIds.isEmpty()) {
                    Log.w(TAG, "No floor IDs available for background mapping")
                    return@launch
                }
                
                // Fetch ALL beacon names from ALL floors
                Log.d(TAG, "Fetching beacon names from ${availableFloorIds.size} floors for comprehensive background mapping")
                val allBeaconNames = configProvider.fetchAllBeaconNames(availableFloorIds)
                
                if (allBeaconNames.isEmpty()) {
                    Log.d(TAG, "No beacon names to map across all floors")
                    return@launch
                }
                
                Log.d(TAG, "Found ${allBeaconNames.size} total beacon names across all floors")
                
                // Check if all beacons are already mapped
                if (beaconNameMapper?.areAllMapped(allBeaconNames) == true) {
                    Log.d(TAG, "All ${allBeaconNames.size} beacons already mapped, no background mapping needed")
                    return@launch
                }
                
                val unmappedCount = beaconNameMapper?.getUnmappedBeacons(allBeaconNames)?.size ?: allBeaconNames.size
                Log.d(TAG, "Starting background mapping for $unmappedCount unmapped beacons (across all floors)")
                
                // Start background mapper with ALL beacon names
                backgroundMapper = BackgroundBeaconMapper(context)
                backgroundMapper?.start(allBeaconNames) {
                    // Callback when mapping is complete
                    Log.d(TAG, "Background mapping complete! All ${allBeaconNames.size} beacons are now mapped.")
                    
                    // Refresh beacon list for current floor with newly mapped beacons
                    currentFloorId?.let { floorId ->
                        scope.launch {
                            refreshBeaconList(floorId)
                        }
                    }
                }
                
                Log.d(TAG, "Background beacon mapping started for all floors")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background mapping", e)
            }
        }
    }
    
    /**
     * Refresh beacon list when new mappings are discovered
     */
    private suspend fun refreshBeaconList(floorId: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Refreshing beacon list with newly mapped beacons...")
                
                // Fetch updated beacons
                val beacons = configProvider.fetchBeacons(floorId, beaconNameMapper)
                if (beacons == null || beacons.isEmpty()) {
                    Log.w(TAG, "Failed to refresh beacon list")
                    return@withContext
                }
                
                // Update observation model with new beacons
                val wasRunning = isRunning
                if (wasRunning) {
                    stop()
                }
                
                observationModel = ObservationModel(
                    beacons = beacons,
                    rankWeight = config.rankWeight,
                    pairwiseWeight = config.pairwiseWeight,
                    distanceRatioSlope = config.distanceRatioSlope
                )
                
                // Update HMM engine
                hmmEngine = HmmEngine(
                    graphModel = graphModel!!,
                    observationModel = observationModel!!,
                    transitionModel = transitionModel!!,
                    hysteresisK = config.hysteresisK,
                    searchRadiusM = config.searchRadiusM
                )
                
                // Restore HMM state
                val currentNode = _localizationState.value.currentNodeId
                hmmEngine?.initialize(currentNode)
                
                // Update beacon scanner with new beacon IDs
                beaconScanner?.setKnownBeaconIds(beacons.map { it.id }.toSet())
                
                if (wasRunning) {
                    start()
                }
                
                Log.d(TAG, "Beacon list refreshed: ${beacons.size} beacons now available")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing beacon list", e)
            }
        }
    }
    
    /**
     * Get background mapper status
     */
    fun getBackgroundMapperStatus(): BackgroundBeaconMapper.MappingStatus? {
        return backgroundMapper?.getStatus()
    }
    
    /**
     * Check if background mapping is complete
     */
    fun isBackgroundMappingComplete(): Boolean {
        return backgroundMapper?.isComplete() ?: true
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        backgroundMapper?.cleanup()
        beaconScanner?.cleanup()
        imuTracker?.cleanup()
        configProvider.cleanup()
        scope.cancel()
        
        Log.d(TAG, "Localization cleaned up")
    }
}
