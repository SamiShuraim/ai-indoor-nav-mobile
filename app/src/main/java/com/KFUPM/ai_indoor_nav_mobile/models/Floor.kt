package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Floor(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("building_id")
    val buildingId: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("floor_number")
    private val _floorNumber: Int = 0,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    
    @SerializedName("pois")
    val pois: List<POI>? = null,
    
    @SerializedName("beacons")
    val beacons: List<Beacon>? = null,
    
    @SerializedName("routeNodes")
    val routeNodes: List<RouteNode>? = null
) {
    /**
     * Get floor number - with fallback to parsing from name if backend returns 0
     */
    val floorNumber: Int
        get() {
            // If backend returns a valid floor number, use it
            if (_floorNumber > 0) {
                return _floorNumber
            }
            
            // Otherwise, parse from name as fallback
            return parseFloorNumberFromName(name)
        }
    
    companion object {
        /**
         * Parse floor number from floor name
         * Examples: "First Floor" -> 1, "Second Floor" -> 2, "Ground Floor" -> 0
         */
        private fun parseFloorNumberFromName(name: String): Int {
            return when {
                name.contains("First", ignoreCase = true) || name.contains("1", ignoreCase = true) -> 1
                name.contains("Second", ignoreCase = true) || name.contains("2", ignoreCase = true) -> 2
                name.contains("Third", ignoreCase = true) || name.contains("3", ignoreCase = true) -> 3
                name.contains("Fourth", ignoreCase = true) || name.contains("4", ignoreCase = true) -> 4
                name.contains("Fifth", ignoreCase = true) || name.contains("5", ignoreCase = true) -> 5
                name.contains("Ground", ignoreCase = true) || name.contains("G", ignoreCase = true) -> 0
                name.contains("Basement", ignoreCase = true) || name.contains("B", ignoreCase = true) -> -1
                else -> {
                    // Try to extract number from name (e.g., "Floor 3" -> 3)
                    val numberPattern = """\d+""".toRegex()
                    val match = numberPattern.find(name)
                    match?.value?.toIntOrNull() ?: 1 // Default to 1 if can't parse
                }
            }
        }
    }
}