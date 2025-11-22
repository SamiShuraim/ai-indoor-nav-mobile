package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

/**
 * User assignment with age and health status
 */
data class UserAssignment(
    @SerializedName("age")
    val age: Int,
    
    @SerializedName("isDisabled")
    val isDisabled: Boolean,
    
    @SerializedName("floorId")
    val floorId: Int? = null,
    
    @SerializedName("floorName")
    val floorName: String? = null
) {
    /**
     * Get health status emoji
     */
    fun getHealthStatusEmoji(): String {
        return if (isDisabled) "♿" else "🚶"
    }
    
    /**
     * Get health status text
     */
    fun getHealthStatusText(): String {
        return if (isDisabled) "Disabled" else "Enabled"
    }
}

/**
 * Assignment request to backend (Load Balancer format)
 */
data class AssignmentRequest(
    @SerializedName("level")
    val level: Int,
    
    @SerializedName("visitorId")
    val visitorId: String,
    
    @SerializedName("decision")
    val decision: AssignmentDecision,
    
    @SerializedName("traceId")
    val traceId: String? = null
)

/**
 * Assignment decision details
 */
data class AssignmentDecision(
    @SerializedName("isDisabled")
    val isDisabled: Boolean,
    
    @SerializedName("age")
    val age: Int,
    
    @SerializedName("ageCutoff")
    val ageCutoff: Int? = null,
    
    @SerializedName("alpha1")
    val alpha1: Double? = null,
    
    @SerializedName("pDisabled")
    val pDisabled: Double? = null,
    
    @SerializedName("shareLeftForOld")
    val shareLeftForOld: Double? = null,
    
    @SerializedName("tauQuantile")
    val tauQuantile: Double? = null,
    
    @SerializedName("occupancy")
    val occupancy: Map<String, Int>? = null,
    
    @SerializedName("reason")
    val reason: String? = null
)

/**
 * Position coordinates
 */
data class Position(
    @SerializedName("x")
    val x: Double,
    
    @SerializedName("y")
    val y: Double
)
