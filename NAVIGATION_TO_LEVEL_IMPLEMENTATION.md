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
- **Navigation Logic**: `MainActivity.kt:1947-2002`
- **Observer Integration**: `MainActivity.kt:1592-1600`
- **Progress Tracking**: `MainActivity.kt:1509-1714`

## Route Progress Tracking

### Visual Feedback as User Walks
As the user walks along the navigation path, the route updates dynamically:

1. **Visited Nodes** (past): 
   - Color: Gray (#888888)
   - Size: Smaller (6px radius)
   - Opacity: 60%
   - Shows where you've already been

2. **Upcoming Nodes** (future):
   - Color: Orange-red (#FF6B35)
   - Size: Larger (8px radius)
   - Opacity: 90%
   - Shows where you need to go

3. **Visited Edges**: Gray, faded (50% opacity)
4. **Upcoming Edges**: Orange-red, bright (80% opacity)

### How It Works

1. **Real-Time Position Monitoring**
   - `updateNavigationProgress()` called every time trilateration updates
   - Checks if current node is part of the active navigation path
   - Marks nodes as visited when user reaches them

2. **Path Visualization Update**
   - `redrawPathWithProgress()` separates visited/future nodes
   - Past nodes and edges rendered in gray
   - Future nodes and edges rendered in orange-red
   - Provides clear visual progress indicator

3. **Destination Detection**
   - `checkIfDestinationReached()` monitors user position
   - Checks if current node has same level as target level
   - Triggers when user reaches ANY node with the target accessibility level
   - Not limited to specific destination node - any node with correct level counts

4. **Automatic Completion**
   - When destination reached:
     - Shows toast: "✅ You have reached your destination (Level X)!"
     - Automatically clears navigation (clicks the X button)
     - Resets all navigation state

### Example Scenario

**Path**: A → B → C → D (all going to Level 3 destination)

1. **Start at A**:
   - All nodes orange-red
   
2. **Walk to B**:
   - A turns gray (visited)
   - B, C, D remain orange-red
   - Edge A→B turns gray

3. **Walk to C**:
   - A, B both gray
   - C, D remain orange-red
   - Edges A→B and B→C gray

4. **Reach D** (has level 3):
   - Message: "✅ You have reached your destination (Level 3)!"
   - Navigation automatically clears
   - User can continue exploring

## Notes

- Navigation triggers only when confidence > 0.5 to ensure accurate position
- Node ID is parsed from String to Int for API compatibility
- Path visualization uses orange-red for future, gray for past
- Clear path button automatically appears after navigation
- Destination detection works for any node with matching level, not just the final node
- Progress tracking requires nodes to have `id` property in GeoJSON
- Visited node tracking persists until navigation is cleared
