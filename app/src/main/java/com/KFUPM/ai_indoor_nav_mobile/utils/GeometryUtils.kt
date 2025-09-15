package com.KFUPM.ai_indoor_nav_mobile.utils

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object GeometryUtils {
    private const val TAG = "GeometryUtils"
    
    /**
     * Extract coordinates from PostGIS geometry
     */
    fun extractCoordinatesFromGeometry(geometry: Any?): Pair<Double, Double>? {
        if (geometry == null) return null
        
        try {
            val geometryString = geometry.toString()
            Log.d(TAG, "Parsing geometry: $geometryString")
            
            // Try to parse as GeoJSON
            val jsonElement = JsonParser.parseString(geometryString)
            if (jsonElement.isJsonObject) {
                val geoJson = jsonElement.asJsonObject
                val type = geoJson.get("type")?.asString
                
                when (type) {
                    "Point" -> {
                        val coordinates = geoJson.getAsJsonArray("coordinates")
                        if (coordinates.size() >= 2) {
                            val lng = coordinates[0].asDouble
                            val lat = coordinates[1].asDouble
                            Log.d(TAG, "Extracted Point coordinates: lng=$lng, lat=$lat")
                            return Pair(lng, lat)
                        }
                    }
                    "Polygon" -> {
                        val coordinates = geoJson.getAsJsonArray("coordinates")
                        if (coordinates.size() > 0) {
                            val ring = coordinates[0].asJsonArray
                            if (ring.size() > 0) {
                                val firstPoint = ring[0].asJsonArray
                                if (firstPoint.size() >= 2) {
                                    val lng = firstPoint[0].asDouble
                                    val lat = firstPoint[1].asDouble
                                    Log.d(TAG, "Extracted Polygon first point: lng=$lng, lat=$lat")
                                    return Pair(lng, lat)
                                }
                            }
                        }
                    }
                }
            }
            
            // Try to parse as WKT (Well-Known Text) format
            if (geometryString.startsWith("POINT")) {
                val coords = extractWKTPointCoordinates(geometryString)
                if (coords != null) {
                    Log.d(TAG, "Extracted WKT Point coordinates: lng=${coords.first}, lat=${coords.second}")
                    return coords
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse geometry: $geometry", e)
        }
        
        return null
    }
    
    /**
     * Extract coordinates from WKT Point format
     */
    private fun extractWKTPointCoordinates(wkt: String): Pair<Double, Double>? {
        try {
            // POINT(lng lat) or POINT (lng lat)
            val regex = """POINT\s*\(\s*([-+]?\d*\.?\d+)\s+([-+]?\d*\.?\d+)\s*\)""".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(wkt)
            
            if (match != null) {
                val lng = match.groupValues[1].toDouble()
                val lat = match.groupValues[2].toDouble()
                return Pair(lng, lat)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WKT: $wkt", e)
        }
        
        return null
    }
    
    /**
     * Create a MapLibre Feature from PostGIS geometry
     */
    fun createFeatureFromGeometry(geometry: Any?, name: String, id: String): Feature? {
        if (geometry == null) return null
        
        try {
            val geometryString = geometry.toString()
            
            // First try to parse as GeoJSON directly
            return try {
                Feature.fromJson(geometryString)
            } catch (e: Exception) {
                Log.d(TAG, "Not valid GeoJSON, trying coordinate extraction")
                
                // Extract coordinates and create a point feature
                val coords = extractCoordinatesFromGeometry(geometry)
                if (coords != null) {
                    val point = Point.fromLngLat(coords.first, coords.second)
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("name", name)
                    feature.addStringProperty("id", id)
                    feature
                } else {
                    null
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create feature from geometry", e)
            return null
        }
    }
}