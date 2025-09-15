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
                    Log.w(TAG, "No buildings found, trying legacy POI endpoint...")
                    // Fall back to legacy approach
                    fetchLegacyPOIs()
                    return@launch
                }
                
                // Select the first building
                currentBuilding = buildings.first()
                Log.d(TAG, "Selected building: ${currentBuilding?.name}")
                
                // Fetch floors for the selected building
                fetchFloorsForBuilding(currentBuilding!!.id)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing app data, trying legacy approach", e)
                fetchLegacyPOIs()
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
                
                // Debug coordinate information
                currentPOIs.forEach { poi ->
                    Log.d(TAG, "POI ${poi.name}: x=${poi.x}, y=${poi.y}, lat=${poi.latitude}, lng=${poi.longitude}")
                }
                currentBeacons.take(3).forEach { beacon -> // Only log first 3 to avoid spam
                    Log.d(TAG, "Beacon ${beacon.name}: x=${beacon.x}, y=${beacon.y}, lat=${beacon.latitude}, lng=${beacon.longitude}")
                }
                currentRouteNodes.take(3).forEach { node ->
                    Log.d(TAG, "Route node ${node.name}: x=${node.x}, y=${node.y}, lat=${node.latitude}, lng=${node.longitude}")
                }
                
                // Update map display
                updateMapDisplay()
                
                // Fit map to show all features
                fitMapToFeatures()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading floor data", e)
            }
        }
    }

    /**
     * Update the map display with current floor data
     */
    private fun updateMapDisplay() {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            Log.w(TAG, "Map style not ready, skipping update")
            return
        }
        
        try {
            Log.d(TAG, "Updating map display...")
            
            // Clear existing layers and sources
            clearMapLayers(style)
            
            // Add POIs
            if (currentPOIs.isNotEmpty()) {
                addPOIsToMap(style, currentPOIs)
            } else {
                Log.d(TAG, "No POIs to display")
            }
            
            // Add beacons
            if (currentBeacons.isNotEmpty()) {
                addBeaconsToMap(style, currentBeacons)
            } else {
                Log.d(TAG, "No beacons to display")
            }
            
            // Add route nodes
            if (currentRouteNodes.isNotEmpty()) {
                addRouteNodesToMap(style, currentRouteNodes)
            } else {
                Log.d(TAG, "No route nodes to display")
            }
            
            Log.d(TAG, "Map display update completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating map display", e)
        }
    }

    /**
     * Clear existing map layers and sources
     */
    private fun clearMapLayers(style: Style) {
        try {
            // Remove layers first (order matters - layers must be removed before sources)
            listOf(poiFillLayerId, poiStrokeLayerId, beaconLayerId, routeNodeLayerId).forEach { layerId ->
                try {
                    if (style.getLayer(layerId) != null) {
                        style.removeLayer(layerId)
                        Log.d(TAG, "Removed layer: $layerId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove layer $layerId", e)
                }
            }
            
            // Remove sources after layers
            listOf(poiSourceId, beaconSourceId, routeNodeSourceId).forEach { sourceId ->
                try {
                    if (style.getSource(sourceId) != null) {
                        style.removeSource(sourceId)
                        Log.d(TAG, "Removed source: $sourceId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove source $sourceId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing map layers", e)
        }
    }

    /**
     * Add POIs to the map
     */
    private fun addPOIsToMap(style: Style, pois: List<POI>) {
        try {
            if (pois.isEmpty()) {
                Log.d(TAG, "No POIs to add")
                return
            }
            
            // Ensure source doesn't already exist
            if (style.getSource(poiSourceId) != null) {
                Log.w(TAG, "POI source already exists, skipping")
                return
            }
            
            val features = mutableListOf<Feature>()
            
            pois.forEach { poi ->
                try {
                    if (poi.geometry != null) {
                        // Use existing geometry if available
                        val feature = Feature.fromJson(poi.geometry.toString())
                        feature.addStringProperty("name", poi.name)
                        feature.addStringProperty("id", poi.id)
                        features.add(feature)
                    } else {
                        // Create point feature from coordinates
                        // Use lat/lng if available, otherwise fall back to x/y
                        val point = if (poi.latitude != null && poi.longitude != null) {
                            Point.fromLngLat(poi.longitude, poi.latitude)
                        } else {
                            Point.fromLngLat(poi.x, poi.y)
                        }
                        val feature = Feature.fromGeometry(point)
                        feature.addStringProperty("name", poi.name)
                        feature.addStringProperty("id", poi.id)
                        features.add(feature)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create feature for POI ${poi.id}", e)
                }
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(poiSourceId, featureCollection)
                
                // Add source
                style.addSource(source)
                Log.d(TAG, "Added POI source with ${features.size} features")
                
                // Add fill layer for polygons (only if layer doesn't exist)
                if (style.getLayer(poiFillLayerId) == null) {
                    val fillLayer = FillLayer(poiFillLayerId, poiSourceId).withProperties(
                        fillColor("#80FF0000")
                    )
                    style.addLayer(fillLayer)
                    Log.d(TAG, "Added POI fill layer")
                }
                
                // Add stroke layer for outlines (only if layer doesn't exist)
                if (style.getLayer(poiStrokeLayerId) == null) {
                    val strokeLayer = LineLayer(poiStrokeLayerId, poiSourceId).withProperties(
                        lineColor("#FF0000"),
                        lineWidth(2f)
                    )
                    style.addLayer(strokeLayer)
                    Log.d(TAG, "Added POI stroke layer")
                }
                
                Log.d(TAG, "Successfully added ${features.size} POI features to map")
            } else {
                Log.w(TAG, "No valid POI features created")
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
            if (beacons.isEmpty()) {
                Log.d(TAG, "No beacons to add")
                return
            }
            
            // Ensure source doesn't already exist
            if (style.getSource(beaconSourceId) != null) {
                Log.w(TAG, "Beacon source already exists, skipping")
                return
            }
            
            val features = beacons.mapNotNull { beacon ->
                try {
                    // Use lat/lng if available, otherwise fall back to x/y
                    val point = if (beacon.latitude != null && beacon.longitude != null) {
                        Point.fromLngLat(beacon.longitude, beacon.latitude)
                    } else {
                        Point.fromLngLat(beacon.x, beacon.y)
                    }
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("name", beacon.name ?: "Beacon ${beacon.id}")
                    feature.addStringProperty("id", beacon.id)
                    feature.addStringProperty("uuid", beacon.uuid)
                    feature
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create feature for beacon ${beacon.id}", e)
                    null
                }
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(beaconSourceId, featureCollection)
                
                // Add source
                style.addSource(source)
                Log.d(TAG, "Added beacon source with ${features.size} features")
                
                // Add circle layer for beacons (only if layer doesn't exist)
                if (style.getLayer(beaconLayerId) == null) {
                    val beaconLayer = CircleLayer(beaconLayerId, beaconSourceId).withProperties(
                        circleRadius(8f),
                        circleColor("#FFA500"), // Orange
                        circleStrokeWidth(2f),
                        circleStrokeColor("#FF8C00")
                    )
                    style.addLayer(beaconLayer)
                    Log.d(TAG, "Added beacon layer")
                }
                
                Log.d(TAG, "Successfully added ${features.size} beacon features to map")
            } else {
                Log.w(TAG, "No valid beacon features created")
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
            if (routeNodes.isEmpty()) {
                Log.d(TAG, "No route nodes to add")
                return
            }
            
            // Ensure source doesn't already exist
            if (style.getSource(routeNodeSourceId) != null) {
                Log.w(TAG, "Route node source already exists, skipping")
                return
            }
            
            val features = routeNodes.mapNotNull { node ->
                try {
                    // Use lat/lng if available, otherwise fall back to x/y
                    val point = if (node.latitude != null && node.longitude != null) {
                        Point.fromLngLat(node.longitude, node.latitude)
                    } else {
                        Point.fromLngLat(node.x, node.y)
                    }
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("name", node.name ?: "Node ${node.id}")
                    feature.addStringProperty("id", node.id)
                    feature.addStringProperty("nodeType", node.nodeType ?: "")
                    feature
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create feature for route node ${node.id}", e)
                    null
                }
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(routeNodeSourceId, featureCollection)
                
                // Add source
                style.addSource(source)
                Log.d(TAG, "Added route node source with ${features.size} features")
                
                // Add circle layer for route nodes (only if layer doesn't exist)
                if (style.getLayer(routeNodeLayerId) == null) {
                    val routeNodeLayer = CircleLayer(routeNodeLayerId, routeNodeSourceId).withProperties(
                        circleRadius(3f), // Small dots
                        circleColor("#0066FF"), // Blue
                        circleStrokeWidth(1f),
                        circleStrokeColor("#003399")
                    )
                    style.addLayer(routeNodeLayer)
                    Log.d(TAG, "Added route node layer")
                }
                
                Log.d(TAG, "Successfully added ${features.size} route node features to map")
            } else {
                Log.w(TAG, "No valid route node features created")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding route nodes to map", e)
        }
    }

    /**
     * Legacy POI fetching method (fallback)
     */
    private fun fetchLegacyPOIs() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching POIs using legacy endpoint...")
                val pois = apiService.getAllPOIs()
                
                if (pois.isNullOrEmpty()) {
                    Log.w(TAG, "No POIs found in legacy endpoint")
                    return@launch
                }
                
                currentPOIs = pois
                Log.d(TAG, "Loaded ${currentPOIs.size} POIs from legacy endpoint")
                
                // Debug coordinate information
                currentPOIs.take(5).forEach { poi ->
                    Log.d(TAG, "POI ${poi.name}: x=${poi.x}, y=${poi.y}, lat=${poi.latitude}, lng=${poi.longitude}")
                }
                
                // Display using simple approach
                displayLegacyPOIs()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching legacy POIs", e)
            }
        }
    }

    /**
     * Display POIs using the original simple approach
     */
    private fun displayLegacyPOIs() {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            Log.w(TAG, "Map style not ready for legacy POI display")
            return
        }
        
        try {
            Log.d(TAG, "Displaying legacy POIs...")
            
            val features = mutableListOf<Feature>()
            
            currentPOIs.forEach { poi ->
                try {
                    if (poi.geometry != null) {
                        val feature = Feature.fromJson(poi.geometry.toString())
                        feature.addStringProperty("name", poi.name)
                        feature.addStringProperty("id", poi.id)
                        features.add(feature)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create legacy feature for POI ${poi.id}", e)
                }
            }
            
            if (features.isNotEmpty()) {
                // Check if the response is an array and convert to FeatureCollection
                val featureCollection = FeatureCollection.fromFeatures(features)
                
                // Remove existing layers first
                try {
                    style.getLayer("poi-fill-layer")?.let { style.removeLayer("poi-fill-layer") }
                    style.getLayer("poi-stroke-layer")?.let { style.removeLayer("poi-stroke-layer") }
                    style.getSource("poi-source")?.let { style.removeSource("poi-source") }
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing existing legacy layers", e)
                }
                
                // Add GeoJSON source
                val geoJsonSource = GeoJsonSource("poi-source", featureCollection)
                style.addSource(geoJsonSource)
                
                // Add fill layer for polygon interiors
                val fillLayer = FillLayer("poi-fill-layer", "poi-source").withProperties(
                    fillColor("#80FF0000") // red
                )
                style.addLayer(fillLayer)
                
                // Add line layer for polygon outlines
                val strokeLayer = LineLayer("poi-stroke-layer", "poi-source").withProperties(
                    lineColor("#FF0000"), // Red outline
                    lineWidth(2f)
                )
                style.addLayer(strokeLayer)
                
                Log.d(TAG, "Successfully added ${features.size} legacy POI features to map")
                
                // Try to fit map to features
                fitMapToLegacyFeatures(features)
            } else {
                Log.w(TAG, "No valid legacy POI features created")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying legacy POIs", e)
        }
    }

    /**
     * Fit map to legacy features
     */
    private fun fitMapToLegacyFeatures(features: List<Feature>) {
        try {
            if (features.isEmpty()) return
            
            // Extract coordinates from features
            val coordinates = mutableListOf<org.maplibre.android.geometry.LatLng>()
            
            features.forEach { feature ->
                val geometry = feature.geometry()
                if (geometry is Point) {
                    coordinates.add(org.maplibre.android.geometry.LatLng(geometry.latitude(), geometry.longitude()))
                }
            }
            
            if (coordinates.isNotEmpty()) {
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                coordinates.forEach { bounds.include(it) }
                
                val padding = 100
                mapLibreMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), padding),
                    1000
                )
                
                Log.d(TAG, "Fitted map to ${coordinates.size} legacy coordinates")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fitting map to legacy features", e)
        }
    }

    /**
     * Fit the map view to show all loaded features
     */
    private fun fitMapToFeatures() {
        try {
            val allCoordinates = mutableListOf<Point>()
            
            // Collect all coordinates
            currentPOIs.forEach { poi ->
                val point = if (poi.latitude != null && poi.longitude != null) {
                    Point.fromLngLat(poi.longitude, poi.latitude)
                } else {
                    Point.fromLngLat(poi.x, poi.y)
                }
                allCoordinates.add(point)
            }
            
            currentBeacons.forEach { beacon ->
                val point = if (beacon.latitude != null && beacon.longitude != null) {
                    Point.fromLngLat(beacon.longitude, beacon.latitude)
                } else {
                    Point.fromLngLat(beacon.x, beacon.y)
                }
                allCoordinates.add(point)
            }
            
            currentRouteNodes.forEach { node ->
                val point = if (node.latitude != null && node.longitude != null) {
                    Point.fromLngLat(node.longitude, node.latitude)
                } else {
                    Point.fromLngLat(node.x, node.y)
                }
                allCoordinates.add(point)
            }
            
            if (allCoordinates.isNotEmpty()) {
                // Calculate bounds
                var minLng = allCoordinates[0].longitude()
                var maxLng = allCoordinates[0].longitude()
                var minLat = allCoordinates[0].latitude()
                var maxLat = allCoordinates[0].latitude()
                
                allCoordinates.forEach { point ->
                    minLng = minOf(minLng, point.longitude())
                    maxLng = maxOf(maxLng, point.longitude())
                    minLat = minOf(minLat, point.latitude())
                    maxLat = maxOf(maxLat, point.latitude())
                }
                
                Log.d(TAG, "Feature bounds: lng[$minLng, $maxLng], lat[$minLat, $maxLat]")
                
                // Create bounds and fit camera
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                    .include(org.maplibre.android.geometry.LatLng(minLat, minLng))
                    .include(org.maplibre.android.geometry.LatLng(maxLat, maxLng))
                    .build()
                
                // Add padding and animate to bounds
                val padding = 100 // pixels
                mapLibreMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding),
                    1000 // animation duration in ms
                )
                
                Log.d(TAG, "Map fitted to ${allCoordinates.size} features")
            } else {
                Log.w(TAG, "No coordinates to fit map to")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fitting map to features", e)
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