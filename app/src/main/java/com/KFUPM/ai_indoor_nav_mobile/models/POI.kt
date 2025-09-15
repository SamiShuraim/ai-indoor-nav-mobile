package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName
import com.KFUPM.ai_indoor_nav_mobile.utils.GeometryUtils

data class POI(
    @SerializedName("id")
    val id: Int?,
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("floor_id")
    val floorId: Int? = null,
    
    @SerializedName("category_id")
    val categoryId: Int? = null,
    
    @SerializedName("poi_type")
    val poiType: String? = null,
    
    @SerializedName("color")
    val color: String? = null,
    
    @SerializedName("is_visible")
    val isVisible: Boolean = true,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    
    @SerializedName("geometry")
    val geometry: Any? = null, // PostGIS Polygon geometry
    
    @SerializedName("category")
    val category: POICategory? = null
) {
    // Computed properties for backward compatibility
    private val coordinates: Pair<Double, Double>? by lazy {
        GeometryUtils.extractCoordinatesFromGeometry(geometry)
    }
    
    val x: Double get() = coordinates?.first ?: 0.0
    val y: Double get() = coordinates?.second ?: 0.0
    val latitude: Double? get() = coordinates?.second
    val longitude: Double? get() = coordinates?.first
}

data class POICategory(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("color")
    val color: String? = null
)