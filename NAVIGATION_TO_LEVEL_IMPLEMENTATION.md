# Navigation to Assigned Level Implementation

## Overview
Implemented automatic navigation to the user's assigned accessibility level after trilateration determines their position. This feature uses the `/api/RouteNode/navigateToLevel` endpoint to generate an optimal path from the user's current trilaterated node to a suitable node on their assigned level.

## Implementation Details

### 1. API Endpoint Configuration
**File**: `ApiConstants.kt`
- Added new endpoint: `NAVIGATE_TO_LEVEL = "/api/RouteNode/navigateToLevel"`

### 2. Request Model
**File**: `models/PathModels.kt`
- Created `NavigateToLevelRequest` data class:
  ```kotlin
  data class NavigateToLevelRequest(
      @SerializedName("currentNodeId") val currentNodeId: Int,
      @SerializedName("targetLevel") val targetLevel: Int
  )
  ```

### 3. API Service Method
**File**: `services/ApiService.kt`
- Added `navigateToLevel()` method that:
  - Takes current node ID (from trilateration) and target level (from assignment)
  - Posts request to `/api/RouteNode/navigateToLevel`
  - Returns `FeatureCollection` containing the navigation path
  - Handles both array and FeatureCollection response formats
  - Includes comprehensive error logging

### 4. MainActivity Integration
**File**: `MainActivity.kt`

#### Added Navigation Logic:
- **New method**: `navigateToAssignedLevel(assignment: UserAssignment)`
  - Retrieves current node ID from localization controller (trilateration result)
  - Extracts target level from user assignment
  - Calls `apiService.navigateToLevel()` API
  - Displays path on map using existing `displayPath()` method
  - Falls back to `drawPathToAccessibilityLevel()` if API fails

#### Enhanced Localization Observer:
- Modified `observeLocalizationUpdates()` to:
  - Monitor when both assignment and trilateration are ready
  - Automatically trigger navigation when conditions are met
  - Use confidence threshold (>0.5) before navigating

#### State Management:
- Added `hasNavigatedToAssignedLevel` flag to prevent duplicate navigation
- Flag resets when new assignment is requested
- Ensures navigation happens even if assignment arrives before trilateration completes

## Flow Sequence

1. **App Startup**
   - Loads floor data and initializes localization
   - Auto-clicks assignment button after 1 second delay

2. **Trilateration**
   - Beacon scanning starts via `LocalizationController`
   - HMM engine determines most likely node ID
   - Updates `localizationState` with current node

3. **Assignment**
   - Backend assigns accessibility level based on age/disability
   - Assignment stored in `currentAssignment`
   - `displayAssignment()` called

4. **Automatic Navigation**
   - When both node ID and assignment level are available
   - `navigateToAssignedLevel()` called automatically
   - API request: `POST /api/RouteNode/navigateToLevel`
     ```json
     {
       "currentNodeId": 42,
       "targetLevel": 3
     }
     ```
   - Path displayed on map with orange-red visualization

## Example Usage

### From User's Perspective:
1. User enters building with app open
2. Localization determines they're at node 42
3. System assigns them to Level 3
4. Path automatically appears showing route to Level 3

### API Request Example:
```kotlin
val pathFeatureCollection = apiService.navigateToLevel(
    currentNodeId = 42,
    targetLevel = 3
)
```

### Response:
Returns GeoJSON FeatureCollection with:
- Path nodes (points) with `is_path_node = true`
- Path edges (lines) with `is_path_edge = true`

## Fallback Behavior

If the `/api/RouteNode/navigateToLevel` endpoint fails or is unavailable:
- System falls back to `drawPathToAccessibilityLevel()`
- Finds nearest node with matching accessibility level
- Uses existing path-finding logic

## Benefits

1. **Seamless Integration**: Works with existing trilateration and assignment systems
2. **Automatic Operation**: No user interaction required
3. **Robust Fallbacks**: Multiple fallback strategies ensure navigation always works
4. **Clear Logging**: Comprehensive logs for debugging and monitoring
5. **State Management**: Prevents duplicate navigation requests

## Testing Recommendations

1. **Test with different node positions**: Verify navigation works from various starting points
2. **Test different levels**: Ensure paths to all accessibility levels (1, 2, 3) work correctly
3. **Test timing scenarios**:
   - Assignment before trilateration completes
   - Trilateration before assignment arrives
   - Both ready simultaneously
4. **Test fallback**: Simulate API failure to verify fallback to old method
5. **Test new assignments**: Click assignment button multiple times to verify re-navigation

## Key Code Locations

- **API Endpoint**: `ApiConstants.kt:59`
- **Request Model**: `models/PathModels.kt:86-95`
- **API Method**: `services/ApiService.kt:490-544`
- **Navigation Logic**: `MainActivity.kt:1847-1902`
- **Observer Integration**: `MainActivity.kt:1592-1600`

## Notes

- Navigation triggers only when confidence > 0.5 to ensure accurate position
- Node ID is parsed from String to Int for API compatibility
- Path visualization uses same rendering as POI navigation (orange-red color)
- Clear path button automatically appears after navigation
