package com.KFUPM.ai_indoor_nav_mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
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
    
    // Path navigation IDs
    private val pathNodesSourceId = "path-nodes-source"
    private val pathEdgesSourceId = "path-edges-source"
    private val pathNodesLayerId = "path-nodes-layer"
    private val pathEdgesLayerId = "path-edges-layer"
    
    // Clear path button
    private lateinit var fabClearPath: FloatingActionButton
    
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
    
    private val poiSearchLauncher = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val poiId = data?.getIntExtra("poi_id", -1)
                val poiName = data?.getStringExtra("poi_name")
                
                if (poiId != null && poiId != -1 && poiName != null) {
                    Log.d(TAG, "POI selected: $poiName (ID: $poiId)")
                    handleNavigationToPOI(poiId, poiName)
                }
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
        fabClearPath = findViewById(R.id.fabClearPath)
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
            // Pass current building ID if available
            currentBuilding?.let { building ->
                intent.putExtra("building_id", building.id)
                intent.putExtra("building_name", building.name)
            }
            poiSearchLauncher.launch(intent)
        }
        
        fabClearPath.setOnClickListener {
            clearPath()
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
    private suspend fun fetchFloorsForBuilding(buildingId: Int) {
        try {
            Log.d(TAG, "Fetching floors for building: $buildingId")
            val buildingFloors = apiService.getFloorsByBuilding(buildingId)
            
            if (buildingFloors.isNullOrEmpty()) {
                Log.w(TAG, "No floors found for building: $buildingId")
                return
            }
            
            floors = buildingFloors.sortedBy { it.floorNumber }
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
                Log.d(TAG, "Loading GeoJSON data for floor: ${floor.name}")
                
                // Fetch all GeoJSON data for the floor in parallel
                val poisDeferred = async { apiService.getPOIsByFloorAsGeoJSON(floor.id) }
                val beaconsDeferred = async { apiService.getBeaconsByFloorAsGeoJSON(floor.id) }
                val routeNodesDeferred = async { apiService.getRouteNodesByFloorAsGeoJSON(floor.id) }
                
                val poisGeoJSON = poisDeferred.await()
                val beaconsGeoJSON = beaconsDeferred.await()
                val routeNodesGeoJSON = routeNodesDeferred.await()
                
                Log.d(TAG, "Loaded GeoJSON data for floor ${floor.name}")
                
                // Update map display with all data
                updateMapDisplayWithGeoJSON(poisGeoJSON, beaconsGeoJSON, routeNodesGeoJSON)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading floor data", e)
            }
        }
    }

    /**
     * Update map display with GeoJSON data for POIs, beacons, and route nodes
     */
    private fun updateMapDisplayWithGeoJSON(poisGeoJSON: String?, beaconsGeoJSON: String?, routeNodesGeoJSON: String?) {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            Log.w(TAG, "Map style not ready for GeoJSON display")
            return
        }
        
        try {
            Log.d(TAG, "Updating map display with GeoJSON data...")
            
            // Clear all existing layers and sources
            clearAllMapLayers(style)
            
            val allFeatures = mutableListOf<Feature>()
            
            // Add POIs (red)
            if (!poisGeoJSON.isNullOrBlank()) {
                addGeoJSONLayer(style, poisGeoJSON, "poi", "#FF0000", 2f, allFeatures)
            }
            
            // Add beacons (orange circles)
            if (!beaconsGeoJSON.isNullOrBlank()) {
                addGeoJSONCircleLayer(style, beaconsGeoJSON, "beacon", "#FFA500", 8f, allFeatures)
            }
            
            // Add route nodes (small blue circles)
            if (!routeNodesGeoJSON.isNullOrBlank()) {
                addGeoJSONCircleLayer(style, routeNodesGeoJSON, "route-node", "#0066FF", 3f, allFeatures)
            }
            
            // Fit map to show all features
            if (allFeatures.isNotEmpty()) {
                fitMapToFeatureList(allFeatures)
            }
            
            Log.d(TAG, "Map display update completed with ${allFeatures.size} total features")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating map display with GeoJSON", e)
        }
    }

    /**
     * Clear all map layers and sources
     */
    private fun clearAllMapLayers(style: Style) {
        try {
            // Remove all layers
            val layerIds = listOf(
                "poi-fill-layer", "poi-stroke-layer",
                "beacon-layer", "route-node-layer",
                poiFillLayerId, poiStrokeLayerId, beaconLayerId, routeNodeLayerId
            )
            
            layerIds.forEach { layerId ->
                try {
                    if (style.getLayer(layerId) != null) {
                        style.removeLayer(layerId)
                        Log.d(TAG, "Removed layer: $layerId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove layer $layerId", e)
                }
            }
            
            // Remove all sources
            val sourceIds = listOf(
                "poi-source", "beacon-source", "route-node-source",
                poiSourceId, beaconSourceId, routeNodeSourceId
            )
            
            sourceIds.forEach { sourceId ->
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
            Log.e(TAG, "Error clearing all map layers", e)
        }
    }

    /**
     * Add GeoJSON layer for polygons/lines (POIs)
     */
    private fun addGeoJSONLayer(style: Style, geoJsonString: String, layerPrefix: String, color: String, lineWidth: Float, allFeatures: MutableList<Feature>) {
        try {
            val featureCollection = parseGeoJSONToFeatureCollection(geoJsonString)
            if (featureCollection != null) {
                val sourceId = "$layerPrefix-source"
                val fillLayerId = "$layerPrefix-fill-layer"
                val strokeLayerId = "$layerPrefix-stroke-layer"
                
                // Add source
                val source = GeoJsonSource(sourceId, featureCollection)
                style.addSource(source)
                
                // Add fill layer with 0.6 opacity
                val fillLayer = FillLayer(fillLayerId, sourceId).withProperties(
                    fillColor(color),
                    fillOpacity(0.6f)
                )
                style.addLayer(fillLayer)
                
                // Add stroke layer
                val strokeLayer = LineLayer(strokeLayerId, sourceId).withProperties(
                    lineColor(color),
                    lineWidth(lineWidth)
                )
                style.addLayer(strokeLayer)
                
                // Add features to the list for fitting
                featureCollection.features()?.let { allFeatures.addAll(it) }
                
                val featureCount = featureCollection.features()?.size ?: 0
                Log.d(TAG, "Added $featureCount $layerPrefix features")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding $layerPrefix layer", e)
        }
    }

    /**
     * Add GeoJSON circle layer for points (beacons, route nodes)
     */
    private fun addGeoJSONCircleLayer(style: Style, geoJsonString: String, layerPrefix: String, color: String, radius: Float, allFeatures: MutableList<Feature>) {
        try {
            val featureCollection = parseGeoJSONToFeatureCollection(geoJsonString)
            if (featureCollection != null) {
                val sourceId = "$layerPrefix-source"
                val layerId = "$layerPrefix-layer"
                
                // Add source
                val source = GeoJsonSource(sourceId, featureCollection)
                style.addSource(source)
                
                // Add circle layer
                val circleLayer = CircleLayer(layerId, sourceId).withProperties(
                    circleRadius(radius),
                    circleColor(color),
                    circleStrokeWidth(1f),
                    circleStrokeColor(color.replace("#", "#CC")) // Darker stroke
                )
                style.addLayer(circleLayer)
                
                // Add features to the list for fitting
                featureCollection.features()?.let { allFeatures.addAll(it) }
                
                val featureCount = featureCollection.features()?.size ?: 0
                Log.d(TAG, "Added $featureCount $layerPrefix features")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding $layerPrefix circle layer", e)
        }
    }

    /**
     * Parse GeoJSON string to FeatureCollection
     */
    private fun parseGeoJSONToFeatureCollection(geoJsonString: String): FeatureCollection? {
        return try {
            when {
                geoJsonString.trim().startsWith("[") -> {
                    // Array of features
                    val featureCollectionJson = """{"type": "FeatureCollection", "features": $geoJsonString}"""
                    FeatureCollection.fromJson(featureCollectionJson)
                }
                geoJsonString.contains("\"FeatureCollection\"") -> {
                    // Already a FeatureCollection
                    FeatureCollection.fromJson(geoJsonString)
                }
                geoJsonString.contains("\"Feature\"") -> {
                    // Single feature
                    val feature = Feature.fromJson(geoJsonString)
                    FeatureCollection.fromFeature(feature)
                }
                else -> {
                    Log.w(TAG, "Unknown GeoJSON format")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GeoJSON: $geoJsonString", e)
            null
        }
    }

    /**
     * Fit map to a list of features
     */
    private fun fitMapToFeatureList(features: List<Feature>) {
        try {
            if (features.isEmpty()) return
            
            val coordinates = mutableListOf<org.maplibre.android.geometry.LatLng>()
            
            features.forEach { feature ->
                val geometry = feature.geometry()
                when (geometry) {
                    is Point -> {
                        coordinates.add(org.maplibre.android.geometry.LatLng(geometry.latitude(), geometry.longitude()))
                    }
                    is org.maplibre.geojson.Polygon -> {
                        // For polygons, add all coordinates from the first ring
                        val rings = geometry.coordinates()
                        if (rings.isNotEmpty() && rings[0].isNotEmpty()) {
                            rings[0].forEach { point ->
                                coordinates.add(org.maplibre.android.geometry.LatLng(point.latitude(), point.longitude()))
                            }
                        }
                    }
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
                
                Log.d(TAG, "Fitted map to ${coordinates.size} feature coordinates")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fitting map to feature list", e)
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
                    val feature = if (poi.geometry != null) {
                        // Use existing geometry if available
                        try {
                            val geometryString = poi.geometry.toString()
                            if (geometryString.isNotBlank()) {
                                Feature.fromJson(geometryString)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse geometry for POI ${poi.id}", e)
                            null
                        }
                    } else {
                        // Create point feature from coordinates
                        try {
                            val point = if (poi.latitude != null && poi.longitude != null) {
                                Point.fromLngLat(poi.longitude!!, poi.latitude!!)
                            } else {
                                Point.fromLngLat(poi.x, poi.y)
                            }
                            Feature.fromGeometry(point)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create point for POI ${poi.id}", e)
                            null
                        }
                    }
                    
                    feature?.let {
                        it.addStringProperty("name", poi.name ?: "Unknown POI")
                        it.addStringProperty("id", poi.id?.toString() ?: "unknown")
                        features.add(it)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create feature for POI ${poi.id}", e)
                }
            }
            
            if (features.isNotEmpty()) {
                val featureCollection = FeatureCollection.fromFeatures(features)
                val source = GeoJsonSource(poiSourceId, featureCollection)
                
                // Add source
                try {
                    style.addSource(source)
                    Log.d(TAG, "Added POI source with ${features.size} features")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add POI source", e)
                    return
                }
                
                // Add fill layer for polygons (only if layer doesn't exist)
                try {
                    if (style.getLayer(poiFillLayerId) == null) {
                        val fillLayer = FillLayer(poiFillLayerId, poiSourceId).withProperties(
                            fillColor("#80FF0000")
                        )
                        style.addLayer(fillLayer)
                        Log.d(TAG, "Added POI fill layer")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add POI fill layer", e)
                }
                
                // Add stroke layer for outlines (only if layer doesn't exist)
                try {
                    if (style.getLayer(poiStrokeLayerId) == null) {
                        val strokeLayer = LineLayer(poiStrokeLayerId, poiSourceId).withProperties(
                            lineColor("#FF0000"),
                            lineWidth(2f)
                        )
                        style.addLayer(strokeLayer)
                        Log.d(TAG, "Added POI stroke layer")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add POI stroke layer", e)
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
                    val lat = beacon.latitude
                    val lng = beacon.longitude
                    val point = if (lat != null && lng != null) {
                        Point.fromLngLat(lng, lat)
                    } else {
                        Point.fromLngLat(beacon.x, beacon.y)
                    }
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("name", beacon.name ?: "Beacon ${beacon.id}")
                    feature.addStringProperty("id", beacon.id.toString())
                    feature.addStringProperty("uuid", beacon.uuid ?: "")
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
                    val lat = node.latitude
                    val lng = node.longitude
                    val point = if (lat != null && lng != null) {
                        Point.fromLngLat(lng, lat)
                    } else {
                        Point.fromLngLat(node.x, node.y)
                    }
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("name", node.name ?: "Node ${node.id}")
                    feature.addStringProperty("id", node.id.toString())
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
                val geoJsonString = apiService.getAllPOIsAsGeoJSON()
                
                if (geoJsonString.isNullOrBlank()) {
                    Log.w(TAG, "No POI GeoJSON data received")
                    return@launch
                }
                
                Log.d(TAG, "Received GeoJSON: $geoJsonString")
                
                // Display GeoJSON directly
                displayGeoJSONPOIs(geoJsonString)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching legacy POIs", e)
            }
        }
    }

    /**
     * Display GeoJSON POIs directly
     */
    private fun displayGeoJSONPOIs(geoJsonString: String) {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            Log.w(TAG, "Map style not ready for GeoJSON POI display")
            return
        }
        
        try {
            Log.d(TAG, "Displaying GeoJSON POIs...")
            
            // Remove existing layers first
            try {
                style.getLayer("poi-fill-layer")?.let { style.removeLayer("poi-fill-layer") }
                style.getLayer("poi-stroke-layer")?.let { style.removeLayer("poi-stroke-layer") }
                style.getSource("poi-source")?.let { style.removeSource("poi-source") }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing existing layers", e)
            }
            
            // Parse GeoJSON
            val featureCollection = if (geoJsonString.trim().startsWith("[")) {
                // Array of features - convert to FeatureCollection
                val featureCollectionJson = """{"type": "FeatureCollection", "features": $geoJsonString}"""
                FeatureCollection.fromJson(featureCollectionJson)
            } else if (geoJsonString.contains("\"type\"") && geoJsonString.contains("\"FeatureCollection\"")) {
                // Already a FeatureCollection
                FeatureCollection.fromJson(geoJsonString)
            } else {
                // Single feature - wrap in FeatureCollection
                val feature = Feature.fromJson(geoJsonString)
                FeatureCollection.fromFeature(feature)
            }
            
            // Add GeoJSON source
            val geoJsonSource = GeoJsonSource("poi-source", featureCollection)
            style.addSource(geoJsonSource)
            
            // Add fill layer for polygon interiors with 0.6 opacity
            val fillLayer = FillLayer("poi-fill-layer", "poi-source").withProperties(
                fillColor("#FF0000"), // red
                fillOpacity(0.6f)
            )
            style.addLayer(fillLayer)
            
            // Add line layer for polygon outlines
            val strokeLayer = LineLayer("poi-stroke-layer", "poi-source").withProperties(
                lineColor("#FF0000"), // Red outline
                lineWidth(2f)
            )
            style.addLayer(strokeLayer)
            
            val featureCount = featureCollection.features()?.size ?: 0
            Log.d(TAG, "Successfully added $featureCount GeoJSON POI features to map")
            
            // Fit map to show features
            if (featureCount > 0) {
                fitMapToGeoJSONFeatures(featureCollection)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying GeoJSON POIs", e)
        }
    }

    /**
     * Fit map to GeoJSON features
     */
    private fun fitMapToGeoJSONFeatures(featureCollection: FeatureCollection) {
        try {
            val features = featureCollection.features()
            if (features.isNullOrEmpty()) return
            
            val coordinates = mutableListOf<org.maplibre.android.geometry.LatLng>()
            
            features.forEach { feature ->
                val geometry = feature.geometry()
                when (geometry) {
                    is Point -> {
                        coordinates.add(org.maplibre.android.geometry.LatLng(geometry.latitude(), geometry.longitude()))
                    }
                    is org.maplibre.geojson.Polygon -> {
                        // For polygons, use the first coordinate of the first ring
                        val rings = geometry.coordinates()
                        if (rings.isNotEmpty() && rings[0].isNotEmpty()) {
                            val firstPoint = rings[0][0]
                            coordinates.add(org.maplibre.android.geometry.LatLng(firstPoint.latitude(), firstPoint.longitude()))
                        }
                    }
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
                
                Log.d(TAG, "Fitted map to ${coordinates.size} GeoJSON coordinates")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fitting map to GeoJSON features", e)
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
                    if (poi.geometry != null && poi.type == "Feature") {
                        // POI is already a complete GeoJSON feature, use it directly
                        val poiJson = """
                        {
                            "type": "${poi.type}",
                            "properties": ${if (poi.properties != null) com.google.gson.Gson().toJson(poi.properties) else "{}"},
                            "geometry": ${com.google.gson.Gson().toJson(poi.geometry)}
                        }
                        """.trimIndent()
                        
                        val feature = Feature.fromJson(poiJson)
                        features.add(feature)
                        
                        Log.d(TAG, "Added GeoJSON feature for POI: ${poi.name}")
                    } else {
                        Log.w(TAG, "POI ${poi.id} is not a valid GeoJSON feature")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create feature for POI ${poi.id}: ${e.message}", e)
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
                val lat = poi.latitude
                val lng = poi.longitude
                val point = if (lat != null && lng != null) {
                    Point.fromLngLat(lng, lat)
                } else {
                    Point.fromLngLat(poi.x, poi.y)
                }
                allCoordinates.add(point)
            }
            
            currentBeacons.forEach { beacon ->
                val lat = beacon.latitude
                val lng = beacon.longitude
                val point = if (lat != null && lng != null) {
                    Point.fromLngLat(lng, lat)
                } else {
                    Point.fromLngLat(beacon.x, beacon.y)
                }
                allCoordinates.add(point)
            }
            
            currentRouteNodes.forEach { node ->
                val lat = node.latitude
                val lng = node.longitude
                val point = if (lat != null && lng != null) {
                    Point.fromLngLat(lng, lat)
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

    /**
     * Handle navigation to a selected POI
     */
    private fun handleNavigationToPOI(poiId: Int, poiName: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting navigation to POI: $poiName (ID: $poiId)")
                
                // Get user's current location
                val locationComponent = mapLibreMap.locationComponent
                val userLocation = locationComponent.lastKnownLocation
                
                if (userLocation == null) {
                    Toast.makeText(this@MainActivity, "Unable to get current location", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Create path request
                val pathRequest = PathRequest(
                    userLocation = UserLocation(
                        latitude = userLocation.latitude,
                        longitude = userLocation.longitude
                    ),
                    destinationPoiId = poiId
                )
                
                // Call the path finding API
                val pathFeatureCollection = apiService.findPath(pathRequest)
                
                if (pathFeatureCollection != null) {
                    Log.d(TAG, "Path found with ${pathFeatureCollection.features()?.size ?: 0} features")
                    displayPath(pathFeatureCollection)
                    
                    // Show clear path button
                    fabClearPath.visibility = View.VISIBLE
                    
                    Toast.makeText(this@MainActivity, "Navigation path to $poiName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "No path found to $poiName", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during navigation", e)
                Toast.makeText(this@MainActivity, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Display the navigation path on the map
     */
    private fun displayPath(pathFeatureCollection: FeatureCollection) {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            Log.w(TAG, "Map style not ready for path display")
            return
        }
        
        try {
            Log.d(TAG, "Displaying navigation path...")
            
            // Clear existing path layers
            clearPathLayers(style)
            
            val features = pathFeatureCollection.features() ?: return
            if (features.isEmpty()) {
                Log.w(TAG, "No path features to display")
                return
            }
            
            // Separate path nodes and path edges
            val pathNodes = mutableListOf<Feature>()
            val pathEdges = mutableListOf<Feature>()
            
            features.forEach { feature ->
                val isPathNode = feature.getBooleanProperty("is_path_node") ?: false
                val isPathEdge = feature.getBooleanProperty("is_path_edge") ?: false
                
                when {
                    isPathNode -> pathNodes.add(feature)
                    isPathEdge -> pathEdges.add(feature)
                }
            }
            
            Log.d(TAG, "Path contains ${pathNodes.size} nodes and ${pathEdges.size} edges")
            
            // Add path edges (lines) with distinct color
            if (pathEdges.isNotEmpty()) {
                val pathEdgesCollection = FeatureCollection.fromFeatures(pathEdges)
                val pathEdgesSource = GeoJsonSource(pathEdgesSourceId, pathEdgesCollection)
                style.addSource(pathEdgesSource)
                
                val pathEdgesLayer = LineLayer(pathEdgesLayerId, pathEdgesSourceId)
                    .withProperties(
                        lineColor("#FF6B35"), // Orange-red color for path
                        lineWidth(6f),
                        lineOpacity(0.8f)
                    )
                style.addLayer(pathEdgesLayer)
            }
            
            // Add path nodes with distinct color
            if (pathNodes.isNotEmpty()) {
                val pathNodesCollection = FeatureCollection.fromFeatures(pathNodes)
                val pathNodesSource = GeoJsonSource(pathNodesSourceId, pathNodesCollection)
                style.addSource(pathNodesSource)
                
                val pathNodesLayer = CircleLayer(pathNodesLayerId, pathNodesSourceId)
                    .withProperties(
                        circleRadius(8f),
                        circleColor("#FF6B35"), // Orange-red color for path nodes
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2f),
                        circleOpacity(0.9f)
                    )
                style.addLayer(pathNodesLayer)
            }
            
            Log.d(TAG, "Navigation path displayed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying path", e)
        }
    }
    
    /**
     * Clear the navigation path from the map
     */
    private fun clearPath() {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            return
        }
        
        clearPathLayers(style)
        fabClearPath.visibility = View.GONE
        
        Toast.makeText(this, "Navigation path cleared", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Navigation path cleared")
    }
    
    /**
     * Clear path layers from the map style
     */
    private fun clearPathLayers(style: Style) {
        try {
            // Remove layers
            style.getLayer(pathNodesLayerId)?.let { style.removeLayer(pathNodesLayerId) }
            style.getLayer(pathEdgesLayerId)?.let { style.removeLayer(pathEdgesLayerId) }
            
            // Remove sources
            style.getSource(pathNodesSourceId)?.let { style.removeSource(pathNodesSourceId) }
            style.getSource(pathEdgesSourceId)?.let { style.removeSource(pathEdgesSourceId) }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing path layers", e)
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