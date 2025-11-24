# Critical Fixes Applied

## Issue 1: ❌ Assignment Requested on Floor Click (FIXED)

### The Problem
When user clicked a floor level in the selector, the app was requesting a NEW assignment from the backend. This should ONLY happen:
- When the top-right assignment button is clicked
- At app startup (once position is found)

### Root Cause
The `selectFloor()` function had logic that checked `hasAutoClickedAssignment` and triggered `fabAssignment.performClick()`. This was originally intended to run ONCE at startup, but it was placed inside the floor selection function, causing it to potentially trigger on manual floor changes.

### The Fix
**Removed the auto-click assignment logic from `selectFloor()` entirely.**

**Lines removed from `selectFloor()`:**
```kotlin
// Auto-click assignment button after first floor is fully loaded (only once at startup)
if (!hasAutoClickedAssignment) {
    hasAutoClickedAssignment = true
    delay(1000) // Give localization time to initialize
    withContext(Dispatchers.Main) {
        Log.d(TAG, "=== AUTO-CLICKING ASSIGNMENT BUTTON ===")
        Log.d(TAG, "currentFloor: id=${currentFloor?.id}, number=${currentFloor?.floorNumber}, name=${currentFloor?.name}")
        Log.d(TAG, "floors: ${floors.map { "id=${it.id}, num=${it.floorNumber}" }}")
        Toast.makeText(this@MainActivity, "🔄 Auto-requesting assignment...", Toast.LENGTH_LONG).show()
        fabAssignment.performClick()
    }
}
```

### Current Behavior
- ✅ Initial assignment is requested automatically when position is first found (via `requestInitialAssignment()`)
- ✅ New assignments are ONLY requested when user clicks the top-right assignment button
- ✅ Clicking floor levels does NOT trigger assignment requests

---

## Issue 2: ❌ Beacon List Only Shows Mapped Beacons (FIXED)

### The Problem
The "Nearby Beacons" dialog (top-left button) was only showing beacons that were already mapped in the system. User wanted to see **ALL Bluetooth devices** so they could tap on unmapped ones to map them.

### Root Cause
In `showNearbyBeacons()`, the code was using `knownRssi` (filtered beacon list) instead of `allRssi` (raw BLE scan data).

**Before:**
```kotlin
// Show KNOWN beacons (the ones used for localization)
val rssiMap = knownRssi
```

### The Fix
**Changed to use ALL detected BLE devices:**

**After:**
```kotlin
// Show ALL BLE devices (not just known beacons)
val rssiMap = allRssi
```

### Current Behavior
- ✅ Shows ALL Bluetooth Low Energy devices detected in the last 5 seconds
- ✅ Mapped beacons show as: `📶📶📶 [A] EC:E3:34:1A:CD:BA`
- ✅ Unmapped beacons show as: `📶📶 📍 44:1D:64:F5:B8:4E (UNMAPPED)`
- ✅ User can tap any unmapped beacon to map it with a name

---

## Issue 3: ❌ Blue Dot Greyed Out (FIXED)

### The Problem
The blue localization dot appeared slightly greyed out/translucent, especially when manual floor override was active. This made it look like the position was on a different floor.

### Root Cause
The `updateLocalizationMarker()` function was setting circle opacity to `0.8f` for the inner dot and `0.9f` for the outer stroke.

**Before:**
```kotlin
// Add inner circle layer (blue dot)
val markerLayer = CircleLayer(localizationMarkerLayerId, localizationMarkerSourceId)
    .withProperties(
        circleRadius(10f),
        circleColor("#0080FF"), // Bright blue
        circleOpacity(0.8f)
    )

// Add outer stroke layer
val strokeLayer = CircleLayer(localizationMarkerStrokeLayerId, localizationMarkerSourceId)
    .withProperties(
        circleRadius(12f),
        circleColor("#FFFFFF"), // White stroke
        circleOpacity(0.9f),
        circleStrokeWidth(2f),
        circleStrokeColor("#0080FF")
    )
```

### The Fix
**Changed both opacity values to 1.0f (full opacity):**

**After:**
```kotlin
// Add inner circle layer (blue dot)
val markerLayer = CircleLayer(localizationMarkerLayerId, localizationMarkerSourceId)
    .withProperties(
        circleRadius(10f),
        circleColor("#0080FF"), // Bright blue
        circleOpacity(1.0f)  // FULL opacity - always bright and visible
    )

// Add outer stroke layer
val strokeLayer = CircleLayer(localizationMarkerStrokeLayerId, localizationMarkerSourceId)
    .withProperties(
        circleRadius(12f),
        circleColor("#FFFFFF"), // White stroke
        circleOpacity(1.0f),  // FULL opacity
        circleStrokeWidth(2f),
        circleStrokeColor("#0080FF")
    )
```

### Current Behavior
- ✅ Blue dot is fully opaque and bright blue
- ✅ White stroke is fully opaque
- ✅ Marker is clearly visible regardless of floor or confidence
- ✅ No visual indication that suggests the dot is on a different floor

---

## Summary of Changes

### Modified Files
- **`MainActivity.kt`**
  - Removed assignment auto-click from `selectFloor()` (lines 468-479)
  - Changed beacon list to show ALL BLE devices (line 2759)
  - Changed localization marker opacity to 1.0f (lines 2197, 2208)

### Impact
- ✅ No more unexpected assignment requests when switching floors
- ✅ User can see and map ALL nearby Bluetooth devices
- ✅ Blue dot is fully visible and clear at all times

### Testing
1. **Assignment Issue**: Click different floor levels → Verify no assignment requests appear
2. **Beacon List**: Open top-left button → Verify ALL BLE devices are shown (not just mapped ones)
3. **Blue Dot**: Navigate on any floor → Verify blue dot is bright and fully opaque

---

## Assignment Flow (For Reference)

### When Assignments ARE Requested:

1. **At App Startup (Automatic)**
   - User opens app
   - Localization initializes
   - Position is found for the first time
   - `requestInitialAssignment()` is called automatically
   - Backend assigns a level

2. **Manual Request (User Action)**
   - User clicks top-right assignment button (📋)
   - `requestNewAssignment()` is called
   - Backend assigns a new level

### When Assignments Are NOT Requested:
- ❌ When clicking floor levels in the selector
- ❌ When navigating between floors
- ❌ When manual floor override is active
- ❌ When localization updates position
- ❌ When beacon scanning detects floor changes

---

## All Issues Resolved ✅

The three critical bugs have been fixed:
1. ✅ Assignment only happens at startup or button click (NEVER on floor selection)
2. ✅ ALL Bluetooth devices are shown in beacon list (not just mapped ones)
3. ✅ Blue localization dot is fully opaque and clearly visible

No compilation errors. Ready for testing.
