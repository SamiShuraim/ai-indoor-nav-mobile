# Floor Detection Bug Fix

## Problem Report
User reported: "Stuck on the third floor even though I'm on the first and there are no beacons on the third floor. However, the blue dot is correct. When I went to the second floor, blue dot is somewhat correct, but still stuck on third floor."

## Root Cause Analysis

### Bug #1: Initial Node Selection from Wrong Floor
**Location:** `AutoInitializer.kt` - `estimateInitialNode()` method

**The Problem:**
```kotlin
// OLD CODE (BUGGY)
// Step 5: Estimate initial node (use beacons from detected floor only for initial position)
val detectedFloorBeacons = floorBeacons[floorId]!!
val (initialNode, confidence) = estimateInitialNode(rssiMap, detectedFloorBeacons, graph)
```

The `graph` parameter was the **combined graph** containing nodes from ALL floors, but `detectedFloorBeacons` only had beacons from the detected floor.

**Why This Caused the Bug:**
1. Auto-initialization correctly detected Floor 1 based on visible beacons
2. But `estimateInitialNode()` scored **ALL nodes from ALL floors** (including Floor 3)
3. A node from Floor 3 could win if it had similar geometry to Floor 1
4. Result: System initialized on Floor 3 even though user was on Floor 1

**The Fix:**
```kotlin
// NEW CODE (FIXED)
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
```

Now the initial node is **guaranteed** to be from the correct floor.

### Bug #2: Floor Detection Used Stale Data
**Location:** `LocalizationController.kt` - `getTopNodes()` method

**The Problem:**
```kotlin
// OLD CODE (BUGGY)
fun getTopNodes(count: Int = 3): List<String> {
    val state = _localizationState.value
    val topPosteriors = hmmEngine?.getTopPosteriors(
        state.debug?.topPosteriors?.associate { it.first to it.second } ?: emptyMap(),
        count
    ) ?: emptyList()
    
    return topPosteriors.map { it.first }
}
```

This was getting posteriors from `state.debug?.topPosteriors` which could be:
- Stale (from a previous update)
- Incomplete
- Not sorted correctly

**The Fix:**
```kotlin
// NEW CODE (FIXED)
fun getTopNodes(count: Int = 3): List<String> {
    // Get top nodes directly from HMM engine's current posteriors
    return hmmEngine?.getTopNodes(count) ?: emptyList()
}
```

Added a new method in `HmmEngine.kt`:
```kotlin
/**
 * Get top N most likely nodes based on current posteriors
 */
fun getTopNodes(n: Int = 3): List<String> {
    return logPosteriorPrev.entries
        .sortedByDescending { it.value }
        .take(n)
        .map { it.key }
}
```

Now floor detection uses **live, accurate** data directly from the HMM engine.

## Additional Improvements

### 1. Enhanced Logging for Debugging
Added comprehensive logging in `MainActivity.kt`:
```kotlin
Log.d(TAG, "🔍 Floor Detection:")
Log.d(TAG, "  Top 5 nodes: $topNodes")
Log.d(TAG, "  Floor IDs: $floorIds")
Log.d(TAG, "  Floor counts: $floorCounts")
Log.d(TAG, "  Detected floor: $mostCommonFloor")
Log.d(TAG, "  Current displayed floor: $currentDisplayedFloorId")
```

Added logging in `AutoInitializer.kt`:
```kotlin
val score = score.matchCount * 10.0 + (score.avgRssi + 100) / 10.0 - score.mismatchCount * 2.0
Log.d(TAG, "Floor $floorId: $matchCount matches, $mismatchCount mismatches, avg RSSI: ${String.format("%.1f", avgRssi)}, SCORE: ${String.format("%.2f", score)}")
Log.d(TAG, "🎯 SELECTED FLOOR: $selectedFloor")
```

### 2. Confidence-Based Floor Switching
Added a confidence threshold to prevent spurious floor changes:
```kotlin
// Only switch if we have strong evidence (>= 60% of nodes from target floor)
val floorChangeConfidence = if (totalCount > 0) targetFloorCount.toDouble() / totalCount else 0.0

if (floorChangeConfidence >= 0.6) {  // At least 3 out of 5 nodes
    Log.d(TAG, "🚶 FLOOR CHANGED: $currentDisplayedFloorId → $detectedFloorId (confidence: ${String.format("%.1f%%", floorChangeConfidence * 100)})")
    // ... switch floor ...
} else {
    Log.d(TAG, "⏸️ Floor change pending: $currentDisplayedFloorId → $detectedFloorId (confidence too low: ${String.format("%.1f%%", floorChangeConfidence * 100)})")
}
```

This prevents flickering between floors when posteriors are uncertain.

### 3. Increased Sample Size
Changed from top 3 nodes to top 5 nodes for floor detection:
```kotlin
val topNodes = localizationController.getTopNodes(5)  // Get top 5 for better accuracy
```

More samples = more robust floor detection.

## Testing Instructions

### 1. Check Initialization
1. Start app on Floor 1
2. Check logs for: `✅ AUTO-INIT: Determined floor: 1`
3. Check logs for: `✅ AUTO-INIT: Initial node=XXX on floor 1`
4. Verify UI shows Floor 1

### 2. Check Floor Detection
1. Walk around on Floor 1
2. Watch logs for floor detection output
3. Verify "Detected floor: 1" appears consistently
4. Verify "Top 5 nodes" are from Floor 1

### 3. Check Floor Transitions
1. Walk from Floor 1 to Floor 2 (via stairs)
2. Watch logs for:
   - Floor counts shifting: `{1=4, 2=1}` → `{1=2, 2=3}` → `{2=5}`
   - Confidence increasing: `confidence too low: 40.0%` → `confidence: 80.0%`
   - Floor change: `🚶 FLOOR CHANGED: 1 → 2`
3. Verify UI switches to Floor 2
4. Verify blue dot stays visible throughout

### 4. Check Edge Cases
1. Stand at stairwell boundary (between floors)
2. Verify system doesn't flicker between floors
3. Logs should show: `⏸️ Floor change pending` when confidence is low
4. Only switches when confidence reaches 60%

## Expected Behavior Now

| Scenario | Expected Result |
|----------|----------------|
| Start on Floor 1 | Initializes on Floor 1, shows Floor 1 |
| Start on Floor 2 | Initializes on Floor 2, shows Floor 2 |
| Walk Floor 1 → 2 | Detects change, switches to Floor 2 |
| Walk Floor 2 → 1 | Detects change, switches to Floor 1 |
| Stand at boundary | Waits for 60% confidence before switching |
| Blue dot | Always visible with correct position |

## Files Modified

1. **`AutoInitializer.kt`**
   - Fixed initial node selection to only consider nodes from detected floor
   - Added detailed logging for floor detection scores

2. **`LocalizationController.kt`**
   - Fixed `getTopNodes()` to use live HMM data instead of stale debug state

3. **`HmmEngine.kt`**
   - Added `getTopNodes(n: Int)` method to directly access current posteriors
   - Added `getCurrentPosteriors()` for debugging

4. **`MainActivity.kt`**
   - Enhanced floor detection logging
   - Added confidence-based floor switching
   - Increased sample size from 3 to 5 nodes
   - Added warning logs for nodes without floor mappings

## Why This Fix Works

1. **Correct Initialization**: System now starts on the correct floor because initial node is constrained to detected floor nodes
2. **Accurate Detection**: Floor detection uses live HMM posteriors instead of stale debug data
3. **Robust Switching**: Confidence threshold prevents spurious floor changes
4. **Better Logging**: Comprehensive logs make debugging easier

The root cause was that the initial node selection was considering ALL nodes from ALL floors, not just the detected floor. Combined with stale debug data, this caused the system to get stuck on the wrong floor.
