package com.KFUPM.ai_indoor_nav_mobile

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import java.io.IOException

class POISearchActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: POIAdapter
    private val client = OkHttpClient()
    private val apiUrl = "http://192.168.128.223:5090/api/poi"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poi_search)
        
        setupRecyclerView()
        fetchPOIs()
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        adapter = POIAdapter { poi ->
            // Handle POI click - for now just show a toast
            val name = poi.getStringProperty("name") ?: "Unknown POI"
            Toast.makeText(this, "Clicked: $name", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun fetchPOIs() {
        val request = Request.Builder()
            .url(apiUrl)
            .build()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("POISearchActivity", "Failed to fetch POIs: ${response.code}")
                        return@use
                    }
                    
                    val jsonString = response.body?.string()
                    if (jsonString != null) {
                        val featureCollection = if (jsonString.trim().startsWith("[")) {
                            val featureCollectionJson = """{"type": "FeatureCollection", "features": $jsonString}"""
                            FeatureCollection.fromJson(featureCollectionJson)
                        } else {
                            FeatureCollection.fromJson(jsonString)
                        }
                        
                        withContext(Dispatchers.Main) {
                            adapter.updatePOIs(featureCollection.features() ?: emptyList())
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("POISearchActivity", "Network error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@POISearchActivity, "Failed to fetch POIs", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("POISearchActivity", "Error parsing POIs: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@POISearchActivity, "Error loading POIs", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        client.dispatcher.executorService.shutdown()
    }
}