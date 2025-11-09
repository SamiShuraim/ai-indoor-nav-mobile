# Localization Blue Dot Implementation

## Overview
Added a separate blue dot marker on the map to visualize the indoor localization position (which node the system determines the user is at based on RSSI/HMM).

## What Was Added

### 1. **Localization Controller Integration**
- Added `LocalizationController` instance to MainActivity
- Initialized in `onCreate()`
- Properly cleaned up in `onDestroy()`

### 2. **Automatic Initialization**
When a floor is selected:
- Automatically attempts to initialize localization for that floor
- Uses auto-initialization first (scans beacons for 5 seconds to determine position)
- Falls back to manual initialization if auto-init fails
- Starts continuous localization tracking

### 3. **Blue Dot Visualization**
The localization position is shown as a **bright blue dot** with:
- **Inner circle**: Bright blue (#0080FF), 10px radius, 80% opacity
- **Outer stroke**: White stroke with blue border for visibility
- **Position**: Snapped to the current node's coordinates
- **Updates**: Real-time updates as the HMM determines new positions

### 4. **Map Layers**
Added three new layer/source IDs:
- `localization-marker-source`: GeoJSON source for position data
- `localization-marker-layer`: Inner blue circle
- `localization-marker-stroke-layer`: Outer white stroke

## How It Works

### Position Determination
1. **RSSI Scanning**: Continuously scans BLE beacons with smoothing (EMA filter)
2. **HMM Processing**: Uses Hidden Markov Model to determine most likely node
3. **Hysteresis**: Requires 2 consecutive ticks (~2 seconds) before changing nodes to prevent jitter
4. **Position Update**: Gets (x, y) coordinates of the determined node

### Blue Dot Updates
```kotlin
localizationController.localizationState.collect { state ->
    val position = localizationController.getCurrentPosition()
    if (position != null) {
        val (x, y) = position
        updateLocalizationMarker(x, y, confidence)
    }
}
```

### Key Differences from GPS Blue Dot
| Feature | GPS Dot (MapLibre) | Localization Dot (Custom) |
|---------|-------------------|---------------------------|
| **Color** | Default blue | Bright blue (#0080FF) |
| **Position** | GPS coordinates | Graph node coordinates |
| **Updates** | GPS sensor | RSSI + HMM algorithm |
| **Smoothing** | GPS smoothing | Hysteresis (2 ticks) |
| **Accuracy** | Depends on GPS | Depends on beacon visibility |

## Position Behavior

### Node Snapping
- ✅ Position is **always at a node**, not interpolated between nodes
- ✅ Uses hysteresis to prevent rapid jumping
- ✅ Requires 2 consecutive ticks of same node before committing to change

### Smoothing Applied
1. **RSSI Smoothing**: EMA (Exponential Moving Average) with configurable gamma
2. **Hysteresis**: 2-tick delay before node changes
3. **No visual interpolation**: Dot jumps from node to node (instantly, after hysteresis)

### When Blue Dot Appears
- ✅ When localization successfully initializes
- ✅ When at least 2 beacons are visible
- ✅ When confidence > 0 (HMM has determined a position)
- ❌ Cleared when confidence drops to 0 or no beacons visible

## Permissions Required
The system checks for Bluetooth permissions before starting:
- **Android 12+**: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`
- **Android 11 and below**: `ACCESS_FINE_LOCATION`

If permissions are not granted, localization won't start (but won't crash).

## Logging
The implementation includes detailed logging:
```
Localization: node=node_123, pos=(50.1234, 26.5678), confidence=0.85
Beacons visible: 5
At junction - position may be ambiguous
```

## Future Enhancements (Not Implemented)

### Potential Additions
1. **Visual animation**: Smooth transition between nodes (interpolation)
2. **Confidence indicator**: Change dot size/opacity based on confidence
3. **Low signal warning**: Visual indicator when beacon count < 3
4. **Path prediction**: Show likely next nodes based on heading
5. **Accuracy circle**: Display uncertainty radius around the dot

### Animation Between Nodes
To add smooth movement, you could:
```kotlin
// Animate from current position to new position over 1 second
ValueAnimator.ofFloat(0f, 1f).apply {
    duration = 1000
    addUpdateListener { animator ->
        val fraction = animator.animatedValue as Float
        val interpolatedX = currentX + (newX - currentX) * fraction
        val interpolatedY = currentY + (newY - currentY) * fraction
        updateLocalizationMarker(interpolatedX, interpolatedY, confidence)
    }
    start()
}
```

## Testing
To test the blue dot:
1. Build and run the app
2. Grant all permissions (location/bluetooth)
3. Select a floor
4. Wait 5 seconds for auto-initialization
5. Walk near beacons - the blue dot should appear and update

## Troubleshooting

### Blue dot doesn't appear
- Check logs for "Bluetooth permissions not granted"
- Ensure beacons are visible: "Beacons visible: X" should be > 2
- Check "Auto-initialization failed" in logs
- Verify beacons are configured for the floor in backend

### Blue dot jumps around
- This is expected behavior (no interpolation)
- Hysteresis prevents rapid jumping (2-tick delay)
- Low beacon count causes uncertainty

### Blue dot is in wrong position
- Check beacon calibration in backend
- Verify graph nodes have correct (x, y) coordinates
- Check RSSI values are reasonable (-30 to -90 dBm)
