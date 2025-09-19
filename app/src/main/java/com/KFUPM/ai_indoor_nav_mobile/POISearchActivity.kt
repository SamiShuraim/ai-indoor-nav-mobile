package com.KFUPM.ai_indoor_nav_mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import com.KFUPM.ai_indoor_nav_mobile.services.ApiService

class POISearchActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: POIAdapter
    private val apiService = ApiService()
    private var buildingId: Int? = null
    private var buildingName: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poi_search)
        
        // Get building information from intent
        buildingId = intent.getIntExtra("building_id", -1).takeIf { it != -1 }
        buildingName = intent.getStringExtra("building_name")
        
        Log.d("POISearchActivity", "Building ID: $buildingId, Building Name: $buildingName")
        
        setupRecyclerView()
        fetchPOIs()
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        adapter = POIAdapter { poi ->
            // Handle POI click - return selected POI to MainActivity
            val name = poi.getStringProperty("name") ?: "Unknown POI"
            val poiId = poi.getNumberProperty("id")?.toInt()
            
            if (poiId != null) {
                Log.d("POISearchActivity", "Selected POI: $name (ID: $poiId)")
                
                val resultIntent = Intent().apply {
                    putExtra("poi_id", poiId)
                    putExtra("poi_name", name)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Invalid POI selected", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun fetchPOIs() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geoJsonString = if (buildingId != null) {
                    Log.d("POISearchActivity", "Fetching POIs for building: $buildingId")
                    apiService.getPOIsByBuildingAsGeoJSON(buildingId!!)
                } else {
                    Log.d("POISearchActivity", "No building ID provided, fetching all POIs")
                    apiService.getAllPOIsAsGeoJSON()
                }

                Log.d("POISearchActivity", "Received GeoJSON: $geoJsonString")
                
                if (geoJsonString != null) {
                    val featureCollection = if (geoJsonString.trim().startsWith("[")) {
                        val featureCollectionJson = """{"type": "FeatureCollection", "features": $geoJsonString}"""
                        FeatureCollection.fromJson(featureCollectionJson)
                    } else {
                        FeatureCollection.fromJson(geoJsonString)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val features = featureCollection.features() ?: emptyList()
                        Log.d("POISearchActivity", "Loaded ${features.size} POIs")
                        adapter.updatePOIs(features)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.w("POISearchActivity", "No POI data received")
                        Toast.makeText(this@POISearchActivity, "No POIs found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("POISearchActivity", "Error fetching POIs: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@POISearchActivity, "Error loading POIs", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        apiService.cleanup()
    }
}