package com.KFUPM.ai_indoor_nav_mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.KFUPM.ai_indoor_nav_mobile.BuildConfig
import com.KFUPM.ai_indoor_nav_mobile.R
import com.KFUPM.ai_indoor_nav_mobile.models.*
import com.KFUPM.ai_indoor_nav_mobile.services.ApiService
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.utils.ColorUtils
import org.maplibre.geojson.*
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
    private lateinit var floorSelectorContainer: LinearLayout
    private lateinit var floorRecyclerView: RecyclerView
    private lateinit var floorSelectorAdapter: FloorSelectorAdapter
    
    private val apiService = ApiService()
    
    // Data
    private var currentBuilding: Building? = null
    private var floors: List<Floor> = emptyList()
    private var currentFloor: Floor? = null
    private var currentPOIs: List<POI> = emptyList()
    private var currentBeacons: List<Beacon> = emptyList()
    private var currentRouteNodes: List<RouteNode> = emptyList()

    // Source and layer IDs
    private val poiSourceId = "poi-source"
    private val beaconSourceId = "beacon-source"
    private val routeNodeSourceId = "route-node-source"
    private val poiFillLayerId = "poi-fill-layer"
    private val poiStrokeLayerId = "poi-stroke-layer"
    private val beaconLayerId = "beacon-layer"
    private val routeNodeLayerId = "route-node-layer"
    
    companion object {
        private const val TAG = "MainActivity"
    }

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
        floorSelectorContainer = findViewById(R.id.floorSelectorContainer)
        floorRecyclerView = findViewById(R.id.floorRecyclerView)
        
        setupFloorSelector()
        mapView.onCreate(savedInstanceState)
        setupButtonListeners()

        Log.d(TAG, "tileUrl: ${BuildConfig.tileUrl}")

        mapView.getMapAsync { maplibreMap ->
            mapLibreMap = maplibreMap
            mapLibreMap.setStyle(
                Style.Builder().fromUri(BuildConfig.tileUrl)
            ) {
                checkLocationPermission()
                initializeAppData()
            }
        }
    }

    private fun setupFloorSelector() {
        floorSelectorAdapter = FloorSelectorAdapter(emptyList()) { floor ->
            onFloorSelected(floor)
        }
        floorRecyclerView.layoutManager = LinearLayoutManager(this)
        floorRecyclerView.adapter = floorSelectorAdapter
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

    /**
     * Initialize app data by fetching buildings and selecting the first one
     */
    private fun initializeAppData() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching buildings...")
                val buildings = apiService.getBuildings()
                
                if (buildings.isNullOrEmpty()) {
                    Log.w(TAG, "No buildings found")
                    return@launch
                }
                
                // Select the first building
                currentBuilding = buildings.first()
                Log.d(TAG, "Selected building: ${currentBuilding?.name}")
                
                // Fetch floors for the selected building
                fetchFloorsForBuilding(currentBuilding!!.id)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing app data", e)
            }
        }
    }

    /**
     * Fetch floors for the given building
     */
    private suspend fun fetchFloorsForBuilding(buildingId: String) {
        try {
            Log.d(TAG, "Fetching floors for building: $buildingId")
            val buildingFloors = apiService.getFloorsByBuilding(buildingId)
            
            if (buildingFloors.isNullOrEmpty()) {
                Log.w(TAG, "No floors found for building: $buildingId")
                return
            }
            
            floors = buildingFloors.sortedBy { it.level }
            Log.d(TAG, "Found ${floors.size} floors")
            
            // Update floor selector
            floorSelectorAdapter.updateFloors(floors)
            floorSelectorContainer.visibility = View.VISIBLE
            
            // Select the first floor
            if (floors.isNotEmpty()) {
                selectFloor(floors.first())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching floors", e)
        }
    }

    /**
     * Handle floor selection
     */
    private fun onFloorSelected(floor: Floor) {
        Log.d(TAG, "Floor selected: ${floor.name} (id: ${floor.id})")
        selectFloor(floor)
    }

    /**
     * Select and load data for the given floor
     */
    private fun selectFloor(floor: Floor) {
        currentFloor = floor
        floorSelectorAdapter.setSelectedFloor(floor.id)
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading data for floor: ${floor.name}")
                
                // Fetch all data for the floor in parallel
                val poisDeferred = async { apiService.getPOIsByFloor(floor.id) }
                val beaconsDeferred = async { apiService.getBeaconsByFloor(floor.id) }
                val routeNodesDeferred = async { apiService.getRouteNodesByFloor(floor.id) }
                
                currentPOIs = poisDeferred.await() ?: emptyList()
                currentBeacons = beaconsDeferred.await() ?: emptyList()
                currentRouteNodes = routeNodesDeferred.await() ?: emptyList()
                
                Log.d(TAG, "Loaded ${currentPOIs.size} POIs, ${currentBeacons.size} beacons, ${currentRouteNodes.size} route nodes")
                
                // Update map display
                updateMapDisplay()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading floor data", e)
            }
        }
    }

    /**
     * Update the map display with current floor data
     */
    private fun updateMapDisplay() {
        val style = mapLibreMap.style ?: return
        
        try {
            // Clear existing layers and sources
            clearMapLayers(style)
            
            // Add POIs
            if (currentPOIs.isNotEmpty()) {
                addPOIsToMap(style, currentPOIs)
            }
            
            // Add beacons
            if (currentBeacons.isNotEmpty()) {
                addBeaconsToMap(style, currentBeacons)
            }
            
            // Add route nodes
            if (currentRouteNodes.isNotEmpty()) {
                addRouteNodesToMap(style, currentRouteNodes)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating map display", e)
        }
    }

    /**
     * Clear existing map layers and sources
     */
    private fun clearMapLayers(style: Style) {
        // Remove layers
        listOf(poiFillLayerId, poiStrokeLayerId, beaconLayerId, routeNodeLayerId).forEach { layerId ->
            style.getLayer(layerId)?.let { style.removeLayer(layerId) }
        }
        
        // Remove sources
        listOf(poiSourceId, beaconSourceId, routeNodeSourceId).forEach { sourceId ->
            style.getSource(sourceId)?.let { style.removeSource(sourceId) }
        }
    }

    /**
     * Add POIs to the map
     */
    private fun addPOIsToMap(style: Style, pois: List<POI>) {
        try {
            val features = mutableListOf<Feature>()
            
            pois.forEach { poi ->
                if (poi.geometry != null) {
                    // Use existing geometry if available
                    val feature = Feature.fromJson(poi.geometry.toString())
                    feature.addStringProperty("name", poi.name)
                    feature.addStringProperty("id", poi.id)
                    features.add(feature)
                } else {
                    // Create point feature from coordinates
                    val point = Point.fromLngLat(poi.x, poi.y)
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("name", poi.name)
                    feature.addStringProperty("id", poi.id)
                    features.add(feature)
                }
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(poiSourceId, featureCollection)
                style.addSource(source)
                
                // Add fill layer for polygons
                val fillLayer = FillLayer(poiFillLayerId, poiSourceId).withProperties(
                    fillColor("#80FF0000"),
                    fillOpacity(0.6f)
                )
                style.addLayer(fillLayer)
                
                // Add stroke layer for outlines
                val strokeLayer = LineLayer(poiStrokeLayerId, poiSourceId).withProperties(
                    lineColor("#FF0000"),
                    lineWidth(2f),
                    lineOpacity(0.8f)
                )
                style.addLayer(strokeLayer)
                
                Log.d(TAG, "Added ${features.size} POI features to map")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding POIs to map", e)
        }
    }

    /**
     * Add beacons to the map
     */
    private fun addBeaconsToMap(style: Style, beacons: List<Beacon>) {
        try {
            val features = beacons.map { beacon ->
                val point = Point.fromLngLat(beacon.x, beacon.y)
                val feature = Feature.fromGeometry(point)
                feature.addStringProperty("name", beacon.name ?: "Beacon ${beacon.id}")
                feature.addStringProperty("id", beacon.id)
                feature.addStringProperty("uuid", beacon.uuid)
                feature
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(beaconSourceId, featureCollection)
                style.addSource(source)
                
                // Add circle layer for beacons
                val beaconLayer = CircleLayer(beaconLayerId, beaconSourceId).withProperties(
                    circleRadius(8f),
                    circleColor("#FFA500"), // Orange
                    circleOpacity(0.8f),
                    circleStrokeWidth(2f),
                    circleStrokeColor("#FF8C00")
                )
                style.addLayer(beaconLayer)
                
                Log.d(TAG, "Added ${features.size} beacon features to map")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding beacons to map", e)
        }
    }

    /**
     * Add route nodes to the map as small blue dots
     */
    private fun addRouteNodesToMap(style: Style, routeNodes: List<RouteNode>) {
        try {
            val features = routeNodes.map { node ->
                val point = Point.fromLngLat(node.x, node.y)
                val feature = Feature.fromGeometry(point)
                feature.addStringProperty("name", node.name ?: "Node ${node.id}")
                feature.addStringProperty("id", node.id)
                feature.addStringProperty("nodeType", node.nodeType ?: "")
                feature
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(routeNodeSourceId, featureCollection)
                style.addSource(source)
                
                // Add circle layer for route nodes (small blue dots)
                val routeNodeLayer = CircleLayer(routeNodeLayerId, routeNodeSourceId).withProperties(
                    circleRadius(3f), // Small dots
                    circleColor("#0066FF"), // Blue
                    circleOpacity(0.8f),
                    circleStrokeWidth(1f),
                    circleStrokeColor("#003399")
                )
                style.addLayer(routeNodeLayer)
                
                Log.d(TAG, "Added ${features.size} route node features to map")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding route nodes to map", e)
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        apiService.cleanup()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}