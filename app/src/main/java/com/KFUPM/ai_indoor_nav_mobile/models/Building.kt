package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class Building(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    
    @SerializedName("floors")
    val floors: List<Floor>? = null
)