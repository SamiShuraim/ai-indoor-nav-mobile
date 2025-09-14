package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Building(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("address")
    val address: String? = null,
    
    @SerializedName("latitude")
    val latitude: Double? = null,
    
    @SerializedName("longitude")
    val longitude: Double? = null,
    
    @SerializedName("floors")
    val floors: List<Floor>? = null
)