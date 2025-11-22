package com.KFUPM.ai_indoor_nav_mobile.services

import android.util.Log
import com.KFUPM.ai_indoor_nav_mobile.ApiConstants
import com.KFUPM.ai_indoor_nav_mobile.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.maplibre.geojson.FeatureCollection

import java.io.IOException

class ApiService {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    companion object {
        private const val TAG = "ApiService"
    }
    
    /**
     * Fetch all buildings
     */
    suspend fun getBuildings(): List<Building>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.BUILDINGS}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body.string()
                        run {
                            Log.d(TAG, "Buildings response: $jsonString")
                            val type = object : TypeToken<List<Building>>() {}.type
                            gson.fromJson<List<Building>>(jsonString, type)
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch buildings: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching buildings", e)
                null
            }
        }
    }
    
    /**
     * Fetch floors for a specific building
     */
    suspend fun getFloorsByBuilding(buildingId: Int): List<Floor>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.floorsByBuilding(buildingId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Floors response: $jsonString")
                            val type = object : TypeToken<List<Floor>>() {}.type
                            gson.fromJson<List<Floor>>(jsonString, type)
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch floors: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching floors", e)
                null
            }
        }
    }
    
    /**
     * Fetch POIs for a specific floor
     */
    suspend fun getPOIsByFloor(floorId: Int): List<POI>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.poisByFloor(floorId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "POIs response: $jsonString")
                            val type = object : TypeToken<List<POI>>() {}.type
                            gson.fromJson<List<POI>>(jsonString, type)
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch POIs: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching POIs", e)
                null
            }
        }
    }
    
    /**
     * Fetch POIs for a specific building (all floors)
     */
    suspend fun getPOIsByBuilding(buildingId: Int): List<POI>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.poisByBuilding(buildingId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Building POIs response: $jsonString")
                            val type = object : TypeToken<List<POI>>() {}.type
                            gson.fromJson<List<POI>>(jsonString, type)
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch building POIs: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching building POIs", e)
                null
            }
        }
    }
    
    /**
     * Fetch POIs for a specific building as GeoJSON
     */
    suspend fun getPOIsByBuildingAsGeoJSON(buildingId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.poisByBuilding(buildingId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Building POI GeoJSON response: $jsonString")
                            jsonString
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch building POI GeoJSON: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching building POI GeoJSON", e)
                null
            }
        }
    }
    
    /**
     * Fetch beacons for a specific floor
     */
    suspend fun getBeaconsByFloor(floorId: Int): List<Beacon>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.beaconsByFloor(floorId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Beacons response: $jsonString")
                            val type = object : TypeToken<List<Beacon>>() {}.type
                            gson.fromJson<List<Beacon>>(jsonString, type)
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch beacons: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching beacons", e)
                null
            }
        }
    }
    
    /**
     * Fetch route nodes for a specific floor
     */
    suspend fun getRouteNodesByFloor(floorId: Int): List<RouteNode>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.routeNodesByFloor(floorId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Route nodes response: $jsonString")
                            val type = object : TypeToken<List<RouteNode>>() {}.type
                            gson.fromJson<List<RouteNode>>(jsonString, type)
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch route nodes: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching route nodes", e)
                null
            }
        }
    }
    
    /**
     * Fetch all POIs as raw GeoJSON string
     */
    suspend fun getAllPOIsAsGeoJSON(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.POIS}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "POI GeoJSON response: $jsonString")
                            jsonString
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch POI GeoJSON: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching POI GeoJSON", e)
                null
            }
        }
    }
    
    /**
     * Fetch POIs for a specific floor as GeoJSON
     */
    suspend fun getPOIsByFloorAsGeoJSON(floorId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.poisByFloor(floorId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Floor POI GeoJSON response: $jsonString")
                            jsonString
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch floor POI GeoJSON: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching floor POI GeoJSON", e)
                null
            }
        }
    }
    
    /**
     * Fetch beacons for a specific floor as GeoJSON
     */
    suspend fun getBeaconsByFloorAsGeoJSON(floorId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.beaconsByFloor(floorId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Beacon GeoJSON response: $jsonString")
                            jsonString
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch beacon GeoJSON: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching beacon GeoJSON", e)
                null
            }
        }
    }
    
    /**
     * Fetch route nodes for a specific floor as GeoJSON
     */
    suspend fun getRouteNodesByFloorAsGeoJSON(floorId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.routeNodesByFloor(floorId)}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Route node GeoJSON response: $jsonString")
                            jsonString
                        } else null
                    } else {
                        Log.e(TAG, "Failed to fetch route node GeoJSON: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching route node GeoJSON", e)
                null
            }
        }
    }

    /**
     * Assign a visitor ID via the load balancer
     */
    suspend fun assignVisitor(): VisitorAssignment? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.ASSIGN_VISITOR}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Visitor assignment response: $jsonString")
                            gson.fromJson(jsonString, VisitorAssignment::class.java)
                        } else null
                    } else {
                        Log.e(TAG, "Failed to assign visitor: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error assigning visitor", e)
                null
            }
        }
    }

    /**
     * Request user assignment from backend (Load Balancer format)
     */
    suspend fun requestUserAssignment(
        level: Int, 
        visitorId: String, 
        age: Int, 
        isDisabled: Boolean
    ): UserAssignment? {
        return withContext(Dispatchers.IO) {
            try {
                // Create the decision object
                val decision = AssignmentDecision(
                    isDisabled = isDisabled,
                    age = age,
                    ageCutoff = null,  // Backend will use defaults
                    alpha1 = null,
                    pDisabled = null,
                    shareLeftForOld = null,
                    tauQuantile = null,
                    occupancy = null,
                    reason = null
                )

                // Create the assignment request
                val assignmentRequest = AssignmentRequest(
                    level = level,
                    visitorId = visitorId,
                    decision = decision,
                    traceId = null  // Optional trace ID
                )

                val requestBody = RequestBody.create(
                    "application/json".toMediaType(),
                    gson.toJson(assignmentRequest)
                )

                Log.d(TAG, "Sending assignment request: ${gson.toJson(assignmentRequest)}")

                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.ASSIGN_VISITOR}")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "User assignment response: $jsonString")
                            gson.fromJson(jsonString, UserAssignment::class.java)
                        } else null
                    } else {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "Failed to request user assignment: ${response.code} - ${response.message}")
                        Log.e(TAG, "Error body: $errorBody")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting user assignment", e)
                null
            }
        }
    }

    /**
     * Find path from user location to a POI
     */
    suspend fun findPath(pathRequest: PathRequest): FeatureCollection? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = RequestBody.create(
                    "application/json".toMediaType(),
                    gson.toJson(pathRequest)
                )
                
                val request = Request.Builder()
                    .url("${ApiConstants.API_BASE_URL}${ApiConstants.Endpoints.FIND_PATH}")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string()
                        if (jsonString != null) {
                            Log.d(TAG, "Path response: $jsonString")
                            
                            // Handle different response formats
                            val featureCollection = if (jsonString.trim().startsWith("[")) {
                                // Response is an array of features - wrap in FeatureCollection
                                val featureCollectionJson = """{"type": "FeatureCollection", "features": $jsonString}"""
                                FeatureCollection.fromJson(featureCollectionJson)
                            } else {
                                // Response is already a FeatureCollection
                                FeatureCollection.fromJson(jsonString)
                            }
                            
                            featureCollection
                        } else null
                    } else {
                        Log.e(TAG, "Failed to find path: ${response.code} - ${response.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding path", e)
                null
            }
        }
    }
    
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
    }
}