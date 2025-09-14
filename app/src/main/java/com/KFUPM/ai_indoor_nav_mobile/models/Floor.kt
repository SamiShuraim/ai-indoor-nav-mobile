package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Floor(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("buildingId")
    val buildingId: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("level")
    val level: Int,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("floorPlanUrl")
    val floorPlanUrl: String? = null,
    
    @SerializedName("pois")
    val pois: List<POI>? = null,
    
    @SerializedName("beacons")
    val beacons: List<Beacon>? = null,
    
    @SerializedName("routeNodes")
    val routeNodes: List<RouteNode>? = null
)