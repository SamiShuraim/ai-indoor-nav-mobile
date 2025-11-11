package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName
import com.KFUPM.ai_indoor_nav_mobile.utils.GeometryUtils

data class RouteNode(
    @SerializedName("type")
    val type: String = "Feature",
    
    @SerializedName("properties")
    val properties: RouteNodeProperties?,
    
    @SerializedName("geometry")
    val geometry: Any? = null // GeoJSON Point geometry
) {
    // Computed properties for backward compatibility
    val id: Int get() = properties?.id ?: 0
    val floorId: Int get() = properties?.floorId ?: 0
    val connectedNodeIds: List<Int>? get() = properties?.connectedNodeIds
    val isVisible: Boolean get() = properties?.isVisible ?: true
    val createdAt: String? get() = properties?.createdAt
    val updatedAt: String? get() = properties?.updatedAt
    
    val name: String? get() = "Node $id"
    val nodeType: String? get() = null
    val isAccessible: Boolean get() = isVisible
    val connectedNodes: List<String>? get() = connectedNodeIds?.map { it.toString() }
    
    // Extract coordinates from geometry
    // Note: Using a computed property instead of lazy to avoid Gson deserialization issues
    private val coordinates: Pair<Double, Double>?
        get() = _coordinates ?: GeometryUtils.extractCoordinatesFromGeometry(geometry).also { _coordinates = it }
    
    // Transient backing field for caching (not serialized)
    @Transient
    private var _coordinates: Pair<Double, Double>? = null
    
    val x: Double get() = coordinates?.first ?: 0.0
    val y: Double get() = coordinates?.second ?: 0.0
    val latitude: Double? get() = coordinates?.second
    val longitude: Double? get() = coordinates?.first
}

data class RouteNodeProperties(
    @SerializedName("id")
    val id: Int?,
    
    @SerializedName("floor_id")
    val floorId: Int?,
    
    @SerializedName("connected_node_ids")
    val connectedNodeIds: List<Int>? = null,
    
    @SerializedName("is_visible")
    val isVisible: Boolean = true,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null
)