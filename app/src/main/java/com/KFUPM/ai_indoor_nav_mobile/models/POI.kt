package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class POI(
    @SerializedName("id")
    val id: String?,
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("floorId")
    val floorId: String? = null,
    
    @SerializedName("categoryId")
    val categoryId: String? = null,
    
    @SerializedName("x")
    val x: Double = 0.0,
    
    @SerializedName("y")
    val y: Double = 0.0,
    
    @SerializedName("latitude")
    val latitude: Double? = null,
    
    @SerializedName("longitude")
    val longitude: Double? = null,
    
    @SerializedName("geometry")
    val geometry: Any? = null, // GeoJSON geometry
    
    @SerializedName("category")
    val category: POICategory? = null
)

data class POICategory(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("color")
    val color: String? = null,
    
    @SerializedName("icon")
    val icon: String? = null
)