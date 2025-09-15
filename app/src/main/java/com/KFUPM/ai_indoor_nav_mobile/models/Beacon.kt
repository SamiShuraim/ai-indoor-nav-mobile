package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Beacon(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("floor_id")
    val floorId: Int,
    
    @SerializedName("beacon_type_id")
    val beaconTypeId: Int? = null,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("uuid")
    val uuid: String? = null,
    
    @SerializedName("major_id")
    val majorId: Int? = null,
    
    @SerializedName("minor_id")
    val minorId: Int? = null,
    
    @SerializedName("geometry")
    val geometry: Any? = null, // PostGIS Point geometry
    
    @SerializedName("is_active")
    val isActive: Boolean = true,
    
    @SerializedName("is_visible")
    val isVisible: Boolean = true,
    
    @SerializedName("battery_level")
    val batteryLevel: Int = 100,
    
    @SerializedName("last_seen")
    val lastSeen: String? = null,
    
    @SerializedName("installation_date")
    val installationDate: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    
    @SerializedName("beacon_type")
    val beaconType: BeaconType? = null
) {
    // Computed properties for backward compatibility
    val major: Int get() = majorId ?: 0
    val minor: Int get() = minorId ?: 0
    val x: Double get() = 0.0 // Will be extracted from geometry
    val y: Double get() = 0.0 // Will be extracted from geometry  
    val latitude: Double? get() = null // Will be extracted from geometry
    val longitude: Double? get() = null // Will be extracted from geometry
}

data class BeaconType(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("transmission_power")
    val transmissionPower: Int? = null,
    
    @SerializedName("battery_life")
    val batteryLife: Int? = null,
    
    @SerializedName("range_meters")
    val rangeMeters: Double? = null
)