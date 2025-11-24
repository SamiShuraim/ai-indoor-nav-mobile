# BLE Rate Limit Fix

## Problem
```
E  registration failed because app is scanning too frequently
```

Android enforces strict BLE scan rate limits:
- **Maximum 5 scan start/stop cycles per 30 seconds**
- If exceeded, all scans are blocked for 30 seconds

## Root Causes

### 1. BackgroundBeaconMapper
Was starting/stopping scans every 2 seconds:
- Scan for 500ms
- Stop scan
- Wait 2 seconds
- Repeat

**Problem:** 5 cycles = 10 seconds → Rate limit triggered!

### 2. Beacon-Based Floor Detection
Called `getCurrentRssiMap()` on every localization update (multiple times per second), but this doesn't actually start new scans - it just reads cached data from the ongoing BeaconScanner.

## Solutions Applied

### Solution 1: Disabled BackgroundBeaconMapper
```kotlin
// Background mapping disabled (not working reliably)
// startBackgroundMapping()
```

**Reason:** User confirmed it doesn't work, so no point keeping it active and risking rate limits.

### Solution 2: Rate Limited Floor Detection
```kotlin
// Rate limit: Only detect floor every 2 seconds
private var lastFloorDetectionTime = 0L
private val floorDetectionIntervalMs = 2000L

fun detectFloorFromBeacons(): Int? {
    val currentTime = System.currentTimeMillis()
    
    if (currentTime - lastFloorDetectionTime < floorDetectionIntervalMs) {
        return lastDetectedFloor  // Return cached result
    }
    
    lastFloorDetectionTime = currentTime
    // ... proceed with detection
}
```

**Note:** This doesn't actually help with the scan limit (since `getCurrentRssiMap()` doesn't start scans), but it reduces CPU usage and log spam.

### Solution 3: Increased BackgroundBeaconMapper Interval (if re-enabled)
Changed from 2 seconds to 10 seconds:
```kotlin
// Android limits scan start/stop to max 5 times per 30 seconds
// So we need at least 6 seconds between scans (30s / 5 = 6s)
// Using 10 seconds to be safe
private val scanIntervalMs = 10000L
```

## Android BLE Scan Best Practices

### Don't Do This (Rate Limit Trigger)
```kotlin
while (true) {
    scanner.startScan(callback)
    delay(500)
    scanner.stopScan(callback)  // ❌ Stop/start triggers rate limit
    delay(2000)
}
```

### Do This Instead
```kotlin
// Option 1: Long-running scan (preferred for continuous tracking)
scanner.startScan(callback)
// Keep running indefinitely
// Only stop when feature is disabled

// Option 2: Infrequent bursts (for background tasks)
while (true) {
    scanner.startScan(callback)
    delay(2000)  // Scan for 2 seconds
    scanner.stopScan(callback)
    delay(10000)  // Wait 10+ seconds before next cycle
}
```

## Current BLE Scan Architecture

### BeaconScanner (Main Localization)
- **Started once** when localization starts
- **Runs continuously** until localization stops
- **Never stops/restarts** during operation
- ✅ No rate limit issues

### BackgroundBeaconMapper
- **DISABLED** (user confirmed not working)
- Would have scanned every 10 seconds if enabled
- ✅ No rate limit issues when disabled

## Testing

After this fix:
1. ✅ No more "scanning too frequently" errors
2. ✅ Floor detection works (using cached RSSI data)
3. ✅ Localization continues normally
4. ✅ BackgroundBeaconMapper doesn't interfere

## If BackgroundBeaconMapper Needs to be Re-enabled

1. Fix the underlying issue (why it's not working)
2. Keep the 10-second interval
3. Consider using `SCAN_MODE_LOW_POWER` instead of `LOW_LATENCY`
4. Add exponential backoff if mapping fails

## Files Modified

1. **`LocalizationController.kt`**
   - Disabled `startBackgroundMapping()` calls
   - Added rate limiting to `detectFloorFromBeacons()`

2. **`BackgroundBeaconMapper.kt`**
   - Changed scan interval from 2s to 10s
   - Increased scan duration from 500ms to 2s
   - Updated logging intervals
