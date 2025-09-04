package com.KFUPM.ai_indoor_nav_mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.KFUPM.ai_indoor_nav_mobile.BuildConfig
import com.KFUPM.ai_indoor_nav_mobile.R
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.utils.ColorUtils
import org.maplibre.geojson.FeatureCollection
import kotlinx.coroutines.*
import okhttp3.*
import org.maplibre.android.WellKnownTileServer
import java.io.IOException
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    private lateinit var fabBluetooth: FloatingActionButton
    private lateinit var fabSearch: FloatingActionButton
    private val client = OkHttpClient()
    private val apiUrl = "http://192.168.239.223:5090/api/poi"

    // Source and layer IDs
    private val geoJsonSourceId = "poi-source"
    private val polygonFillLayerId = "poi-fill-layer"
    private val polygonStrokeLayerId = "poi-stroke-layer"

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableUserLocation()
            } else {
                // TODO: Handle permission denied (optional)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre with DEMO tile server
        MapLibre.getInstance(
            this,
            "",
            WellKnownTileServer.MapLibre
        )

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        fabBluetooth = findViewById(R.id.fabBluetooth)
        fabSearch = findViewById(R.id.fabSearch)
        
        mapView.onCreate(savedInstanceState)
        
        setupButtonListeners()

        Log.d("MyAppTag", "tileUrl: ${BuildConfig.tileUrl}")

        mapView.getMapAsync { maplibreMap ->
            mapLibreMap = maplibreMap
            mapLibreMap.setStyle(
                Style.Builder().fromUri(BuildConfig.tileUrl)
            ) {
                checkLocationPermission()
                fetchAndDisplayGeoJson()
            }
        }
    }

    private fun setupButtonListeners() {
        fabBluetooth.setOnClickListener {
            val intent = Intent(this, BluetoothDevicesActivity::class.java)
            startActivity(intent)
        }
        
        fabSearch.setOnClickListener {
            val intent = Intent(this, POISearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableUserLocation()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun enableUserLocation() {
        val locationComponent = mapLibreMap.locationComponent
        locationComponent.activateLocationComponent(
            org.maplibre.android.location.LocationComponentActivationOptions.builder(
                this,
                mapLibreMap.style!!
            ).build()
        )
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING

        val lastLocation = locationComponent.lastKnownLocation
        lastLocation?.let {
            mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    org.maplibre.android.geometry.LatLng(it.latitude, it.longitude),
                    16.0 // Adjust zoom level as you like
                )
            )
        }
    }

    private fun fetchAndDisplayGeoJson() {
        val request = Request.Builder()
            .url(apiUrl)
            .build()

        // Use coroutine to make async network call
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("MyAppTag", "Failed to fetch GeoJSON: ${response.code}")
                        return@use
                    }

                    val jsonString = response.body?.string()
                    if (jsonString != null) {
                        Log.d("MyAppTag", "Received JSON: $jsonString")

                        // Check if the response is an array and convert to FeatureCollection
                        val featureCollection = if (jsonString.trim().startsWith("[")) {
                            val featureCollectionJson = """{"type": "FeatureCollection", "features": $jsonString}"""
                            FeatureCollection.fromJson(featureCollectionJson)
                        } else {
                            FeatureCollection.fromJson(jsonString)
                        }

                        // Switch to main thread to update UI
                        withContext(Dispatchers.Main) {
                            displayGeoJsonFeatures(featureCollection)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("MyAppTag", "Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e("MyAppTag", "Error parsing GeoJSON: ${e.message}")
            }
        }
    }

    private fun displayGeoJsonFeatures(featureCollection: FeatureCollection) {
        val style = mapLibreMap.style
        if (style == null) {
            Log.e("MyAppTag", "Map style is null")
            return
        }

        try {
            // Add GeoJSON source
            val geoJsonSource = GeoJsonSource(geoJsonSourceId, featureCollection)
            style.addSource(geoJsonSource)

            // Add fill layer for polygon interiors
            val fillLayer = FillLayer(polygonFillLayerId, geoJsonSourceId).withProperties(
                fillColor("#80FF0000"), // red
                fillOpacity(0.83f)
            )
            style.addLayer(fillLayer)

            // Add line layer for polygon outlines
            val strokeLayer = LineLayer(polygonStrokeLayerId, geoJsonSourceId).withProperties(
                lineColor("#FF0000"), // Red outline
                lineWidth(2f),
                lineOpacity(0.8f)
            )
            style.addLayer(strokeLayer)

            Log.d("MyAppTag", "Successfully added ${featureCollection.features()?.size ?: 0} features to map")

        } catch (e: Exception) {
            Log.e("MyAppTag", "Error adding layers to map: ${e.message}")
        }
    }

    // Method to refresh GeoJSON data (you can call this when needed)
    private fun refreshGeoJsonData() {
        val style = mapLibreMap.style

        // Remove existing layers and source
        style?.let {
            if (it.getLayer(polygonStrokeLayerId) != null) {
                it.removeLayer(polygonStrokeLayerId)
            }
            if (it.getLayer(polygonFillLayerId) != null) {
                it.removeLayer(polygonFillLayerId)
            }
            if (it.getSource(geoJsonSourceId) != null) {
                it.removeSource(geoJsonSourceId)
            }
        }

        // Fetch and display updated data
        fetchAndDisplayGeoJson()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        // Clean up HTTP client
        client.dispatcher.executorService.shutdown()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}