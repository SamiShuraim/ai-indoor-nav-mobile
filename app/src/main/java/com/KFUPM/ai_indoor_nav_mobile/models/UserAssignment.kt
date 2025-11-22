package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

/**
 * Simple assignment request to backend
 */
data class AssignmentRequestSimple(
    @SerializedName("age")
    val age: Int,
    
    @SerializedName("isDisabled")
    val isDisabled: Boolean
)

/**
 * Assignment response from backend (Load Balancer)
 */
data class AssignmentResponse(
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
 * User assignment with age and health status (for display)
 */
data class UserAssignment(
    @SerializedName("age")
    val age: Int,
    
    @SerializedName("isDisabled")
    val isDisabled: Boolean,
    
    @SerializedName("level")
    val level: Int? = null,
    
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
