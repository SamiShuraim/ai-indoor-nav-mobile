package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName
import com.KFUPM.ai_indoor_nav_mobile.utils.GeometryUtils

data class Beacon(
    @SerializedName("type")
    val type: String = "Feature",
    
    @SerializedName("properties")
    val properties: BeaconProperties?,
    
    @SerializedName("geometry")
    val geometry: Any? = null // GeoJSON Point geometry
) {
    // Computed properties for backward compatibility
    val id: Int get() = properties?.id ?: 0
    val floorId: Int get() = properties?.floorId ?: 0
    val beaconTypeId: Int? get() = properties?.beaconTypeId
    val name: String? get() = properties?.name
    val uuid: String? get() = properties?.uuid
    val majorId: Int? get() = properties?.majorId
    val minorId: Int? get() = properties?.minorId
    val isActive: Boolean get() = properties?.isActive ?: true
    val isVisible: Boolean get() = properties?.isVisible ?: true
    val batteryLevel: Int get() = properties?.batteryLevel ?: 100
    val lastSeen: String? get() = properties?.lastSeen
    val installationDate: String? get() = properties?.installationDate
    val createdAt: String? get() = properties?.createdAt
    val updatedAt: String? get() = properties?.updatedAt
    val beaconType: BeaconType? get() = properties?.beaconType
    
    val major: Int get() = majorId ?: 0
    val minor: Int get() = minorId ?: 0
    
    // Extract coordinates from geometry
    // Note: Using a computed property instead of lazy to avoid Gson deserialization issues
    private val coordinates: Pair<Double, Double>?
        get() = _coordinates ?: GeometryUtils.extractCoordinatesFromGeometry(geometry).also { _coordinates = it }
    
    // Transient backing field for caching (not serialized)
    @Transient
    private var _coordinates: Pair<Double, Double>? = null
    
    val x: Double get() = coordinates?.first ?: 0.0
    val y: Double get() = coordinates?.second ?: 0.0
    val latitude: Double? get() = coordinates?.second
    val longitude: Double? get() = coordinates?.first
}

data class BeaconProperties(
    @SerializedName("id")
    val id: Int?,
    
    @SerializedName("floor_id")
    val floorId: Int?,
    
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
)

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