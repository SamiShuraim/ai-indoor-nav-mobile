# On-Device Indoor Localization System

## Overview

This module implements a **real-time, on-device indoor localization system** using:
- BLE beacon RSSI measurements
- IMU sensors (step detection + heading)
- Graph-based HMM (Hidden Markov Model)
- Zero-calibration observation model (rank & pairwise-based)
- Forward-biased, speed-gated transitions with hysteresis

The system works **offline** after initial data synchronization and requires **no per-site calibration**.

---

## Architecture

### Package Structure

```
com.KFUPM.ai_indoor_nav_mobile.localization/
├── models/
│   └── LocalizationModels.kt       # Data models
├── BeaconScanner.kt                # BLE scanning with RSSI smoothing
├── ImuTracker.kt                   # Step detection + heading
├── GraphModel.kt                   # Node/edge graph management
├── ObservationModel.kt             # Rank & pairwise log-likelihoods
├── TransitionModel.kt              # Speed gating + forward bias
├── HmmEngine.kt                    # Online Viterbi + hysteresis
├── ConfigProvider.kt               # Data fetching & caching
└── LocalizationController.kt       # Main orchestrator (public API)
```

### Core Components

#### 1. **BeaconScanner**
- Scans BLE beacons at ~1 Hz
- Maintains a sliding window (last 3-5 samples)
- Applies median + EMA smoothing per beacon
- Outputs: `Map<BeaconId, SmoothedRSSI>`

#### 2. **ImuTracker**
- Uses Android step detector sensor
- Computes heading from rotation vector (or accelerometer + magnetometer)
- Applies EMA smoothing to heading
- Debounces rapid step events
- Outputs: `ImuData(steps, headingRad)`

#### 3. **GraphModel**
- Stores nodes and edges
- Provides neighbor lookup, edge directions, distances
- Validates graph integrity

#### 4. **ObservationModel**
- **Rank-based**: Computes Spearman's ρ between distance ranks and RSSI ranks
- **Pairwise**: Scores all beacon pairs using logistic consistency
- Combines both into a log-likelihood for each candidate node
- Optional: Online self-calibration (very slow learning rate)

#### 5. **TransitionModel**
- **Speed gating**: Disallows transitions faster than max walking speed (1.8 m/s)
- **Forward bias**: Uses IMU heading + edge direction to prefer aligned transitions
- **Self-transition**: Higher weight when no steps detected (stickiness)
- Outputs: Log transition probabilities

#### 6. **HmmEngine**
- Implements online Viterbi algorithm
- Maintains log posteriors over nodes
- **Hysteresis**: Requires K=2 consecutive ticks before committing node change
- Tracks path history
- Outputs: Current node ID, confidence, top posteriors

#### 7. **ConfigProvider**
- Fetches beacons, graph, and config from API
- Checks for config version updates

#### 8. **LocalizationController** (Main API)
- Orchestrates all components
- Runs update loop at configurable tick rate (default 1 Hz)
- Exposes `StateFlow<LocalizationState>` for UI consumption

---

## Usage

### 1. Initialization

```kotlin
import com.KFUPM.ai_indoor_nav_mobile.localization.LocalizationController

val localizationController = LocalizationController(context)

// Initialize for a specific floor
lifecycleScope.launch {
    val success = localizationController.initialize(
        floorId = 1,
        initialNodeId = "node_entrance" // Optional
    )
    
    if (success) {
        // Start localization
        localizationController.start()
    }
}
```

### 2. Observing State

```kotlin
// Collect localization state in your Activity/Fragment
lifecycleScope.launch {
    localizationController.localizationState.collect { state ->
        // Update UI
        val nodeId = state.currentNodeId
        val confidence = state.confidence
        val pathHistory = state.pathHistory
        
        Log.d("Localization", "Current node: $nodeId (confidence: $confidence)")
        
        // Debug info (if available)
        state.debug?.let { debug ->
            Log.d("Debug", "Visible beacons: ${debug.visibleBeaconCount}")
            Log.d("Debug", "Top posteriors: ${debug.topPosteriors}")
            Log.d("Debug", "Tick duration: ${debug.tickDurationMs}ms")
        }
    }
}
```

### 3. Get Current Position

```kotlin
val position = localizationController.getCurrentPosition()
if (position != null) {
    val (x, y) = position
    Log.d("Position", "Current position: ($x, $y)")
}
```

### 4. Stop and Clean Up

```kotlin
override fun onDestroy() {
    super.onDestroy()
    localizationController.stop()
    localizationController.cleanup()
}
```

### 5. Check for Updates

```kotlin
lifecycleScope.launch {
    val hasUpdate = localizationController.checkForUpdates()
    if (hasUpdate) {
        // Reload config
        localizationController.reload()
    }
}
```

---

## Configuration Parameters

All parameters can be configured via `LocalizationConfig`. Defaults are shown below:

### BLE Parameters
```kotlin
bleWindowSize = 5              // Number of samples in smoothing window
bleEmaGamma = 0.5              // EMA smoothing factor
```

### Observation Model
```kotlin
rankWeight = 3.0               // α: Weight for rank correlation
pairwiseWeight = 1.0           // β: Weight for pairwise consistency
distanceRatioSlope = 8.0       // κ: Distance-ratio slope (set to 0 for pure rank)
```

### Transition Model
```kotlin
forwardBiasLambda = 1.5        // λ: Forward bias strength
maxWalkingSpeed = 1.8          // m/s: Maximum feasible speed
```

### HMM
```kotlin
hysteresisK = 2                // Consecutive ticks before node change
tickRateHz = 1.0               // Update frequency (Hz)
searchRadiusM = 25.0           // Search radius for large graphs
```

### Calibration
```kotlin
calibrationLearningRate = 0.01         // η: Very slow online learning
calibrationConfidenceThreshold = 0.9   // Only calibrate when confident
```

---

## Tuning Guide

### When to Adjust Parameters

#### **High Noise / Low Beacon Density**
- Increase `bleWindowSize` to 7-10 for more smoothing
- Reduce `rankWeight` (α) to 2.0
- Increase `hysteresisK` to 3

#### **Dense Beacon Deployment**
- Increase `rankWeight` (α) to 4.0
- Enable pairwise by keeping `distanceRatioSlope` (κ) at 8.0

#### **Fast Walking / Running**
- Increase `maxWalkingSpeed` to 3.0 m/s
- Reduce `hysteresisK` to 1 for faster response

#### **Slow/Stationary Movement**
- Current settings are optimized for walking
- Hysteresis naturally handles stationary users

#### **No Compass / Poor Heading**
- Reduce `forwardBiasLambda` (λ) to 0.5 or 0.0
- System will rely more on RSSI observations

#### **Junction Ambiguity**
- Increase `pairwiseWeight` (β) to 2.0
- Use more beacons near junctions
- Temporarily increase `hysteresisK` to 3

---

## Performance Considerations

### Target Performance
- **Tick duration**: <10 ms for graphs up to 500 nodes
- **Tick rate**: 1 Hz (adjustable to 0.5 Hz for battery saving)
- **Memory**: ~5-10 MB for typical floor (100 nodes, 200 edges, 20 beacons)

### Optimization for Large Graphs
The `searchRadiusM` parameter restricts HMM candidates to nodes within 25m of the current node. For graphs >500 nodes, this prevents performance degradation.

### Battery Impact
- BLE scanning: Low impact at 1 Hz
- IMU tracking: Minimal (sensor fusion is efficient)
- HMM updates: <1% CPU at 1 Hz

To reduce battery usage:
- Lower `tickRateHz` to 0.5 Hz
- Stop localization when app is backgrounded
- Use `SCAN_MODE_LOW_POWER` in production

---

## Offline Operation

After initial data fetch, all computation happens on-device:

1. All computation happens on-device
2. No network requests during localization operation
3. Requires network connectivity for initial beacon and graph data

```kotlin
// Initialize localization for a floor
val success = localizationController.initialize(floorId = 1)
// Fetches beacon and graph data from API
```

---

## Error Handling

### Insufficient Beacons
If <2 beacons are visible:
- Confidence drops to <0.4
- Current node is frozen (no jumps)
- System waits for more beacons

```kotlin
localizationController.localizationState.collect { state ->
    if (state.confidence < 0.4) {
        // Show "weak signal" warning
    }
}
```

### Sensor Failures
- **No step detector**: System still works using RSSI only
- **No heading**: Reduce forward bias (system relies more on RSSI)
- **Bluetooth disabled**: Prompt user to enable

---

## API Endpoints

The system expects the following API endpoints (adjust in `ConfigProvider.kt`):

### Beacons
```
GET /api/Beacon?floor={floorId}
Response: List<Beacon> (with x, y coordinates)
```

### Graph
```
GET /api/Graph?floor={floorId}
Response: GraphResponse {
  version: String
  nodes: [{ id, x, y }, ...]
  edges: [{ from, to, length_m, forward_bias? }, ...]
}
```

### Config
```
GET /api/LocalizationConfig
Response: ConfigResponse {
  version: String
  config: { ...tunable parameters... }
}

GET /api/LocalizationConfig/version
Response: "v1.2.3"
```

---

## Testing

### Unit Tests
Run unit tests:
```bash
./gradlew test
```

Test files:
- `ObservationModelTest.kt`
- `TransitionModelTest.kt`
- `HmmEngineTest.kt`
- `GraphModelTest.kt`

### Acceptance Tests

#### 1. Static Test
- Place phone stationary near a node for 30s
- Expected: Single committed node, confidence >0.8, no flips

#### 2. Corridor Walk
- Walk straight through 5 adjacent nodes
- Expected: Monotonic progress, ≤1 hesitation, avg tick <20ms

#### 3. Junction Test
- Approach T-junction, pause 2s, choose right
- Expected: <3s ambiguity, then correct branch commitment

#### 4. Low Signal Test
- Cover 1-2 beacons
- Expected: Confidence drops, holds last node, no wild jumps

#### 5. Offline Test
- Initialize, then enable airplane mode
- Expected: Continues operating normally

---

## Permissions

Required permissions (already in `AndroidManifest.xml`):

```xml
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Location (required for BLE scanning on Android <12) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Step detection (optional but recommended) -->
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
```

Request permissions at runtime:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    requestPermissions(
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ),
        REQUEST_CODE
    )
} else {
    requestPermissions(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        REQUEST_CODE
    )
}

// For step detection (Android 10+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    requestPermissions(
        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
        REQUEST_CODE
    )
}
```

---

## Troubleshooting

### Issue: Localization not starting
**Solution:**
- Check Bluetooth is enabled
- Verify permissions are granted
- Ensure `initialize()` completed successfully
- Check logs for errors

### Issue: Confidence always low
**Possible causes:**
- Too few beacons visible (<3)
- Beacons not in database
- Wrong floor selected
- RSSI values contradictory

**Solution:**
- Verify beacon IDs match database
- Check beacon coordinates are correct
- Increase `bleWindowSize` for more smoothing

### Issue: Wrong node committed
**Possible causes:**
- Beacon positions incorrect
- Graph topology doesn't match physical layout
- Too much RSSI noise

**Solution:**
- Verify beacon and node coordinates
- Increase `hysteresisK` to 3
- Check graph edges are correct

### Issue: Performance slow (tick >50ms)
**Possible causes:**
- Large graph (>500 nodes)
- Too many beacons

**Solution:**
- Reduce `searchRadiusM` to 15m
- Lower `tickRateHz` to 0.5 Hz
- Optimize graph (remove redundant nodes)

---

## Future Enhancements

Potential improvements (not yet implemented):

1. **Edge discretization**: Treat edge segments as states for smoother positioning
2. **Particle filter**: Alternative to Viterbi for continuous positioning
3. **Multi-floor support**: Detect floor changes using barometer
4. **Beacon health monitoring**: Track battery levels, report outages
5. **Crowd-sourced calibration**: Aggregate RSSI data from many users

---

## License

This module is part of the KFUPM AI Indoor Navigation Mobile app.

---

## Contact

For questions or issues, contact the development team.
