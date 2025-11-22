package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Floor(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("buildingId")
    val buildingId: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("floorNumber")
    private val _floorNumber: Int,
    
    @SerializedName("createdAt")
    val createdAt: String? = null,
    
    @SerializedName("updatedAt")
    val updatedAt: String? = null,
    
    @SerializedName("pois")
    val pois: List<POI>? = null,
    
    @SerializedName("beacons")
    val beacons: List<Beacon>? = null,
    
    @SerializedName("routeNodes")
    val routeNodes: List<RouteNode>? = null
) {
    /**
     * Display floor number (adds 1 to 0-indexed backend value)
     * Backend: 0, 1, 2 -> Display: 1, 2, 3
     */
    val floorNumber: Int
        get() = _floorNumber + 1
}