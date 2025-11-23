package com.KFUPM.ai_indoor_nav_mobile

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.utils.ColorUtils
import org.maplibre.geojson.*
import org.maplibre.android.style.expressions.Expression.*
import kotlinx.coroutines.*
import okhttp3.*
import org.maplibre.android.WellKnownTileServer
import java.io.IOException
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.KFUPM.ai_indoor_nav_mobile.localization.LocalizationController
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.WriterException

class MainActivity : AppCompatActivity() {

    // Helper class for parallel API calls
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    private lateinit var fabAssignment: FloatingActionButton
    private lateinit var fabSearch: FloatingActionButton
    private lateinit var fabClearPath: FloatingActionButton
    private lateinit var fabQrCode: FloatingActionButton
    private lateinit var floorSelectorContainer: LinearLayout
    private lateinit var floorRecyclerView: RecyclerView
    private lateinit var floorSelectorAdapter: FloorSelectorAdapter
    private lateinit var assignmentInfoContainer: LinearLayout
    private lateinit var assignmentInfoText: TextView
    private lateinit var btnRetryApi: Button

    private val apiService = ApiService()
    private lateinit var localizationController: LocalizationController
    private var isLocalizationActive = false

    // Visitor ID for QR code
    private var visitorId: String? = null

    private var currentAssignment: UserAssignment? = null
    private var hasRequestedInitialAssignment = false
    private var hasAutoClickedAssignment = false // Track if we've auto-clicked the assignment button once
    private var hasNavigatedToAssignedLevel = false // Track if we've navigated to the assigned level
    private var hasInitializedMultiFloorLocalization = false // Track if we've done initial multi-floor localization
    private var allowAutoFloorSwitch = true // Allow auto-switching only initially and when navigation starts
    
    // Mapping of node ID to floor ID for automatic floor switching
    private val nodeToFloorMap = mutableMapOf<String, Int>()

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
    
    // Past path IDs (for visited nodes/edges)
    private val pastPathNodesSourceId = "past-path-nodes-source"
    private val pastPathEdgesSourceId = "past-path-edges-source"
    private val pastPathNodesLayerId = "past-path-nodes-layer"
    private val pastPathEdgesLayerId = "past-path-edges-layer"
    
    // Floor transition indicators (stairs/elevator markers)
    private val transitionIndicatorsSourceId = "transition-indicators-source"
    private val transitionIndicatorsLayerId = "transition-indicators-layer"
    
    // Localization position marker IDs
    private val localizationMarkerSourceId = "localization-marker-source"
    private val localizationMarkerLayerId = "localization-marker-layer"
    private val localizationMarkerStrokeLayerId = "localization-marker-stroke-layer"
    
    // Current navigation state
    private var currentNavigationPath: FeatureCollection? = null
    private var targetNavigationLevel: Int? = null
    private val visitedPathNodeIds = mutableSetOf<Int>()
    
    // Cache for node types (to avoid repeated API calls)
    private val nodeTypeCache = mutableMapOf<Int, String?>()
    companion object {
        private const val TAG = "MainActivity"
    }

//    private val locationPermissionRequest =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
//            if (granted) {
//                enableUserLocation()
//            } else {
//                // TODO: Handle permission denied (optional)
//            }
//        }
    
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
        fabAssignment = findViewById(R.id.fabAssignment)
        fabSearch = findViewById(R.id.fabSearch)
        fabClearPath = findViewById(R.id.fabClearPath)
        fabQrCode = findViewById(R.id.fabQrCode)
        floorSelectorContainer = findViewById(R.id.floorSelectorContainer)
        floorRecyclerView = findViewById(R.id.floorRecyclerView)
        assignmentInfoContainer = findViewById(R.id.assignmentInfoContainer)
        assignmentInfoText = findViewById(R.id.assignmentInfoText)
        btnRetryApi = findViewById(R.id.btnRetryApi)

        // Initialize localization controller
        localizationController = LocalizationController(this)

        setupFloorSelector()
        mapView.onCreate(savedInstanceState)
        setupButtonListeners()
        
        // Request battery optimization exemption for unrestricted Bluetooth scanning
        requestBatteryOptimizationExemption()

        Log.d(TAG, "tileUrl: ${BuildConfig.tileUrl}")

        mapView.getMapAsync { maplibreMap ->
            mapLibreMap = maplibreMap
            mapLibreMap.setStyle(
                Style.Builder().fromUri(BuildConfig.tileUrl)
            ) {
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
        fabAssignment.setOnClickListener {
            requestNewAssignment()
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

        fabQrCode.setOnClickListener {
            showQrCodeDialog()
        }

        btnRetryApi.setOnClickListener {
            btnRetryApi.visibility = View.GONE
            initializeAppData()
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
                    showRetryButton()
                    return@launch
                }
                
                // Select the first building
                currentBuilding = buildings.first()
                Log.d(TAG, "Selected building: ${currentBuilding?.name}")
                
                // Fetch floors for the selected building
                fetchFloorsForBuilding(currentBuilding!!.id)
                
                // Load node-to-floor mappings for all floors to enable automatic floor switching
                loadNodeToFloorMappings()
                
                // Load beacons from all floors for initial trilateration
                loadAllBeaconsForTrilateration()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing app data", e)
                showRetryButton()
            }
        }
    }
    
    /**
     * Load node-to-floor mappings for all floors to enable automatic floor switching
     */
    private suspend fun loadNodeToFloorMappings() {
        try {
            Log.d(TAG, "Loading node-to-floor mappings for all floors...")
            
            // Load route nodes for each floor in parallel
            val allRouteNodes = coroutineScope {
                floors.map { floor ->
                    async { 
                        apiService.getRouteNodesByFloor(floor.id)?.also { nodes ->
                            Log.d(TAG, "Loaded ${nodes.size} nodes for floor ${floor.name}")
                        }
                    }
                }.awaitAll().filterNotNull().flatten()
            }
            
            // Populate the mapping
            allRouteNodes.forEach { node ->
                nodeToFloorMap[node.id.toString()] = node.floorId
            }
            
            Log.d(TAG, "Loaded ${nodeToFloorMap.size} node-to-floor mappings across ${floors.size} floors")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading node-to-floor mappings", e)
        }
    }
    
    /**
     * Load beacons from all floors for initial trilateration
     * This allows the system to determine which floor the user is on at startup
     */
    private suspend fun loadAllBeaconsForTrilateration() {
        try {
            Log.d(TAG, "Loading beacons from all floors for trilateration...")
            
            // Load beacons for each floor in parallel
            val allBeacons = coroutineScope {
                floors.map { floor ->
                    async { 
                        apiService.getBeaconsByFloor(floor.id)?.also { beacons ->
                            Log.d(TAG, "Loaded ${beacons.size} beacons for floor ${floor.name}")
                        }
                    }
                }.awaitAll().filterNotNull().flatten()
            }
            
            Log.d(TAG, "Loaded ${allBeacons.size} total beacons across ${floors.size} floors")
            
            // Now initialize localization with ALL floor IDs so it can figure out which floor user is on
            if (floors.isNotEmpty()) {
                val allFloorIds = floors.map { it.id }
                Log.d(TAG, "Initializing localization with all floor IDs: $allFloorIds")
                initializeLocalizationWithAllFloors(allFloorIds)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading beacons for trilateration", e)
        }
    }

    /**
     * Show retry button when API call fails
     */
    private fun showRetryButton() {
        btnRetryApi.visibility = View.VISIBLE
        Toast.makeText(this, "Failed to load data. Please retry.", Toast.LENGTH_LONG).show()
    }

    /**
     * Assign a visitor ID from the API
     */
    private suspend fun assignVisitorId() {
        try {
            Log.d(TAG, "Requesting visitor ID assignment...")
            val assignment = apiService.assignVisitor()

            if (assignment != null) {
                visitorId = assignment.visitorId
                Log.d(TAG, "Visitor ID assigned: $visitorId (Level: ${assignment.assignedLevel})")
                Toast.makeText(this, "Welcome! Your visitor ID: $visitorId", Toast.LENGTH_LONG).show()
            } else {
                Log.w(TAG, "Failed to assign visitor ID")
                Toast.makeText(this, "Could not assign visitor ID", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error assigning visitor ID", e)
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
                showRetryButton()
                return
            }
            
            floors = buildingFloors.sortedByDescending { it.floorNumber }
            Log.d(TAG, "Found ${floors.size} floors (sorted highest first): ${floors.map { "id=${it.id}, num=${it.floorNumber}, name=${it.name}" }}")
            
            // Update floor selector
            floorSelectorAdapter.updateFloors(floors)
            floorSelectorContainer.visibility = View.VISIBLE
            
            // Don't select any floor initially at startup
            // Let trilateration determine the user's floor first, then auto-switch
            Log.d(TAG, "Floors loaded. Waiting for trilateration to determine user's floor...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching floors", e)
            showRetryButton()
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
        Log.d(TAG, "selectFloor called: id=${floor.id}, number=${floor.floorNumber}, name=${floor.name}")
        
        // Clear any existing blue dot from previous floor
        clearLocalizationMarker()
        
        currentFloor = floor
        floorSelectorAdapter.setSelectedFloor(floor.id)
        Log.d(TAG, "currentFloor set to: id=${currentFloor?.id}, number=${currentFloor?.floorNumber}, name=${currentFloor?.name}")

        // Redraw path if navigation is active (after floor data loads)
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading GeoJSON data for floor: ${floor.name}")
                
                // Fetch all GeoJSON data for the floor in parallel
                val (poisGeoJSON, beaconsGeoJSON, routeNodesGeoJSON, routeNodesList) = coroutineScope {
                    val poisDeferred = async { apiService.getPOIsByFloorAsGeoJSON(floor.id) }
                    val beaconsDeferred = async { apiService.getBeaconsByFloorAsGeoJSON(floor.id) }
                    val routeNodesDeferred = async { apiService.getRouteNodesByFloorAsGeoJSON(floor.id) }
                    val routeNodesListDeferred = async { apiService.getRouteNodesByFloor(floor.id) }
                    
                    // Await all results and return as quadruple
                    Quadruple(
                        poisDeferred.await(),
                        beaconsDeferred.await(),
                        routeNodesDeferred.await(),
                        routeNodesListDeferred.await()
                    )
                }
                
                // Update node-to-floor mapping for automatic floor switching
                routeNodesList?.forEach { node ->
                    nodeToFloorMap[node.id.toString()] = node.floorId
                }
                Log.d(TAG, "Updated node-to-floor mapping with ${routeNodesList?.size ?: 0} nodes for floor ${floor.id}")
                
                Log.d(TAG, "Loaded GeoJSON data for floor ${floor.name}")
                
                // Update map display with all data
                updateMapDisplayWithGeoJSON(poisGeoJSON, beaconsGeoJSON, routeNodesGeoJSON)
                
                // Initialize localization for this floor
                // Skip if we've already done multi-floor initialization at startup
                if (!hasInitializedMultiFloorLocalization) {
                    initializeLocalization(floor.id)
                } else {
                    Log.d(TAG, "Skipping per-floor localization - already initialized with all floors")
                }
                
                // Redraw navigation path for the new floor if navigation is active
                if (currentNavigationPath != null) {
                    redrawPathWithProgress()
                }
                
                // Auto-click assignment button after first floor is fully loaded (only once at startup)
                if (!hasAutoClickedAssignment) {
                    hasAutoClickedAssignment = true
                    delay(1000) // Give localization time to initialize
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "=== AUTO-CLICKING ASSIGNMENT BUTTON ===")
                        Log.d(TAG, "currentFloor: id=${currentFloor?.id}, number=${currentFloor?.floorNumber}, name=${currentFloor?.name}")
                        Log.d(TAG, "floors: ${floors.map { "id=${it.id}, num=${it.floorNumber}" }}")
                        Toast.makeText(this@MainActivity, "🔄 Auto-requesting assignment...", Toast.LENGTH_LONG).show()
                        fabAssignment.performClick()
                    }
                }

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
            
            // Add POIs (blue with transparency)
            if (!poisGeoJSON.isNullOrBlank()) {
                addGeoJSONLayer(style, poisGeoJSON, "poi", "#3B82F6", 2f, allFeatures)
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
                            lineColor("#2563EB"), // Darker blue for outline
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
            
            // Add fill layer for polygon interiors with transparency
            val fillLayer = FillLayer("poi-fill-layer", "poi-source").withProperties(
                fillColor("#3B82F6"), // Pleasant blue
                fillOpacity(0.4f) // More transparent
            )
            style.addLayer(fillLayer)
            
            // Add line layer for polygon outlines
            val strokeLayer = LineLayer("poi-stroke-layer", "poi-source").withProperties(
                lineColor("#2563EB"), // Darker blue outline
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
                    fillColor("#3B82F6"), // Pleasant blue
                    fillOpacity(0.4f) // More transparent
                )
                style.addLayer(fillLayer)
                
                // Add line layer for polygon outlines
                val strokeLayer = LineLayer("poi-stroke-layer", "poi-source").withProperties(
                    lineColor("#2563EB"), // Darker blue outline
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
                
                // Get position from localization (artificial blue dot) - NEVER use GPS
                val localizationPosition = localizationController.getCurrentPosition()
                
                if (localizationPosition == null) {
                    Toast.makeText(this@MainActivity, "Localization position not available. Please wait for indoor positioning to initialize.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // Use localization position (Bluetooth RSSI based)
                val (x, y) = localizationPosition
                Log.d(TAG, "Using localization position for routing: ($x, $y)")

                val userLocation = UserLocation(
                    latitude = y,  // y is latitude
                    longitude = x  // x is longitude
                )

                // Create path request
                val pathRequest = PathRequest(
                    userLocation = userLocation,
                    destinationPoiId = poiId
                )
                
                // Call the path finding API
                val pathFeatureCollection = apiService.findPath(pathRequest)
                
                if (pathFeatureCollection != null) {
                    Log.d(TAG, "Path found with ${pathFeatureCollection.features()?.size ?: 0} features")
                    displayPath(pathFeatureCollection, targetLevel = null)
                    
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
    private fun displayPath(pathFeatureCollection: FeatureCollection, targetLevel: Int? = null) {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            Log.w(TAG, "Map style not ready for path display")
            return
        }
        
        try {
            Log.d(TAG, "Displaying navigation path...")
            
            // Store current navigation state
            currentNavigationPath = pathFeatureCollection
            targetNavigationLevel = targetLevel
            visitedPathNodeIds.clear()
            
            // Re-enable auto-switching when navigation starts
            // This will switch to user's current floor to begin navigation
            allowAutoFloorSwitch = true
            Log.d(TAG, "Navigation started - enabling auto-switch to current floor")
            
            // Redraw with filtering for current floor
            redrawPathWithProgress()
            
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
        
        // Clear navigation state
        currentNavigationPath = null
        targetNavigationLevel = null
        visitedPathNodeIds.clear()
        
        // Clear the user current floor indicator from floor selector
        floorSelectorAdapter.setUserCurrentFloor(-1)
        
        Toast.makeText(this, "Navigation path cleared", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Navigation path cleared")
    }
    
    /**
     * Clear path layers from the map style
     */
    private fun clearPathLayers(style: Style) {
        try {
            // Remove future path layers
            style.getLayer(pathNodesLayerId)?.let { style.removeLayer(pathNodesLayerId) }
            style.getLayer(pathEdgesLayerId)?.let { style.removeLayer(pathEdgesLayerId) }
            
            // Remove past path layers
            style.getLayer(pastPathNodesLayerId)?.let { style.removeLayer(pastPathNodesLayerId) }
            style.getLayer(pastPathEdgesLayerId)?.let { style.removeLayer(pastPathEdgesLayerId) }
            
            // Remove transition indicators (both text and background layers)
            style.getLayer(transitionIndicatorsLayerId)?.let { style.removeLayer(transitionIndicatorsLayerId) }
            style.getLayer("${transitionIndicatorsLayerId}_background")?.let { style.removeLayer("${transitionIndicatorsLayerId}_background") }
            
            // Remove sources
            style.getSource(pathNodesSourceId)?.let { style.removeSource(pathNodesSourceId) }
            style.getSource(pathEdgesSourceId)?.let { style.removeSource(pathEdgesSourceId) }
            style.getSource(pastPathNodesSourceId)?.let { style.removeSource(pastPathNodesSourceId) }
            style.getSource(pastPathEdgesSourceId)?.let { style.removeSource(pastPathEdgesSourceId) }
            style.getSource(transitionIndicatorsSourceId)?.let { style.removeSource(transitionIndicatorsSourceId) }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing path layers", e)
        }
    }

    /**
     * Update navigation progress as user walks along the path
     */
    private fun updateNavigationProgress(currentNodeId: String?) {
        try {
            if (currentNodeId == null || currentNavigationPath == null) {
                return
            }

            // Parse node ID to integer
            val currentNodeIdInt = currentNodeId.toIntOrNull() ?: return

            // Get all features from the current path
            val features = currentNavigationPath?.features() ?: return

            // Find if current node is in the path
            val pathNodes = features.filter { it.getBooleanProperty("is_path_node") == true }
            val isOnPath = pathNodes.any { feature ->
                val nodeIdStr = feature.getStringProperty("id")
                nodeIdStr?.toIntOrNull() == currentNodeIdInt
            }

            if (isOnPath) {
                // Mark this node as visited
                if (!visitedPathNodeIds.contains(currentNodeIdInt)) {
                    visitedPathNodeIds.add(currentNodeIdInt)
                    Log.d(TAG, "Node $currentNodeIdInt marked as visited (${visitedPathNodeIds.size} nodes visited)")
                    
                    // Redraw the path with updated visited nodes
                    redrawPathWithProgress()
                }

                // Check if we reached the destination
                checkIfDestinationReached(currentNodeIdInt)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating navigation progress", e)
        }
    }

    /**
     * Check if user has reached their destination
     */
    private fun checkIfDestinationReached(currentNodeIdInt: Int) {
        try {
            val targetLevel = targetNavigationLevel ?: return

            // Check if current node has the same level as target
            lifecycleScope.launch {
                try {
                    val currentFloorId = nodeToFloorMap[currentNodeIdInt.toString()]
                    if (currentFloorId == null) {
                        return@launch
                    }

                    // Get route nodes for current floor to check node level
                    val routeNodes = apiService.getRouteNodesByFloor(currentFloorId)
                    val currentNode = routeNodes?.find { it.id == currentNodeIdInt }

                    if (currentNode != null && currentNode.level == targetLevel) {
                        Log.d(TAG, "Destination reached! Node $currentNodeIdInt has level $targetLevel")
                        
                        withContext(Dispatchers.Main) {
                            // Show success message
                            Toast.makeText(
                                this@MainActivity,
                                "✅ You have reached your destination (Level $targetLevel)!",
                                Toast.LENGTH_LONG
                            ).show()

                            // Auto-clear the navigation
                            clearPath()
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error checking destination", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in checkIfDestinationReached", e)
        }
    }

    /**
     * Redraw the path with visited nodes shown in gray, filtered by current floor
     */
    private fun redrawPathWithProgress() {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            return
        }

        try {
            val features = currentNavigationPath?.features() ?: return
            val currentFloorId = currentFloor?.id

            if (currentFloorId == null) {
                Log.w(TAG, "Cannot redraw path: no current floor")
                return
            }

            // Separate into past and future nodes/edges, filtered by current floor
            val pastNodes = mutableListOf<Feature>()
            val futureNodes = mutableListOf<Feature>()
            val pastEdges = mutableListOf<Feature>()
            val futureEdges = mutableListOf<Feature>()

            features.forEach { feature ->
                val isPathNode = feature.getBooleanProperty("is_path_node") ?: false
                val isPathEdge = feature.getBooleanProperty("is_path_edge") ?: false

                if (isPathNode) {
                    // Check if this node is on the current floor
                    val nodeIdStr = feature.getStringProperty("id")
                    val nodeId = nodeIdStr?.toIntOrNull()
                    
                    if (nodeId != null) {
                        val nodeFloorId = nodeToFloorMap[nodeId.toString()]
                        
                        // Only add node if it's on the current floor
                        if (nodeFloorId == currentFloorId) {
                            if (visitedPathNodeIds.contains(nodeId)) {
                                pastNodes.add(feature)
                            } else {
                                futureNodes.add(feature)
                            }
                        }
                    }
                } else if (isPathEdge) {
                    // Check if both nodes of this edge are on the current floor
                    val fromNodeId = feature.getNumberProperty("from_node_id")?.toInt()
                    val toNodeId = feature.getNumberProperty("to_node_id")?.toInt()
                    
                    if (fromNodeId != null && toNodeId != null) {
                        val fromFloorId = nodeToFloorMap[fromNodeId.toString()]
                        val toFloorId = nodeToFloorMap[toNodeId.toString()]
                        
                        // Only add edge if both nodes are on the current floor
                        if (fromFloorId == currentFloorId && toFloorId == currentFloorId) {
                            if (visitedPathNodeIds.contains(fromNodeId) && 
                                visitedPathNodeIds.contains(toNodeId)) {
                                pastEdges.add(feature)
                            } else {
                                futureEdges.add(feature)
                            }
                        }
                    }
                }
            }

            // Clear existing path layers
            clearPathLayers(style)

            // Add past path edges (gray, faded)
            if (pastEdges.isNotEmpty()) {
                val pastEdgesCollection = FeatureCollection.fromFeatures(pastEdges)
                val pastEdgesSource = GeoJsonSource(pastPathEdgesSourceId, pastEdgesCollection)
                style.addSource(pastEdgesSource)

                val pastEdgesLayer = LineLayer(pastPathEdgesLayerId, pastPathEdgesSourceId)
                    .withProperties(
                        lineColor("#888888"), // Gray for visited path
                        lineWidth(5f),
                        lineOpacity(0.5f)
                    )
                style.addLayer(pastEdgesLayer)
            }

            // Add past path nodes (gray, smaller)
            if (pastNodes.isNotEmpty()) {
                val pastNodesCollection = FeatureCollection.fromFeatures(pastNodes)
                val pastNodesSource = GeoJsonSource(pastPathNodesSourceId, pastNodesCollection)
                style.addSource(pastNodesSource)

                val pastNodesLayer = CircleLayer(pastPathNodesLayerId, pastPathNodesSourceId)
                    .withProperties(
                        circleRadius(6f),
                        circleColor("#888888"), // Gray for visited nodes
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(1f),
                        circleOpacity(0.6f)
                    )
                style.addLayer(pastNodesLayer)
            }

            // Add future path edges (orange-red, bright)
            if (futureEdges.isNotEmpty()) {
                val futureEdgesCollection = FeatureCollection.fromFeatures(futureEdges)
                val futureEdgesSource = GeoJsonSource(pathEdgesSourceId, futureEdgesCollection)
                style.addSource(futureEdgesSource)

                val futureEdgesLayer = LineLayer(pathEdgesLayerId, pathEdgesSourceId)
                    .withProperties(
                        lineColor("#FF6B35"), // Orange-red for future path
                        lineWidth(6f),
                        lineOpacity(0.8f)
                    )
                style.addLayer(futureEdgesLayer)
            }

            // Add future path nodes (orange-red, bright)
            if (futureNodes.isNotEmpty()) {
                val futureNodesCollection = FeatureCollection.fromFeatures(futureNodes)
                val futureNodesSource = GeoJsonSource(pathNodesSourceId, futureNodesCollection)
                style.addSource(futureNodesSource)

                val futureNodesLayer = CircleLayer(pathNodesLayerId, pathNodesSourceId)
                    .withProperties(
                        circleRadius(8f),
                        circleColor("#FF6B35"), // Orange-red for future nodes
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2f),
                        circleOpacity(0.9f)
                    )
                style.addLayer(futureNodesLayer)
            }

            Log.d(TAG, "Path redrawn for floor ${currentFloor?.name}: ${pastNodes.size} visited nodes, ${futureNodes.size} remaining nodes on this floor")

            // Add floor transition indicators after path is drawn
            addFloorTransitionIndicators(style, features, currentFloorId)

        } catch (e: Exception) {
            Log.e(TAG, "Error redrawing path with progress", e)
        }
    }

    /**
     * Add floor transition indicators next to stairs/elevators
     * Shows indicators at the starting point of floor transitions
     */
    private fun addFloorTransitionIndicators(style: Style, features: List<Feature>, currentFloorId: Int) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔍 Looking for floor transitions on floor $currentFloorId...")
                val transitionFeatures = mutableListOf<Feature>()
                
                // Get all path edges to find floor transitions
                val pathEdges = features.filter { it.getBooleanProperty("is_path_edge") == true }
                val pathNodes = features.filter { it.getBooleanProperty("is_path_node") == true }
                
                Log.d(TAG, "Found ${pathEdges.size} path edges and ${pathNodes.size} path nodes to check")
                
                // Find edges that transition between floors
                pathEdges.forEach { edge ->
                    val fromNodeId = edge.getNumberProperty("from_node_id")?.toInt()
                    val toNodeId = edge.getNumberProperty("to_node_id")?.toInt()
                    val isLevelTransition = edge.getBooleanProperty("is_level_transition") ?: false
                    
                    if (fromNodeId != null && toNodeId != null) {
                        val fromFloorId = nodeToFloorMap[fromNodeId.toString()]
                        val toFloorId = nodeToFloorMap[toNodeId.toString()]
                        
                        // Check if this edge transitions between floors
                        if (fromFloorId != toFloorId && fromFloorId != null && toFloorId != null) {
                            Log.d(TAG, "Found floor transition edge: $fromNodeId (floor $fromFloorId) -> $toNodeId (floor $toFloorId)")
                            
                            // Show indicator on the FROM floor (where user needs to take action)
                            if (fromFloorId == currentFloorId) {
                                // Find the FROM node to get its position
                                val fromNode = pathNodes.find { 
                                    val nodeIdStr = it.getStringProperty("id")
                                    nodeIdStr?.toIntOrNull() == fromNodeId
                                }
                                
                                if (fromNode != null) {
                                    val geometry = fromNode.geometry()
                                    if (geometry is Point) {
                                        // Determine direction (up or down)
                                        val fromFloor = floors.find { it.id == fromFloorId }
                                        val toFloor = floors.find { it.id == toFloorId }
                                        
                                        if (fromFloor != null && toFloor != null) {
                                            val direction = if (toFloor.floorNumber > fromFloor.floorNumber) "↑" else "↓"
                                            val text = "$direction F${toFloor.floorNumber}"
                                            
                                            val indicator = Feature.fromGeometry(geometry)
                                            indicator.addStringProperty("text", text)
                                            indicator.addStringProperty("direction", direction)
                                            indicator.addNumberProperty("targetFloor", toFloor.floorNumber)
                                            transitionFeatures.add(indicator)
                                            
                                            Log.d(TAG, "  ✅ Creating indicator '$text' at node $fromNodeId on ${fromFloor.name}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Add all indicators at once on main thread
                if (transitionFeatures.isNotEmpty()) {
                    Log.d(TAG, "🎯 Adding ${transitionFeatures.size} transition indicators to map")
                    withContext(Dispatchers.Main) {
                        addTransitionIndicatorsToMap(style, transitionFeatures)
                    }
                } else {
                    Log.w(TAG, "⚠️ No transition indicators found to display")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error adding floor transition indicators", e)
            }
        }
    }

    /**
     * Add transition indicators to the map
     */
    private fun addTransitionIndicatorsToMap(style: Style, indicators: List<Feature>) {
        try {
            if (indicators.isEmpty()) {
                Log.d(TAG, "No transition indicators to add")
                return
            }
            
            // Remove existing layers and source
            val backgroundLayerId = "${transitionIndicatorsLayerId}_background"
            style.getLayer(transitionIndicatorsLayerId)?.let { 
                style.removeLayer(transitionIndicatorsLayerId)
                Log.d(TAG, "Removed existing transition indicators text layer")
            }
            style.getLayer(backgroundLayerId)?.let { 
                style.removeLayer(backgroundLayerId)
                Log.d(TAG, "Removed existing transition indicators background layer")
            }
            style.getSource(transitionIndicatorsSourceId)?.let { 
                style.removeSource(transitionIndicatorsSourceId)
                Log.d(TAG, "Removed existing transition indicators source")
            }
            
            // Create feature collection
            val featureCollection = FeatureCollection.fromFeatures(indicators)
            val source = GeoJsonSource(transitionIndicatorsSourceId, featureCollection)
            style.addSource(source)
            
            // Add white background circle layer first
            val backgroundLayerId = "${transitionIndicatorsLayerId}_background"
            val backgroundLayer = CircleLayer(backgroundLayerId, transitionIndicatorsSourceId)
                .withProperties(
                    circleRadius(18f), // Larger radius for background
                    circleColor("#FFFFFF"), // White background
                    circleOpacity(1.0f),
                    circleStrokeColor("#000000"), // Black border for definition
                    circleStrokeWidth(2f)
                )
            style.addLayer(backgroundLayer)
            
            // Add text layer on top of background with white text
            val textLayer = org.maplibre.android.style.layers.SymbolLayer(transitionIndicatorsLayerId, transitionIndicatorsSourceId)
                .withProperties(
                    PropertyFactory.textField(get("text")),
                    PropertyFactory.textSize(18f),
                    PropertyFactory.textColor("#FFFFFF"), // White text
                    PropertyFactory.textHaloColor("#000000"), // Black halo for contrast against white background
                    PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textHaloBlur(1f),
                    PropertyFactory.textOffset(arrayOf(0f, 0f)), // Center on circle
                    PropertyFactory.textAnchor("center"),
                    PropertyFactory.textAllowOverlap(true), // Always show
                    PropertyFactory.textIgnorePlacement(true), // Ignore collision
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.textOptional(false) // Text is required
                )
            style.addLayer(textLayer)
            
            Log.d(TAG, "✅ Added ${indicators.size} transition indicators to map")
            indicators.forEach { indicator ->
                Log.d(TAG, "  - Indicator: ${indicator.getStringProperty("text")} at ${indicator.geometry()}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding transition indicators to map", e)
        }
    }

    /**
     * Initialize localization for the current floor
     */
    private fun initializeLocalization(floorId: Int) {
        // Check if we have required Bluetooth permissions
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Bluetooth permissions not granted, cannot start localization")
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Initializing localization for floor $floorId...")

                // Stop any existing localization
                if (isLocalizationActive) {
                    localizationController.stop()
                    isLocalizationActive = false
                }

                // Try auto-initialization first (determines position automatically)
                val success = localizationController.autoInitialize(
                    availableFloorIds = listOf(floorId),
                    scanDurationMs = 5000 // 5 seconds
                )

                if (success) {
                    // Start continuous localization
                    localizationController.start()
                    isLocalizationActive = true

                    // Observe position updates (assignment will be requested once position is found)
                    observeLocalizationUpdates()

                    Log.d(TAG, "Localization started successfully")
                    Toast.makeText(this@MainActivity, "Indoor positioning active", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Auto-initialization failed, trying manual initialization...")

                    // Fallback: manual initialization without specific starting position
                    val manualSuccess = localizationController.initialize(floorId, null)
                    if (manualSuccess) {
                        localizationController.start()
                        isLocalizationActive = true

                        // Observe position updates (assignment will be requested once position is found)
                        observeLocalizationUpdates()

                        Log.d(TAG, "Localization started with manual initialization")
                    } else {
                        Log.e(TAG, "Failed to initialize localization")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing localization", e)
            }
        }
    }
    
    /**
     * Initialize localization with ALL floor IDs to automatically determine user's floor
     * This is called at startup after beacons from all floors are loaded
     */
    private fun initializeLocalizationWithAllFloors(floorIds: List<Int>) {
        // Check if we have required Bluetooth permissions
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Bluetooth permissions not granted, cannot start localization")
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Initializing localization with all floor IDs: $floorIds")

                // Stop any existing localization
                if (isLocalizationActive) {
                    localizationController.stop()
                    isLocalizationActive = false
                }

                // Use auto-initialization with ALL floor IDs
                // This will scan beacons and automatically determine which floor the user is on
                val success = localizationController.autoInitialize(
                    availableFloorIds = floorIds,
                    scanDurationMs = 5000 // 5 seconds
                )

                if (success) {
                    // Start continuous localization
                    localizationController.start()
                    isLocalizationActive = true
                    hasInitializedMultiFloorLocalization = true

                    // Observe position updates
                    observeLocalizationUpdates()

                    Log.d(TAG, "Multi-floor localization started successfully")
                    Toast.makeText(this@MainActivity, "🔍 Detecting your floor...", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Multi-floor auto-initialization failed")
                    Toast.makeText(this@MainActivity, "Could not detect floor position", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing multi-floor localization", e)
            }
        }
    }

    /**
     * Observe localization state updates and update the blue dot
     */
    private fun observeLocalizationUpdates() {
        lifecycleScope.launch {
            localizationController.localizationState.collect { state ->
                val nodeId = state.currentNodeId
                val confidence = state.confidence

                // Get position coordinates
                val position = localizationController.getCurrentPosition()

                if (position != null) {
                    val (x, y) = position
                    Log.d(TAG, "Localization: node=$nodeId, pos=($x, $y), confidence=${String.format("%.2f", confidence)}")

                    // Update blue dot on map
                    updateLocalizationMarker(x, y, confidence)
                    
                    // Update navigation path progress
                    updateNavigationProgress(nodeId)
                    
                    // Update floor selector to show current physical floor indicator
                    if (nodeId != null && confidence > 0.6) {
                        val detectedFloorId = nodeToFloorMap[nodeId]
                        if (detectedFloorId != null) {
                            withContext(Dispatchers.Main) {
                                floorSelectorAdapter.setUserCurrentFloor(detectedFloorId)
                            }
                            
                            val currentFloorId = currentFloor?.id
                            
                            // Auto-switch floor ONLY if allowed (initial detection or navigation start)
                            // After that, user has full control to view any floor they want
                            if (allowAutoFloorSwitch && (currentFloorId == null || detectedFloorId != currentFloorId)) {
                                Log.d(TAG, "Auto-switching to detected floor: current view=$currentFloorId, detected=$detectedFloorId")
                                
                                // Find the floor to switch to
                                val targetFloor = floors.find { it.id == detectedFloorId }
                                if (targetFloor != null) {
                                    val isInitial = currentFloorId == null
                                    val isNavigating = currentNavigationPath != null
                                    Log.d(TAG, "Auto-switching to floor: ${targetFloor.name} (initial: $isInitial, navigating: $isNavigating)")
                                    withContext(Dispatchers.Main) {
                                        // Switch to the detected floor
                                        onFloorSelected(targetFloor)
                                        
                                        // Show toast message
                                        val message = if (isNavigating) {
                                            "📍 Navigation starting on ${targetFloor.name}"
                                        } else {
                                            "📍 Located on ${targetFloor.name}"
                                        }
                                        Toast.makeText(
                                            this@MainActivity, 
                                            message, 
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        
                                        // After switching, disable auto-switching
                                        // User can now freely view any floor without interruption
                                        allowAutoFloorSwitch = false
                                        Log.d(TAG, "Floor set to ${targetFloor.name} - disabling auto-switch. User now has full control.")
                                    }
                                }
                            }
                        }
                    }

                    // Request initial assignment once position is found
                    if (!hasRequestedInitialAssignment) {
                        hasRequestedInitialAssignment = true
                        currentFloor?.let { floor ->
                            Log.d(TAG, "Position initially found - requesting assignment")
                            requestInitialAssignment(floor.id)
                        }
                    }

                    // Navigate to assigned level if we have both assignment and trilaterated position
                    if (!hasNavigatedToAssignedLevel && nodeId != null && confidence > 0.5) {
                        val assignment = currentAssignment
                        if (assignment != null && assignment.level != null) {
                            Log.d(TAG, "Both assignment and trilateration ready - triggering navigation")
                            hasNavigatedToAssignedLevel = true
                            navigateToAssignedLevel(assignment)
                        }
                    }
                } else {
                    // Clear marker if no position
                    clearLocalizationMarker()
                }

                // Log debug info
                state.debug?.let { debug ->
                    Log.d(TAG, "Beacons visible: ${debug.visibleBeaconCount}")
                    if (debug.junctionAmbiguity) {
                        Log.d(TAG, "At junction - position may be ambiguous")
                    }
                }
            }
        }
    }

    /**
     * Update the localization marker (blue dot) on the map
     */
    private fun updateLocalizationMarker(x: Double, y: Double, confidence: Double) {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            return
        }

        try {
            // Create point feature for the marker
            val point = Point.fromLngLat(x, y)
            val feature = Feature.fromGeometry(point)
            feature.addNumberProperty("confidence", confidence)

            val featureCollection = FeatureCollection.fromFeature(feature)

            // Add or update source
            var source = style.getSource(localizationMarkerSourceId) as? GeoJsonSource
            if (source != null) {
                source.setGeoJson(featureCollection)
            } else {
                source = GeoJsonSource(localizationMarkerSourceId, featureCollection)
                style.addSource(source)

                // Add inner circle layer (blue dot)
                if (style.getLayer(localizationMarkerLayerId) == null) {
                    val markerLayer = CircleLayer(localizationMarkerLayerId, localizationMarkerSourceId)
                        .withProperties(
                            circleRadius(10f),
                            circleColor("#0080FF"), // Bright blue
                            circleOpacity(0.8f)
                        )
                    style.addLayer(markerLayer)
                }

                // Add outer stroke layer
                if (style.getLayer(localizationMarkerStrokeLayerId) == null) {
                    val strokeLayer = CircleLayer(localizationMarkerStrokeLayerId, localizationMarkerSourceId)
                        .withProperties(
                            circleRadius(12f),
                            circleColor("#FFFFFF"), // White stroke
                            circleOpacity(0.9f),
                            circleStrokeWidth(2f),
                            circleStrokeColor("#0080FF")
                        )
                    // Add stroke layer below the main layer
                    style.addLayerBelow(strokeLayer, localizationMarkerLayerId)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating localization marker", e)
        }
    }

    /**
     * Clear the localization marker from the map
     */
    private fun clearLocalizationMarker() {
        val style = mapLibreMap.style
        if (style == null || !style.isFullyLoaded) {
            return
        }

        try {
            style.getLayer(localizationMarkerLayerId)?.let { style.removeLayer(localizationMarkerLayerId) }
            style.getLayer(localizationMarkerStrokeLayerId)?.let { style.removeLayer(localizationMarkerStrokeLayerId) }
            style.getSource(localizationMarkerSourceId)?.let { style.removeSource(localizationMarkerSourceId) }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing localization marker", e)
        }
    }
    
    /**
     * Request initial user assignment after localization is initialized
     */
    private fun requestInitialAssignment(floorId: Int) {
        lifecycleScope.launch {
            try {
                // Generate random age and disability status
                val age = (18..90).random()
                val isDisabled = Math.random() < 0.20

                Log.d(TAG, "Requesting initial assignment with age=$age, isDisabled=$isDisabled, floorId=$floorId, currentFloor=${currentFloor?.name}")

                // Request assignment from backend
                val assignmentResponse = apiService.requestUserAssignment(age, isDisabled)

                val assignment = if (assignmentResponse != null) {
                    // Capture the visitor ID from the response
                    visitorId = assignmentResponse.visitorId
                    Log.d(TAG, "Visitor ID captured from initial assignment: $visitorId, level=${assignmentResponse.level}")
                    
                    // Convert response to UserAssignment
                    val userAssignment = UserAssignment(
                        age = assignmentResponse.decision.age,
                        isDisabled = assignmentResponse.decision.isDisabled,
                        level = assignmentResponse.level,
                        floorId = floorId,
                        floorName = currentFloor?.name
                    )
                    Log.d(TAG, "Created initial UserAssignment: age=${userAssignment.age}, isDisabled=${userAssignment.isDisabled}, level=${userAssignment.level}, floorId=${userAssignment.floorId}, floorName=${userAssignment.floorName}")
                    userAssignment
                } else {
                    // Fallback: Generate assignment locally if backend doesn't support it
                    Log.d(TAG, "Backend assignment not available, generating locally")
                    generateLocalAssignment(floorId)
                }

                currentAssignment = assignment
                displayAssignment(assignment)
                Log.d(TAG, "Initial assignment received: age=${assignment.age}, disabled=${assignment.isDisabled}, level=${assignment.level}")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting initial assignment", e)
            }
        }
    }

    /**
     * Request a new assignment (when user clicks the assignment button)
     */
    private fun requestNewAssignment() {
        val floorId = currentFloor?.id

        if (floorId == null) {
            Log.w(TAG, "Cannot request assignment: currentFloor is null. floors.size=${floors.size}")
            Toast.makeText(this, "No floor selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Generate random age and disability status
                val age = (18..90).random()
                val isDisabled = Math.random() < 0.20

                Log.d(TAG, "Requesting new assignment with age=$age, isDisabled=$isDisabled, floorId=$floorId, currentFloor=${currentFloor?.name}")

                // Request new assignment from backend
                val assignmentResponse = apiService.requestUserAssignment(age, isDisabled)

                val assignment = if (assignmentResponse != null) {
                    // Capture the visitor ID from the response
                    visitorId = assignmentResponse.visitorId
                    Log.d(TAG, "Visitor ID captured from new assignment: $visitorId, level=${assignmentResponse.level}")
                    Toast.makeText(this@MainActivity, "New visitor ID: $visitorId", Toast.LENGTH_SHORT).show()
                    
                    // Convert response to UserAssignment
                    val userAssignment = UserAssignment(
                        age = assignmentResponse.decision.age,
                        isDisabled = assignmentResponse.decision.isDisabled,
                        level = assignmentResponse.level,
                        floorId = floorId,
                        floorName = currentFloor?.name
                    )
                    Log.d(TAG, "Created UserAssignment: age=${userAssignment.age}, isDisabled=${userAssignment.isDisabled}, level=${userAssignment.level}, floorId=${userAssignment.floorId}, floorName=${userAssignment.floorName}")
                    userAssignment
                } else {
                    // Fallback: Generate assignment locally if backend doesn't support it
                    Log.d(TAG, "Backend assignment not available, generating locally")
                    generateLocalAssignment(floorId)
                }

                currentAssignment = assignment
                // Reset navigation flag to allow re-navigation with new assignment
                hasNavigatedToAssignedLevel = false
                displayAssignment(assignment)
                Toast.makeText(this@MainActivity, "New assignment received", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "New assignment received: age=${assignment.age}, disabled=${assignment.isDisabled}, level=${assignment.level}")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting new assignment", e)
                Toast.makeText(this@MainActivity, "Assignment error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Generate a local assignment with random age and status
     */
    private fun generateLocalAssignment(floorId: Int): UserAssignment {
        // Random age between 18 and 90
        val age = (18..90).random()

        // 20% chance of being disabled
        val isDisabled = Math.random() < 0.20

        val floorName = currentFloor?.name

        return UserAssignment(
            age = age,
            isDisabled = isDisabled,
            floorId = floorId,
            floorName = floorName
        )
    }

    /**
     * Display the current assignment info with compact emoji format
     */
    private fun displayAssignment(assignment: UserAssignment) {
        try {
            Log.d(TAG, "displayAssignment called: level=${assignment.level}, age=${assignment.age}, isDisabled=${assignment.isDisabled}")
            
            val healthEmoji = assignment.getHealthStatusEmoji()
            val statusEmoji = if (assignment.isDisabled) "⚠️" else "✅"
            
            // Show assigned accessibility LEVEL (L1, L2, L3) - this is what you've been assigned
            val level = assignment.level ?: 1
            val infoText = "$healthEmoji L$level | ${assignment.age} | $statusEmoji"

            assignmentInfoText.text = infoText
            assignmentInfoContainer.visibility = View.VISIBLE

            Log.d(TAG, "Assignment displayed: $infoText (level=$level)")

            // Navigate user to their assigned level after trilateration
            navigateToAssignedLevel(assignment)

        } catch (e: Exception) {
            Log.e(TAG, "Error displaying assignment", e)
        }
    }

    /**
     * Update assignment display - ONLY used to refresh the display, not to change the level
     * The level is static and comes from the assignment, NOT from trilateration
     */
    private fun updateAssignmentDisplay() {
        try {
            val assignment = currentAssignment
            if (assignment == null || assignmentInfoContainer.visibility != View.VISIBLE) {
                return
            }

            // Show the assigned accessibility LEVEL (L1, L2, L3) - this is STATIC
            val level = assignment.level ?: 1
            
            val healthEmoji = assignment.getHealthStatusEmoji()
            val statusEmoji = if (assignment.isDisabled) "⚠️" else "✅"
            val infoText = "$healthEmoji L$level | ${assignment.age} | $statusEmoji"

            // Only update if text changed
            if (assignmentInfoText.text != infoText) {
                assignmentInfoText.text = infoText
                Log.d(TAG, "Assignment display: $infoText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating assignment display", e)
        }
    }

    /**
     * Get the current floor number based on trilaterated position
     */
    private fun getFloorNumberFromTrilateration(): Int? {
        try {
            // Get current node from trilateration
            val currentNodeId = localizationController.localizationState.value.currentNodeId
            if (currentNodeId == null) {
                Log.d(TAG, "getFloorNumberFromTrilateration: No current node yet")
                return null
            }

            // Look up floor ID from node-to-floor mapping
            val floorId = nodeToFloorMap[currentNodeId]
            if (floorId == null) {
                Log.w(TAG, "getFloorNumberFromTrilateration: No floor mapping for node $currentNodeId")
                return null
            }

            // Find the floor object and return its floor number
            val floor = floors.find { it.id == floorId }
            if (floor == null) {
                Log.w(TAG, "getFloorNumberFromTrilateration: No floor found for floorId $floorId")
                return null
            }
            
            Log.d(TAG, "getFloorNumberFromTrilateration: node=$currentNodeId -> floorId=$floorId -> floorNumber=${floor.floorNumber}")
            return floor.floorNumber
        } catch (e: Exception) {
            Log.e(TAG, "Error getting floor number from trilateration", e)
            return null
        }
    }

    /**
     * Navigate user to their assigned level using trilaterated position
     */
    private fun navigateToAssignedLevel(assignment: UserAssignment) {
        lifecycleScope.launch {
            try {
                // Get current node ID from localization (trilateration result)
                val currentNodeId = localizationController.localizationState.value.currentNodeId
                val targetLevel = assignment.level

                if (currentNodeId == null) {
                    Log.w(TAG, "Current node ID not available yet, waiting for trilateration...")
                    // Fall back to the old method
                    drawPathToAccessibilityLevel(assignment)
                    return@launch
                }

                if (targetLevel == null) {
                    Log.w(TAG, "Target level not available in assignment")
                    // Fall back to the old method
                    drawPathToAccessibilityLevel(assignment)
                    return@launch
                }

                // Parse node ID to integer
                val currentNodeIdInt = currentNodeId.toIntOrNull()
                if (currentNodeIdInt == null) {
                    Log.e(TAG, "Failed to parse current node ID: $currentNodeId")
                    // Fall back to the old method
                    drawPathToAccessibilityLevel(assignment)
                    return@launch
                }

                Log.d(TAG, "Navigating to assigned level: currentNodeId=$currentNodeIdInt, targetLevel=$targetLevel")

                // Call the navigateToLevel API
                val pathFeatureCollection = apiService.navigateToLevel(currentNodeIdInt, targetLevel)

                if (pathFeatureCollection != null) {
                    Log.d(TAG, "Navigation path received with ${pathFeatureCollection.features()?.size ?: 0} features")
                    displayPath(pathFeatureCollection, targetLevel)
                    fabClearPath.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Navigation to Level $targetLevel", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "No path found to assigned level, falling back to accessibility-based navigation")
                    // Fall back to the old method
                    drawPathToAccessibilityLevel(assignment)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to assigned level", e)
                // Fall back to the old method
                drawPathToAccessibilityLevel(assignment)
            }
        }
    }

    /**
     * Draw path to nearest node with correct accessibility level based on assignment
     */
    private fun drawPathToAccessibilityLevel(assignment: UserAssignment) {
        lifecycleScope.launch {
            try {
                // Determine required accessibility level based on age and disability
                val requiredLevel = when {
                    assignment.isDisabled -> 1 // Highest accessibility (wheelchair accessible)
                    assignment.age >= 65 -> 2 // Medium accessibility (elderly friendly)
                    else -> 3 // Standard accessibility
                }

                Log.d(TAG, "Finding nearest node with accessibility level $requiredLevel")

                // Get current position
                val position = localizationController.getCurrentPosition()
                if (position == null) {
                    Log.w(TAG, "Position not available for path drawing")
                    return@launch
                }

                val (currentX, currentY) = position

                // Fetch route nodes for current floor
                val floorId = currentFloor?.id
                if (floorId == null) {
                    Log.w(TAG, "Floor ID not available")
                    return@launch
                }

                val routeNodes = apiService.getRouteNodesByFloor(floorId)
                if (routeNodes.isNullOrEmpty()) {
                    Log.w(TAG, "No route nodes found for floor $floorId")
                    return@launch
                }

                // Find nearest node with matching level
                val targetNode = routeNodes
                    .filter { it.level == requiredLevel }
                    .minByOrNull { node ->
                        val dx = node.x - currentX
                        val dy = node.y - currentY
                        Math.sqrt(dx * dx + dy * dy)
                    }

                if (targetNode == null) {
                    Log.w(TAG, "No nodes found with accessibility level $requiredLevel")
                    Toast.makeText(this@MainActivity, "No accessible route found for this level", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d(TAG, "Found target node ${targetNode.id} at (${targetNode.x}, ${targetNode.y})")

                // Create path request to target node
                val pathRequest = PathRequest(
                    userLocation = UserLocation(
                        latitude = currentY,
                        longitude = currentX
                    ),
                    destinationPoiId = targetNode.id // Using node ID as destination
                )

                // Get and display path
                val pathFeatureCollection = apiService.findPath(pathRequest)

                if (pathFeatureCollection != null) {
                    displayPath(pathFeatureCollection, requiredLevel)
                    fabClearPath.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Path to accessibility level $requiredLevel", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Path to accessibility level $requiredLevel displayed")
                } else {
                    Log.w(TAG, "No path found to target node")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error drawing path to accessibility level", e)
            }
        }
    }

    /**
     * Check if Bluetooth permissions are granted
     * Note: ACCESS_FINE_LOCATION is required for Bluetooth scanning on Android 11 and below
     * This is NOT used for GPS - only for Bluetooth beacon scanning
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Use dedicated Bluetooth permissions
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 and below: ACCESS_FINE_LOCATION required for Bluetooth scanning
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request battery optimization exemption to allow unrestricted Bluetooth scanning
     * This prevents Android from throttling BLE scans
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "Requesting battery optimization exemption for unrestricted BLE scanning")
                try {
                    val intent = android.content.Intent().apply {
                        action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Toast.makeText(
                        this,
                        "Please allow unrestricted battery usage for accurate indoor navigation",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request battery optimization exemption", e)
                }
            } else {
                Log.d(TAG, "Battery optimization already exempted - BLE scanning unrestricted")
            }
        }
    }

    /**
     * Show QR code dialog for visitor access
     */
    private fun showQrCodeDialog() {
        // Use the stored visitor ID from the API
        val currentVisitorId = visitorId

        if (currentVisitorId == null) {
            Toast.makeText(this, "No visitor ID assigned. Please try again later.", Toast.LENGTH_SHORT).show()
            // Optionally, try to assign a new visitor ID
            lifecycleScope.launch {
                assignVisitorId()
                if (visitorId != null) {
                    showQrCodeDialog()
                }
            }
            return
        }

        val visitorUrl = "${ApiConstants.API_BASE_URL}/api/visitor/$currentVisitorId/page"

        // Generate QR code bitmap
        val qrBitmap = generateQrCode(visitorUrl, 800, 800)

        if (qrBitmap == null) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            return
        }

        // Create and show dialog
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_qr_code)

        // Set up dialog views
        val qrImageView = dialog.findViewById<ImageView>(R.id.qrCodeImageView)
        val visitorIdText = dialog.findViewById<TextView>(R.id.visitorIdText)
        val urlText = dialog.findViewById<TextView>(R.id.qrCodeUrlText)
        val openBrowserButton = dialog.findViewById<Button>(R.id.openBrowserButton)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)

        qrImageView.setImageBitmap(qrBitmap)
        visitorIdText.text = "Visitor ID: $currentVisitorId"
        urlText.text = "Scan to view visitor information"

        openBrowserButton.setOnClickListener {
            // Open URL in browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(visitorUrl))
                startActivity(browserIntent)
                Log.d(TAG, "Opening visitor page in browser: $visitorUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Error opening browser", e)
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        Log.d(TAG, "QR code displayed for visitor ID: $currentVisitorId")
        Log.d(TAG, "QR code URL: $visitorUrl")
    }

    /**
     * Generate a QR code bitmap from a string
     */
    private fun generateQrCode(content: String, width: Int, height: Int): Bitmap? {
        return try {
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: WriterException) {
            Log.e(TAG, "Error generating QR code", e)
            null
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        super.onDestroy()

        // Stop and cleanup localization
        if (isLocalizationActive) {
            localizationController.stop()
        }
        localizationController.cleanup()

        mapView.onDestroy()
        apiService.cleanup()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}