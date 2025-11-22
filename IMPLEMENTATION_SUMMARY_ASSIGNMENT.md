# User Assignment and Localization-Based Routing Implementation

## Summary of Changes

This document summarizes the implementation of user assignment functionality and localization-based routing for the indoor navigation application.

## Changes Made

### 1. User Assignment Data Model
**File**: `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/models/UserAssignment.kt`
- Created `UserAssignment` data class with:
  - `age`: Random integer between 18-90
  - `isDisabled`: Boolean (20% chance of being true)
  - `floorId` and `floorName`: Current floor information
- Added helper methods:
  - `getHealthStatusEmoji()`: Returns ♿ for disabled, 🚶 for enabled
  - `getHealthStatusText()`: Returns "Disabled" or "Enabled"
- Created supporting classes:
  - `AssignmentRequest`: For sending assignment requests to backend
  - `Position`: For x, y coordinates

### 2. API Service Updates
**File**: `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/services/ApiService.kt`
- Added `requestUserAssignment()` method
  - Sends POST request to `/api/UserAssignment` endpoint
  - Includes floor ID and current position (x, y)
  - Returns `UserAssignment` object from backend

**File**: `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/ApiConstants.kt`
- Added `USER_ASSIGNMENT` endpoint constant

### 3. UI Layout Changes
**File**: `/app/src/main/res/layout/activity_main.xml`
- Replaced Bluetooth button (`fabBluetooth`) with Assignment button (`fabAssignment`)
- Added assignment info display container at bottom:
  - Shows floor name, user age, and health status with emoji
  - Uses existing floor selector background style
  - Hidden by default, becomes visible when assignment is received

**File**: `/app/src/main/res/drawable/ic_assignment.xml`
- Created new assignment icon (clipboard with checklist design)

### 4. MainActivity Updates
**File**: `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/MainActivity.kt`

#### New Variables:
- `fabAssignment`: Reference to assignment button
- `assignmentInfoContainer`: Container for assignment display
- `assignmentInfoText`: TextView for assignment info
- `currentAssignment`: Stores current user assignment

#### New Methods:

1. **`requestInitialAssignment(floorId: Int)`**
   - Called automatically after localization initialization
   - Gets current position from localization controller
   - Requests assignment from backend (with local fallback)
   - Displays assignment info

2. **`requestNewAssignment()`**
   - Called when user clicks assignment button
   - Gets current position from localization controller
   - Requests new assignment from backend (with local fallback)
   - Updates and displays new assignment info

3. **`generateLocalAssignment(floorId: Int)`**
   - Fallback method if backend doesn't support assignment endpoint
   - Generates random age between 18-90
   - Generates random disabled status (20% probability)
   - Returns `UserAssignment` object

4. **`displayAssignment(assignment: UserAssignment)`**
   - Updates assignment info TextView
   - Shows floor name, age, and health status with emoji
   - Makes assignment container visible

#### Updated Methods:

5. **`handleNavigationToPOI(poiId: Int, poiName: String)`**
   - **CRITICAL CHANGE**: Now uses localization position instead of GPS
   - First tries to get position from `localizationController.getCurrentPosition()`
   - Falls back to GPS only if localization is unavailable
   - Logs which position source is being used for debugging

6. **`initializeLocalization(floorId: Int)`**
   - Added call to `requestInitialAssignment()` after successful localization start
   - This ensures assignment is requested as soon as position is determined

7. **`setupButtonListeners()`**
   - Updated to call `requestNewAssignment()` instead of opening Bluetooth devices activity

## Key Features

### 1. Routing from Artificial Blue Dot
- Navigation routing now uses the Bluetooth RSSI-based position (artificial blue dot)
- GPS is only used as a fallback if localization is not available
- This ensures indoor routing works correctly based on beacon triangulation

### 2. Automatic Initial Assignment
- After beacons are fetched and initial position is located via Bluetooth:
  1. System gets current position from localization controller
  2. Sends assignment request to backend with floor ID and position
  3. Displays assignment with floor, age, and health status

### 3. Manual Re-assignment
- User can click the assignment button (top right) at any time
- Gets a new random assignment with different age and health status
- Assignment info updates immediately at the bottom of screen

### 4. Assignment Display Format
```
🚶 Floor: Ground Floor | Age: 45 | Status: Enabled
♿ Floor: First Floor | Age: 72 | Status: Disabled
```

### 5. Backend Integration with Local Fallback
- Attempts to use backend API for assignments
- If backend doesn't support the endpoint yet, generates assignments locally
- Ensures app works regardless of backend implementation status

## Testing Notes

1. **Localization Required**: Assignment is only requested after successful localization
2. **Position Dependency**: Both initial and new assignments require valid position from localization controller
3. **Backend Optional**: Local generation ensures functionality even without backend support
4. **Visual Feedback**: Assignment info is clearly displayed at bottom with emoji indicators

## API Endpoint Expected (Backend)

```
POST /api/UserAssignment
Content-Type: application/json

Request Body:
{
  "floorId": 1,
  "position": {
    "x": 46.123,
    "y": 26.456
  }
}

Response:
{
  "age": 45,
  "isDisabled": false,
  "floorId": 1,
  "floorName": "Ground Floor"
}
```

## Files Created
1. `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/models/UserAssignment.kt`
2. `/app/src/main/res/drawable/ic_assignment.xml`

## Files Modified
1. `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/MainActivity.kt`
2. `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/services/ApiService.kt`
3. `/app/src/main/java/com/KFUPM/ai_indoor_nav_mobile/ApiConstants.kt`
4. `/app/src/main/res/layout/activity_main.xml`

## Completed Requirements

✅ Routing uses artificial blue dot (Bluetooth RSSI position) - **NEVER GPS**
✅ **ALL GPS CODE REMOVED** - No GPS fallback anywhere in the app
✅ Assignment requested after beacons fetched and initial position located  
✅ Random age between 18-90  
✅ 20% chance of disabled status  
✅ Bluetooth button replaced with assignment button at top right  
✅ Assignment info displayed at bottom (floor, age, health status with emoji)  
✅ Button allows getting new assignments at any time

## GPS Removal

The following GPS-related code has been **COMPLETELY REMOVED** from MainActivity:
- ❌ `locationPermissionRequest` - No longer requests location permissions for GPS
- ❌ `checkLocationPermission()` - Removed entirely
- ❌ `enableUserLocation()` - Removed entirely
- ❌ GPS fallback in `handleNavigationToPOI()` - Now requires localization position
- ❌ `LocationComponent` usage - No longer activates or uses GPS location component
- ❌ `CameraMode.TRACKING` import - Removed

**Note**: `ACCESS_FINE_LOCATION` permission is still required on Android 11 and below, but ONLY for Bluetooth beacon scanning, NOT for GPS. This is an Android system requirement for Bluetooth functionality.
