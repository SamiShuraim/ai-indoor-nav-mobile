package com.KFUPM.ai_indoor_nav_mobile.localization.examples

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.KFUPM.ai_indoor_nav_mobile.localization.LocalizationController
import com.KFUPM.ai_indoor_nav_mobile.services.ApiService
import kotlinx.coroutines.launch

/**
 * Example integration for MainActivity
 * Shows how to automatically start localization when app launches
 */
abstract class MainActivityIntegration : AppCompatActivity() {
    
    private val TAG = "MainActivity"
    
    protected lateinit var localizationController: LocalizationController
    private lateinit var apiService: ApiService
    
    private var isLocalizationStarted = false
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            startLocalizationFlow()
        } else {
            Log.e(TAG, "Some permissions denied")
            Toast.makeText(this, "Location permissions required for indoor navigation", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        localizationController = LocalizationController(this)
        apiService = ApiService()
        
        // Check permissions and start
        if (hasRequiredPermissions()) {
            startLocalizationFlow()
        } else {
            requestRequiredPermissions()
        }
    }
    
    /**
     * Main flow: Fetch data from backend, then auto-initialize localization
     */
    private fun startLocalizationFlow() {
        if (isLocalizationStarted) {
            Log.d(TAG, "Localization already started")
            return
        }
        
        lifecycleScope.launch {
            try {
                showLoadingIndicator(true)
                
                // Step 1: Fetch available floors from backend
                Log.d(TAG, "Fetching buildings and floors...")
                val buildings = apiService.getBuildings()
                
                if (buildings.isNullOrEmpty()) {
                    Log.e(TAG, "No buildings found")
                    showError("Unable to load building data")
                    return@launch
                }
                
                // Get first building (or let user select)
                val buildingId = buildings[0].id
                val floors = apiService.getFloorsByBuilding(buildingId)
                
                if (floors.isNullOrEmpty()) {
                    Log.e(TAG, "No floors found for building $buildingId")
                    showError("No floor data available")
                    return@launch
                }
                
                val availableFloorIds = floors.map { it.id }
                Log.d(TAG, "Found ${availableFloorIds.size} floors: $availableFloorIds")
                
                // Step 2: Auto-initialize localization (scans beacons to determine floor & position)
                Log.d(TAG, "Auto-initializing localization...")
                showStatus("Scanning for beacons...")
                
                val success = localizationController.autoInitialize(
                    availableFloorIds = availableFloorIds,
                    scanDurationMs = 5000 // 5 seconds
                )
                
                if (!success) {
                    Log.e(TAG, "Auto-initialization failed")
                    showError("Could not determine your position. Please check that:\n" +
                            "1. Bluetooth is enabled\n" +
                            "2. You are near beacon-equipped areas\n" +
                            "3. Location permissions are granted")
                    return@launch
                }
                
                // Step 3: Start continuous localization
                Log.d(TAG, "Starting continuous localization...")
                localizationController.start()
                isLocalizationStarted = true
                
                // Step 4: Observe position updates
                observeLocalizationUpdates()
                
                showStatus("Localization active")
                showLoadingIndicator(false)
                
                Log.d(TAG, "Localization flow complete!")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in localization flow", e)
                showError("Error starting localization: ${e.message}")
                showLoadingIndicator(false)
            }
        }
    }
    
    /**
     * Observe real-time position updates
     */
    private fun observeLocalizationUpdates() {
        lifecycleScope.launch {
            localizationController.localizationState.collect { state ->
                // Get current position
                val nodeId = state.currentNodeId
                val confidence = state.confidence
                
                Log.d(TAG, "Position: node=$nodeId, confidence=${String.format("%.2f", confidence)}")
                
                // Get coordinates
                val position = localizationController.getCurrentPosition()
                if (position != null) {
                    val (x, y) = position
                    Log.d(TAG, "Coordinates: ($x, $y)")
                    
                    // Update map marker
                    onPositionUpdate(x, y, confidence)
                }
                
                // Handle low confidence
                if (confidence < 0.4) {
                    Log.w(TAG, "Low position confidence")
                    onLowConfidence()
                } else {
                    onNormalConfidence()
                }
                
                // Debug info
                state.debug?.let { debug ->
                    if (debug.visibleBeaconCount < 3) {
                        Log.w(TAG, "Only ${debug.visibleBeaconCount} beacons visible")
                    }
                    
                    if (debug.junctionAmbiguity) {
                        Log.d(TAG, "At junction - position may be ambiguous")
                        onJunctionDetected()
                    }
                    
                    if (debug.tickDurationMs > 50) {
                        Log.w(TAG, "Slow localization tick: ${debug.tickDurationMs}ms")
                    }
                }
            }
        }
    }
    
    /**
     * Check for updates periodically (e.g., once per hour)
     */
    private fun scheduleConfigUpdates() {
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3600000) // 1 hour
                
                val hasUpdate = localizationController.checkForUpdates()
                if (hasUpdate) {
                    Log.d(TAG, "Config update available")
                    // Optionally prompt user or auto-reload
                    // localizationController.reload()
                }
            }
        }
    }
    
    // Abstract methods to be implemented by MainActivity
    
    /**
     * Called when position is updated
     */
    protected abstract fun onPositionUpdate(x: Double, y: Double, confidence: Double)
    
    /**
     * Called when confidence is low
     */
    protected abstract fun onLowConfidence()
    
    /**
     * Called when confidence returns to normal
     */
    protected abstract fun onNormalConfidence()
    
    /**
     * Called when at a junction
     */
    protected abstract fun onJunctionDetected()
    
    /**
     * Show loading indicator
     */
    protected abstract fun showLoadingIndicator(show: Boolean)
    
    /**
     * Show status message
     */
    protected abstract fun showStatus(message: String)
    
    /**
     * Show error message
     */
    protected abstract fun showError(message: String)
    
    // Permission handling
    
    private fun hasRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestRequiredPermissions() {
        val permissions = getRequiredPermissions()
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        return permissions
    }
    
    // Lifecycle
    
    override fun onResume() {
        super.onResume()
        // Localization continues in background via StateFlow
    }
    
    override fun onPause() {
        super.onPause()
        // Keep running in background for continuous tracking
        // Or stop to save battery: localizationController.stop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        localizationController.stop()
        localizationController.cleanup()
        apiService.cleanup()
    }
}

/**
 * Concrete example implementation
 */
class ExampleMainActivity : MainActivityIntegration() {
    
    override fun onPositionUpdate(x: Double, y: Double, confidence: Double) {
        // Update your map view
        // mapView.updateUserLocation(x, y)
        
        // Update UI with confidence indicator
        // confidenceTextView.text = "Accuracy: ${(confidence * 100).toInt()}%"
        
        Log.d("ExampleActivity", "Position updated: ($x, $y) with confidence $confidence")
    }
    
    override fun onLowConfidence() {
        // Show warning icon or message
        // statusIcon.setImageResource(R.drawable.ic_signal_weak)
        // Toast.makeText(this, "Weak signal", Toast.LENGTH_SHORT).show()
    }
    
    override fun onNormalConfidence() {
        // Clear warning
        // statusIcon.setImageResource(R.drawable.ic_signal_strong)
    }
    
    override fun onJunctionDetected() {
        // Optionally show "calculating..." indicator
        // statusText.text = "Calculating position..."
    }
    
    override fun showLoadingIndicator(show: Boolean) {
        // progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    override fun showStatus(message: String) {
        // statusTextView.text = message
        Log.d("ExampleActivity", "Status: $message")
    }
    
    override fun showError(message: String) {
        // Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Or show in a TextView/Dialog
        Log.e("ExampleActivity", "Error: $message")
    }
}
