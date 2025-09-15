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
    val floorNumber: Int,
    
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
)