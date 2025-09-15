package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

// Simple wrapper for GeoJSON Feature
data class POI(
    @SerializedName("type")
    val type: String = "Feature",
    
    @SerializedName("properties")
    val properties: Map<String, Any?>? = null,
    
    @SerializedName("geometry")
    val geometry: Any? = null // Raw GeoJSON geometry
) {
    // Helper properties for easy access
    val id: Int? get() = (properties?.get("id") as? Number)?.toInt()
    val name: String? get() = properties?.get("name") as? String
    val description: String? get() = properties?.get("description") as? String
    val floorId: Int? get() = (properties?.get("floor_id") as? Number)?.toInt()
    val categoryId: Int? get() = (properties?.get("category_id") as? Number)?.toInt()
    val poiType: String? get() = properties?.get("poi_type") as? String
    val color: String? get() = properties?.get("color") as? String
    val isVisible: Boolean get() = (properties?.get("is_visible") as? Boolean) ?: true
    val createdAt: String? get() = properties?.get("created_at") as? String
    val updatedAt: String? get() = properties?.get("updated_at") as? String
    
    // For backward compatibility (not needed with GeoJSON)
    val x: Double get() = 0.0
    val y: Double get() = 0.0
    val latitude: Double? get() = null
    val longitude: Double? get() = null
    val category: POICategory? get() = null
}

data class POIProperties(
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
    
    @SerializedName("category")
    val category: POICategory? = null
)

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