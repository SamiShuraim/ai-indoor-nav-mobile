package com.KFUPM.ai_indoor_nav_mobile.models

import com.google.gson.annotations.SerializedName

data class RouteNode(
    @SerializedName("id")
    val id: String,
    
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
    
    @SerializedName("nodeType")
    val nodeType: String? = null,
    
    @SerializedName("isAccessible")
    val isAccessible: Boolean = true,
    
    @SerializedName("connectedNodes")
    val connectedNodes: List<String>? = null
)