package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class RouteNode(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("floor_id")
    val floorId: Int,
    
    @SerializedName("connected_node_ids")
    val connectedNodeIds: List<Int>? = null,
    
    @SerializedName("geometry")
    val geometry: Any? = null, // PostGIS Point geometry
    
    @SerializedName("is_visible")
    val isVisible: Boolean = true,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null
) {
    // Computed properties for backward compatibility
    val name: String? get() = "Node $id"
    val x: Double get() = 0.0 // Will be extracted from geometry
    val y: Double get() = 0.0 // Will be extracted from geometry
    val latitude: Double? get() = null // Will be extracted from geometry
    val longitude: Double? get() = null // Will be extracted from geometry
    val nodeType: String? get() = null
    val isAccessible: Boolean get() = isVisible
    val connectedNodes: List<String>? get() = connectedNodeIds?.map { it.toString() }
}