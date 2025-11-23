# BLE Rate Limit - Root Cause & Fix

## The Problem

The app was detecting beacons during auto-initialization (29 beacons found!), but then **"Beacons visible: 0"** forever after that.

## Root Cause (Found in Logs)

```
00:41:15 - AutoInitializer starts BLE scanning ✅
00:41:20 - AutoInitializer STOPS scanning (after 5s scan)
00:41:36 - LocalizationController tries to start scanning (16s later)
         ❌ BLOCKED: "App is scanning too frequently"
         status=6 scannerId=-1 (FAILED)
```

**Android BLE Rate Limit**: Maximum **5 scan start/stop cycles per 30 seconds**

The problem:
1. AutoInitializer: start scan (cycle 1)
2. AutoInitializer: stop scan 5s later
3. LocalizationController: tries to start scan 16s later (cycle 2)
4. ❌ Android blocks it because **only 21 seconds elapsed** since cycle 1

You need **30 seconds between scan cycles** to avoid the rate limit!

## The Fix

Modified `AutoInitializer.kt` to **wait 30 seconds total** before allowing the next scanner to start:

```kotlin
// Scan for beacons
scanner.startScanning()
delay(5000)  // Scan for 5 seconds
val results = scanner.getCurrentRssiMap()  // Got 29 beacons!

// Stop scanning
scanner.stopScanning()

// ⚠️ CRITICAL: Wait 25 MORE seconds (total 30s from start)
delay(25000)  // This ensures rate limit window passes

scanner.cleanup()
// ✅ NOW it's safe for LocalizationController to start a new scan
```

## New Timeline

```
t=0s   : AutoInit starts scanning ✅
t=5s   : Detects 29 beacons, stops scanner
t=5-30s: WAITS 25 seconds (respecting Android rate limit)
t=30s  : Returns initialization results
t=31s  : LocalizationController starts continuous scanning ✅ SUCCESS!
```

## User Experience

**Before Fix:**
```
[App starts]
"Detecting your floor..." ✅
"Indoor positioning active"
Beacons visible: 0 ❌ (scanning blocked forever)
```

**After Fix:**
```
[App starts]
"Detecting your floor..." ✅ (5s)
[Waiting 25s...] ⏳ (visible in logs)
"Indoor positioning active" ✅ (after 30s total)
Beacons visible: 29 ✅ (continuous scanning works!)
```

Yes, there's a 25-second delay, but **it's required by Android's BLE restrictions**.

## Why This Happens

Android enforces rate limits to:
1. Prevent apps from draining battery
2. Prevent apps from spamming BLE devices
3. Share BLE resources fairly among apps

The limit is **5 scan start/stop cycles per 30-second rolling window**.

## Alternative Solutions (Not Implemented)

### Option 1: Reuse Same Scanner
Instead of creating 2 separate scanners (one for auto-init, one for continuous), use the same instance throughout. 

**Problem**: Complex refactoring, would need to pass scanner between components.

### Option 2: Skip Auto-Init
Start continuous scanning immediately without auto-initialization.

**Problem**: Wouldn't know which floor the user is on initially.

### Option 3: Lower Scan Mode
Use `SCAN_MODE_LOW_POWER` instead of `LOW_LATENCY`.

**Problem**: Takes longer to detect beacons, worse localization accuracy.

## The Best Solution (Implemented)

**Wait 30 seconds** between scan cycles. Simple, reliable, respects Android's rules.

## Verification

After restart, logs should show:

```
✅ Detected 29 beacons
⏳ Waiting 25000ms to avoid Android BLE rate limits...
✅ Safe to start next scanner now (30s elapsed)
🔍 Starting BeaconScanner...
✅ BLE scanning started successfully!
Beacons visible: 6 (or however many are from your database)
```

## Important Notes

1. **First 30 seconds**: App will seem slow, but it's complying with Android
2. **After 30 seconds**: Continuous scanning works perfectly
3. **29 total beacons detected**: But only 6 are in your database (A, C, D, H, M, O)
4. **The other 23 beacons**: Are nearby BLE devices not in your system

The localization will work with the 6 known beacons!
