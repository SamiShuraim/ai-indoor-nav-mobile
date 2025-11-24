# Continuous Beacon Mapping Implementation

## Overview

Implemented a continuous beacon mapping system that discovers and maps beacon names to MAC addresses over time, using local storage to persist mappings across app restarts. This solves the problem where beacons not nearby during initialization were never mapped.

## Problem Statement

Previously, beacon name-to-MAC address mapping only happened once at initialization with a 5-second timeout. If beacons weren't nearby during that initial scan, they would never be mapped, limiting the system's ability to track all beacons.

## Solution

### 1. **BeaconMappingCache** - Local Persistent Storage
**File:** `BeaconMappingCache.kt`

A SharedPreferences-based cache that stores beacon name-to-MAC address mappings persistently.

**Key Features:**
- Saves mappings to local storage immediately when discovered
- Retrieves cached mappings on app restart
- Provides utility methods to check mapping status
- Tracks last update timestamp

**Methods:**
- `saveMappings()` - Save multiple mappings at once
- `saveMapping()` - Save a single mapping immediately
- `getMappings()` - Get all cached mappings
- `isMapped()` - Check if a beacon is mapped
- `getUnmappedBeacons()` - Get list of unmapped beacons
- `areAllMapped()` - Check if all beacons are mapped

### 2. **Enhanced BeaconNameMapper** - Smart Caching
**File:** `BeaconNameMapper.kt` (Updated)

Enhanced to use the cache before scanning, significantly reducing scan time and resource usage.

**Key Changes:**
- Now checks cache first before scanning
- Only scans for unmapped beacons
- Saves new discoveries to cache immediately
- Reduced initial scan timeout from 5s to 3s (most will be cached)

**New Methods:**
- `getCachedMappings()` - Get cached mappings
- `areAllMapped()` - Check if all beacons are mapped
- `getUnmappedBeacons()` - Get list of unmapped beacons
- `clearCache()` - Clear all cached mappings

### 3. **BackgroundBeaconMapper** - Continuous Discovery
**File:** `BackgroundBeaconMapper.kt` (New)

A background service that continuously scans for unmapped beacons using low-power BLE scanning.

**Key Features:**
- Runs continuously until all beacons are mapped
- Uses LOW_POWER scan mode to minimize battery impact
- Automatically stops when all beacons are mapped
- Provides progress callbacks
- Saves mappings immediately when discovered

**Methods:**
- `start()` - Start background mapping with completion callback
- `stop()` - Stop background mapping
- `getStatus()` - Get current mapping progress
- `isComplete()` - Check if all beacons are mapped

**MappingStatus Data Class:**
```kotlin
data class MappingStatus(
    val isRunning: Boolean,
    val totalBeacons: Int,
    val mappedBeacons: Int,
    val unmappedBeacons: List<String>,
    val discoveredInSession: List<String>,
    val isComplete: Boolean
)
```

### 4. **ConfigProvider Updates** - Partial Mapping Support
**File:** `ConfigProvider.kt` (Updated)

Enhanced to handle partial mappings gracefully and support background discovery.

**Key Changes:**
- Loads cached mappings first
- Returns partial beacon list (mapped beacons only)
- Shorter initial scan timeout (3s instead of 5s)
- New method `fetchBeaconNames()` to get all beacon names for background mapping

**Behavior:**
- Initial load is faster (uses cache + quick scan)
- Background mapper continues finding remaining beacons
- Localization works with partial beacon list initially
- System improves over time as more beacons are discovered

### 5. **LocalizationController Integration**
**File:** `LocalizationController.kt` (Updated)

Integrated background mapping into the main localization workflow.

**Key Changes:**
- Automatically starts background mapping after initialization
- Refreshes beacon list when new mappings are discovered
- Provides status methods for monitoring progress
- Cleans up background mapper on shutdown

**New Methods:**
- `startBackgroundMapping()` - Private method to start background discovery
- `refreshBeaconList()` - Updates observation model with newly discovered beacons
- `getBackgroundMapperStatus()` - Get mapping progress
- `isBackgroundMappingComplete()` - Check if mapping is complete

## Workflow

### Initial App Launch (No Cache)

1. **Initialization:**
   - ConfigProvider tries to load cached mappings → Empty
   - Performs quick 3-second scan for nearby beacons
   - Returns partial beacon list with only mapped beacons
   - Localization starts with available beacons

2. **Background Mapping:**
   - BackgroundBeaconMapper starts automatically
   - Continuously scans in LOW_POWER mode
   - Saves new mappings to cache immediately
   - When complete, refreshes beacon list

3. **Result:**
   - User can start using localization immediately
   - System discovers more beacons over time
   - Localization accuracy improves as more beacons are mapped

### Subsequent App Launches (With Cache)

1. **Initialization:**
   - ConfigProvider loads cached mappings → Most/all beacons found
   - Quick 3-second scan only for any unmapped beacons
   - Returns nearly complete beacon list immediately
   - Localization starts with full accuracy

2. **Background Mapping:**
   - If all beacons cached → Background mapper doesn't start
   - If some unmapped → Background mapper continues until complete
   - New beacons added to cache

3. **Result:**
   - Near-instant initialization with cached data
   - No expensive re-scanning
   - Full localization accuracy from the start

## Benefits

### Performance
- **Faster initialization:** Uses cached mappings (instant) instead of 5s scan
- **Reduced scan time:** Only scans for unmapped beacons
- **Lower battery usage:** Background mapper uses LOW_POWER mode
- **No blocking:** Localization starts immediately with partial data

### Reliability
- **Complete coverage:** Eventually maps all beacons, even those far away
- **Persistent mappings:** Cache survives app restarts
- **Graceful degradation:** Works with partial beacon list
- **Automatic improvement:** System gets better over time

### User Experience
- **Faster app startup:** No waiting for complete beacon scan
- **Immediate functionality:** Localization works right away
- **Improving accuracy:** Localization gets better as user moves around
- **Transparent operation:** Background mapping is invisible to user

## Configuration

### Scan Settings

**Initial Scan (BeaconNameMapper):**
- Duration: 3 seconds (reduced from 5s)
- Mode: LOW_LATENCY
- Saves to cache immediately

**Background Scan (BackgroundBeaconMapper):**
- Duration: Continuous until complete
- Mode: LOW_POWER (battery-friendly)
- Progress check: Every 30 seconds

### Storage

**SharedPreferences:**
- Key: `beacon_mappings`
- Format: JSON map of beacon name → MAC address
- Persists across app restarts

## Monitoring

### Check Background Mapping Status

```kotlin
val status = localizationController.getBackgroundMapperStatus()
if (status != null) {
    Log.d(TAG, "Progress: ${status.mappedBeacons}/${status.totalBeacons}")
    Log.d(TAG, "Unmapped: ${status.unmappedBeacons}")
    Log.d(TAG, "Complete: ${status.isComplete}")
}
```

### Check if Mapping is Complete

```kotlin
val isComplete = localizationController.isBackgroundMappingComplete()
```

## Technical Details

### Thread Safety
- Uses `ConcurrentHashMap` for thread-safe beacon storage
- `AtomicBoolean` for state management
- Coroutines for asynchronous operations

### Memory Management
- Mappings stored in SharedPreferences (persistent)
- Background mapper uses minimal memory
- Automatic cleanup on app shutdown

### Battery Impact
- Initial scan: LOW_LATENCY (3s only)
- Background scan: LOW_POWER mode
- Automatically stops when complete
- No unnecessary re-scanning

## Files Created/Modified

### New Files
1. `BeaconMappingCache.kt` - Persistent storage for mappings
2. `BackgroundBeaconMapper.kt` - Continuous background scanner

### Modified Files
1. `BeaconNameMapper.kt` - Added caching support
2. `ConfigProvider.kt` - Enhanced to use cache and support partial mappings
3. `LocalizationController.kt` - Integrated background mapping

## Testing Recommendations

1. **First Launch Test:**
   - Clear app data
   - Launch app and initialize localization
   - Verify background mapper starts
   - Move to different locations
   - Verify new beacons are discovered and cached

2. **Subsequent Launch Test:**
   - Force close app
   - Relaunch app
   - Verify cached mappings are used
   - Verify faster initialization

3. **Edge Cases:**
   - Test with Bluetooth disabled
   - Test with no beacons nearby
   - Test with partial beacon coverage
   - Verify graceful degradation

## Future Enhancements

1. **Cache Expiration:**
   - Add TTL for cached mappings
   - Invalidate old mappings periodically

2. **Smart Scanning:**
   - Increase scan interval after initial mapping
   - Use geolocation to prioritize certain beacons

3. **UI Integration:**
   - Show mapping progress in UI
   - Allow manual refresh
   - Display cache statistics

4. **Analytics:**
   - Track discovery rates
   - Measure battery impact
   - Monitor cache hit rates
