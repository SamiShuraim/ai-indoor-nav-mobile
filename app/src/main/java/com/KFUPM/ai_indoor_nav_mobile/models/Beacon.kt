package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Beacon(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("uuid")
    val uuid: String,
    
    @SerializedName("major")
    val major: Int,
    
    @SerializedName("minor")
    val minor: Int,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("floorId")
    val floorId: String,
    
    @SerializedName("x")
    val x: Double,
    
    @SerializedName("y")
    val y: Double,
    
    @SerializedName("latitude")
    val latitude: Double? = null,
    
    @SerializedName("longitude")
    val longitude: Double? = null,
    
    @SerializedName("batteryLevel")
    val batteryLevel: Int? = null,
    
    @SerializedName("isActive")
    val isActive: Boolean = true,
    
    @SerializedName("lastHeartbeat")
    val lastHeartbeat: String? = null,
    
    @SerializedName("beaconTypeId")
    val beaconTypeId: String? = null,
    
    @SerializedName("beaconType")
    val beaconType: BeaconType? = null
)

data class BeaconType(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("range")
    val range: Double? = null,
    
    @SerializedName("color")
    val color: String? = null
)