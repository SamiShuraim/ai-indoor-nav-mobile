# Beacon-Based Floor Detection - The Real Fix

## User Feedback
> "When the app starts, it finds my location accurately. Use the exact same logic to find my location everywhere. Your solutions are obviously not working."

**The user was 100% correct.** The auto-initialization worked perfectly because it used simple beacon matching. The continuous localization was failing because it used complex HMM-based floor detection.

## The Problem with Previous Approach

### What Didn't Work: HMM-Based Floor Detection
```kotlin
// OLD APPROACH ❌
val topNodes = localizationController.getTopNodes(5)
val floorIds = topNodes.mapNotNull { nodeToFloorMap[it] }
val detectedFloor = floorIds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
```

**Why it failed:**
1. HMM is optimized for smooth tracking WITHIN a floor
2. Transition model biases toward nearby nodes
3. Hysteresis prevents rapid changes
4. Result: Gets stuck on wrong floor

### What Works: Beacon-Based Detection (AutoInitializer Logic)
```kotlin
// WORKING APPROACH ✅
fun detectFloorFromBeacons(): Int? {
    val rssiMap = beaconScanner?.getCurrentRssiMap()
    
    // Score each floor based on visible beacons
    for ((floorId, beacons) in floorBeaconsCache) {
        val matchCount = visibleBeacons.count { it in floorBeacons }
        val mismatchCount = visibleBeacons.count { it !in floorBeacons }
        val avgRssi = matchingBeacons.average()
        
        // SAME FORMULA AS AUTOINITIALIZER
        score = matchCount * 10.0 + (avgRssi + 100) / 10.0 - mismatchCount * 2.0
    }
    
    return floorWithHighestScore
}
```

**Why it works:**
1. Direct measurement of which beacons are visible RIGHT NOW
2. No bias toward previous floor
3. Works identically at startup and during continuous tracking
4. Simple, reliable, proven

## The Fix

### 1. Added Beacon-Based Floor Detection (`LocalizationController.kt`)
```kotlin
// Floor detection (beacon-based, like AutoInitializer)
private var floorBeaconsCache: Map<Int, List<LocalizationBeacon>> = emptyMap()

fun detectFloorFromBeacons(): Int? {
    val rssiMap = beaconScanner?.getCurrentRssiMap() ?: return null
    if (rssiMap.isEmpty()) return null
    
    val visibleBeaconIds = rssiMap.keys
    val floorScores = mutableMapOf<Int, Double>()
    
    // Score each floor based on beacon matches (EXACTLY like AutoInitializer)
    for ((floorId, beacons) in floorBeaconsCache) {
        val floorBeaconIds = beacons.map { it.id }.toSet()
        
        val matchCount = visibleBeaconIds.count { it in floorBeaconIds }
        val mismatchCount = visibleBeaconIds.count { it !in floorBeaconIds }
        val avgRssi = if (matchCount > 0) {
            visibleBeaconIds
                .filter { it in floorBeaconIds }
                .mapNotNull { rssiMap[it] }
                .average()
        } else {
            -100.0
        }
        
        // SAME formula as AutoInitializer
        val score = matchCount * 10.0 + (avgRssi + 100) / 10.0 - mismatchCount * 2.0
        floorScores[floorId] = score
    }
    
    return floorScores.maxByOrNull { it.value }?.key
}
```

### 2. Cache Beacons During Initialization
```kotlin
// In autoInitialize() and initialize()
val beaconsByFloor = mutableMapOf<Int, List<LocalizationBeacon>>()
for (fId in floorIds) {
    val floorBeacons = configProvider.fetchBeacons(fId, beaconNameMapper)
    if (floorBeacons != null) {
        beaconsByFloor[fId] = floorBeacons
    }
}
floorBeaconsCache = beaconsByFloor
```

### 3. Use Beacon-Based Detection in MainActivity
```kotlin
// OLD: Complex HMM-based detection ❌
val topNodes = localizationController.getTopNodes(5)
val floorIds = topNodes.mapNotNull { nodeToFloorMap[it] }
val detectedFloorId = floorIds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

// NEW: Simple beacon-based detection ✅
val detectedFloorId = localizationController.detectFloorFromBeacons()
```

### 4. Simplified Floor Switching
```kotlin
// No more confidence thresholds - beacon detection is reliable!
if (detectedFloorId != null && detectedFloorId != currentDisplayedFloorId) {
    Log.d(TAG, "🚶 FLOOR CHANGED: $currentDisplayedFloorId → $detectedFloorId")
    
    val newFloor = floors.find { it.id == detectedFloorId }
    if (newFloor != null) {
        withContext(Dispatchers.Main) {
            selectFloor(newFloor)
            Toast.makeText(this@MainActivity, "🚶 ${newFloor.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

## How It Works Now

### At Startup
1. AutoInitializer scans beacons → Determines floor → Finds initial node ✅
2. Caches beacons by floor for continuous use

### During Continuous Tracking  
1. **Position Tracking**: HMM finds position within current floor (what it's good at)
2. **Floor Detection**: Beacon matching determines which floor you're on (proven method)
3. **Floor Switching**: When detected floor changes, UI switches immediately

### Floor Transitions
1. Walk from Floor 1 → Floor 2
2. Beacon scanner sees Floor 2 beacons
3. `detectFloorFromBeacons()` scores:
   - Floor 1: 3 matches, low RSSI → score = 35
   - Floor 2: 5 matches, high RSSI → score = 58
4. Detected floor = 2
5. UI switches to Floor 2
6. HMM continues tracking position (now showing Floor 2 nodes)

## Key Insight

**The HMM is great for tracking position WITHIN a floor, but terrible at detecting WHICH floor you're on.**

Solution: Use each component for what it's good at:
- **Beacon matching** → Floor detection
- **HMM** → Position tracking within floor

## Expected Behavior

| Scenario | Result |
|----------|--------|
| Start on Floor 1 | Detects Floor 1 correctly ✅ |
| Walk around Floor 1 | Stays on Floor 1 ✅ |
| Walk Floor 1 → 2 | Switches to Floor 2 when beacons change ✅ |
| Walk Floor 2 → 1 | Switches to Floor 1 when beacons change ✅ |
| Stand at boundary | Switches based on strongest beacon signal ✅ |
| Blue dot | Always visible ✅ |

## Files Modified

1. **`LocalizationController.kt`**
   - Added `floorBeaconsCache` field
   - Added `detectFloorFromBeacons()` method (copies AutoInitializer logic)
   - Cache beacons during `autoInitialize()` and `initialize()`

2. **`MainActivity.kt`**
   - Replaced HMM-based floor detection with beacon-based detection
   - Simplified floor switching logic (removed confidence thresholds)

## Why This Will Work

1. **Uses proven method**: Same logic that works at startup
2. **Direct measurement**: Based on actual visible beacons, not inferred from HMM
3. **No hysteresis**: Floor switches immediately when beacons indicate change
4. **Simple and reliable**: No complex probability calculations

The user was right to call out that the startup method worked. Now we use that same method everywhere.
