package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

/**
 * Request model for path finding API
 */
data class PathRequest(
    @SerializedName("userLocation")
    val userLocation: UserLocation,
    
    @SerializedName("destinationPoiId")
    val destinationPoiId: Int
)

/**
 * User location coordinates
 */
data class UserLocation(
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double
)

/**
 * Path response containing navigation route as GeoJSON FeatureCollection
 */
data class PathResponse(
    @SerializedName("type")
    val type: String = "FeatureCollection",
    
    @SerializedName("features")
    val features: List<PathFeature>
)

/**
 * Individual feature in the path (either a node or edge)
 */
data class PathFeature(
    @SerializedName("type")
    val type: String = "Feature",
    
    @SerializedName("geometry")
    val geometry: PathGeometry,
    
    @SerializedName("properties")
    val properties: PathProperties
)

/**
 * Geometry for path features (Point for nodes, LineString for edges)
 */
data class PathGeometry(
    @SerializedName("type")
    val type: String, // "Point" or "LineString"
    
    @SerializedName("coordinates")
    val coordinates: Any // [lng, lat] for Point, [[lng, lat], [lng, lat]] for LineString
)

/**
 * Properties for path features
 */
data class PathProperties(
    @SerializedName("path_order")
    val pathOrder: Int? = null,
    
    @SerializedName("is_path_node")
    val isPathNode: Boolean? = null,
    
    @SerializedName("path_segment")
    val pathSegment: Int? = null,
    
    @SerializedName("is_path_edge")
    val isPathEdge: Boolean? = null,
    
    @SerializedName("from_node_id")
    val fromNodeId: Int? = null,
    
    @SerializedName("to_node_id")
    val toNodeId: Int? = null
)

/**
 * Request model for navigating to a specific level
 */
data class NavigateToLevelRequest(
    @SerializedName("currentNodeId")
    val currentNodeId: Int,
    
    @SerializedName("targetLevel")
    val targetLevel: Int
)