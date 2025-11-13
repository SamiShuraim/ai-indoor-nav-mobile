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
 * Assignment request to backend
 */
data class AssignmentRequest(
    @SerializedName("floorId")
    val floorId: Int,
    
    @SerializedName("position")
    val position: Position
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
