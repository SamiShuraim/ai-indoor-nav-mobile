package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class PathRequest(
    @SerializedName("userLocation")
    val userLocation: UserLocation,
    
    @SerializedName("destinationPoiId")
    val destinationPoiId: Int
)

data class UserLocation(
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double
)