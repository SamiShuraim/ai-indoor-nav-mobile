package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

/**
 * Response from /api/loadbalancer/arrivals/assign
 */
data class VisitorAssignment(
    @SerializedName("visitorId")
    val visitorId: String,
    
    @SerializedName("assignedLevel")
    val assignedLevel: Int,
    
    @SerializedName("assignedAt")
    val assignedAt: String,
    
    @SerializedName("expiresAt")
    val expiresAt: String
)
