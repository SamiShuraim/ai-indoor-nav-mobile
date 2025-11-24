# New Features: Beacon Mapping & Manual Floor Override

## Feature 1: Manual Beacon Mapping

### The Problem
Two beacons aren't mapped yet, and the automatic background mapping isn't working reliably.

### The Solution
**Tap-to-Map Interface** in the beacon viewer:

1. Click the **top-left Bluetooth button** (📶)
2. See a list of all nearby beacons sorted by signal strength:
   - **Mapped beacons**: `📶📶📶 [A] EC:E3:34:1A:CD:BA`
   - **Unmapped beacons**: `📶📶 📍 44:1D:64:F5:B8:4E (UNMAPPED)` ← Click these!

3. **Tap an unmapped beacon** (the one with 📍 icon)
4. A dialog appears:
   ```
   📍 Map Beacon
   
   You are standing next to:
   44:1D:64:F5:B8:4E
   RSSI: -65 dBm
   
   Enter a name for this beacon: [____]
   
   [Save] [Cancel]
   ```

5. Type the beacon name (e.g., "G") and tap **Save**
6. Done! ✅ The mapping is stored permanently

### How to Use It
**To map the 2 unmapped beacons:**

1. Walk to the first unmapped beacon
2. Open beacon viewer (top-left button)
3. The unmapped beacon should be at the TOP (strongest signal)
4. Tap it → Enter name → Save
5. Walk to the second unmapped beacon
6. Repeat

**The mapped beacons are stored in SharedPreferences and persist across app restarts.**

### UI Elements
- **📶📶📶📶** = Excellent signal (≥ -50 dBm) - You're very close!
- **📶📶📶** = Good signal (≥ -60 dBm)
- **📶📶** = Fair signal (≥ -70 dBm)
- **📶** = Weak signal (≥ -80 dBm)
- **📵** = Very weak (< -80 dBm)
- **📍** = Unmapped beacon - tap to map!

## Feature 2: Manual Floor Override

### The Problem
Automatic floor detection (level-to-level navigation) isn't working reliably. When you go from Floor 1 to Floor 2, it doesn't detect the change.

### The Solution
**Manual floor selection** now sets your position to that floor:

### How It Works

**Before (Automatic Mode):**
- App detects beacons → Calculates which floor you're on → Shows blue dot
- Problem: Detection fails between floors

**Now (Manual Mode):**
- You click "Level 2" → App says "I'm on Level 2" → Shows blue dot on Level 2
- The blue dot position is calculated from the BLE signals you're receiving

### How to Use It

1. **Go to Floor 2** (physically walk there)
2. **Click "Level 2"** in the floor selector
3. Toast shows: **"📍 Showing position on Floor 2"**
4. Blue dot appears on Floor 2 map, positioned based on BLE signals

The app will:
- ✅ Show the blue dot on the floor YOU selected
- ✅ Calculate position based on beacons you're receiving
- ✅ Stay on that floor until you select a different one
- ✅ Track your movement on that floor

**To return to automatic mode:**
- Just wait - if beacons from a different floor are detected, it will auto-switch and clear the manual override

### Visual Feedback

**Manual Mode Active:**
```
Toast: "📍 Showing position on Floor 2"
Log: "🎯 Manual floor override set to: Floor 2 (id=3)"
```

**Automatic Mode (after auto-detection):**
```
Toast: "🚶 Floor 2"
Log: "🚶 FLOOR CHANGED: 1 → 3"
```

## Technical Details

### Beacon Mapping Storage
- **Location**: `SharedPreferences` (key: "beacon_name_mappings")
- **Format**: JSON map of `{"BeaconName": "MAC:ADDRESS"}`
- **Persistence**: Permanent until cache is cleared
- **Access**: Via `BeaconMappingCache` class

### Manual Floor Override
- **Variable**: `manualFloorOverride: Int?`
- **Scope**: Activity-level (resets on app restart)
- **Priority**: Overrides automatic beacon-based floor detection
- **Cleared**: When automatic floor change is detected

### Beacon Viewer Dialog
```kotlin
// Shows list of beacons with:
- Signal strength bars
- Beacon name (if mapped) or "UNMAPPED"
- MAC address
- RSSI value
- Tap to map (unmapped) or view info (mapped)
```

## User Workflow Examples

### Example 1: Map Two Beacons
```
1. Stand next to beacon G
2. Open beacon viewer → See "📶📶📶📶 📍 44:1D:64:F5:B8:4E (UNMAPPED)" at top
3. Tap it → Enter "G" → Save
4. Toast: "✅ Mapped 'G' → 44:1D:64:F5:B8:4E"

5. Walk to beacon I
6. Open beacon viewer → See "📶📶📶📶 📍 EC:E3:34:1B:44:7E (UNMAPPED)" at top
7. Tap it → Enter "I" → Save
8. Toast: "✅ Mapped 'I' → EC:E3:34:1B:44:7E"

Done! Both beacons are now mapped forever.
```

### Example 2: Navigate Floor 1 → Floor 2
```
1. You're on Floor 1 → Blue dot shows correctly
2. Walk up stairs to Floor 2
3. Automatic detection fails → Blue dot disappears
4. Click "Level 2" in floor selector
5. Toast: "📍 Showing position on Floor 2"
6. Blue dot appears on Floor 2 map → Navigation works!
```

## Benefits

### Beacon Mapping
- ✅ No more waiting 30 seconds for background scans
- ✅ Works immediately (you're standing next to it!)
- ✅ User has full control
- ✅ Visual feedback (signal bars show proximity)
- ✅ Persistent storage

### Manual Floor Override
- ✅ Works even when automatic detection fails
- ✅ Immediate feedback
- ✅ Simple UX (just tap the floor)
- ✅ Blue dot always visible
- ✅ Can navigate normally on selected floor

## Limitations

### Beacon Mapping
- User must physically walk to each unmapped beacon
- Requires knowing the beacon's correct name
- Only works for beacons in the database

### Manual Floor Override
- User must manually select correct floor
- Doesn't automatically switch floors anymore (until manual override is cleared)
- Position accuracy depends on beacon placement

## Future Enhancements

1. **Auto-suggest beacon names** based on proximity to POIs
2. **Beacon preview image** in mapping dialog
3. **"Re-enable automatic detection"** button
4. **Floor confidence indicator** showing beacon signal quality per floor
5. **"Suggest floor"** button that recommends floor based on signals
