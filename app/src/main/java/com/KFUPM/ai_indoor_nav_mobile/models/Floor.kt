package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Floor(
    @SerializedName("Id")
    val id: Int,
    
    @SerializedName("BuildingId")
    val buildingId: Int,
    
    @SerializedName("Name")
    val name: String,
    
    @SerializedName("FloorNumber")
    val floorNumber: Int,
    
    @SerializedName("CreatedAt")
    val createdAt: String? = null,
    
    @SerializedName("UpdatedAt")
    val updatedAt: String? = null,
    
    @SerializedName("Pois")
    val pois: List<POI>? = null,
    
    @SerializedName("Beacons")
    val beacons: List<Beacon>? = null,
    
    @SerializedName("RouteNodes")
    val routeNodes: List<RouteNode>? = null
)