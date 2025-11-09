package com.KFUPM.ai_indoor_nav_mobile.localization.examples

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.KFUPM.ai_indoor_nav_mobile.localization.LocalizationController
import kotlinx.coroutines.launch

/**
 * Example Activity showing how to integrate LocalizationController
 * 
 * This is a reference implementation - adapt to your actual Activity structure
 */
class LocalizationExampleActivity : AppCompatActivity() {
    
    private val TAG = "LocalizationExample"
    
    private lateinit var localizationController: LocalizationController
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            initializeLocalization()
        } else {
            Log.e(TAG, "Some permissions denied")
            // Handle permission denial
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize controller
        localizationController = LocalizationController(this)
        
        // Check and request permissions
        if (hasRequiredPermissions()) {
            initializeLocalization()
        } else {
            requestRequiredPermissions()
        }
    }
    
    /**
     * Initialize localization for a specific floor
     */
    private fun initializeLocalization() {
        lifecycleScope.launch {
            val floorId = 1 // Get from your app's state
            val initialNodeId = "entrance_node" // Optional starting position
            
            val success = localizationController.initialize(
                floorId = floorId,
                initialNodeId = initialNodeId
            )
            
            if (success) {
                // Start localization
                localizationController.start()
                
                // Observe state updates
                observeLocalizationState()
            } else {
                Log.e(TAG, "Failed to initialize localization")
            }
        }
    }
    
    /**
     * Observe localization state updates
     */
    private fun observeLocalizationState() {
        lifecycleScope.launch {
            localizationController.localizationState.collect { state ->
                // Update UI with current location
                val nodeId = state.currentNodeId
                val confidence = state.confidence
                
                Log.d(TAG, "Current node: $nodeId (confidence: ${String.format("%.2f", confidence)})")
                
                // Get coordinates
                val position = localizationController.getCurrentPosition()
                if (position != null) {
                    val (x, y) = position
                    Log.d(TAG, "Position: ($x, $y)")
                    
                    // Update map marker, navigation, etc.
                    updateMapMarker(x, y)
                }
                
                // Handle debug info
                state.debug?.let { debug ->
                    Log.d(TAG, "Visible beacons: ${debug.visibleBeaconCount}")
                    Log.d(TAG, "Top 3 nodes:")
                    debug.topPosteriors.forEachIndexed { index, (id, prob) ->
                        Log.d(TAG, "  ${index + 1}. $id: ${String.format("%.3f", prob)}")
                    }
                    
                    if (debug.junctionAmbiguity) {
                        Log.w(TAG, "Junction ambiguity detected")
                        // Show "calculating position" indicator
                    }
                    
                    if (debug.tickDurationMs > 20) {
                        Log.w(TAG, "Slow tick: ${debug.tickDurationMs}ms")
                    }
                }
                
                // Handle low confidence
                if (confidence < 0.4) {
                    Log.w(TAG, "Low confidence - weak signal")
                    // Show warning to user
                    showWeakSignalWarning()
                }
            }
        }
    }
    
    /**
     * Check for updates periodically (e.g., every 5 minutes)
     */
    private fun checkForConfigUpdates() {
        lifecycleScope.launch {
            val hasUpdate = localizationController.checkForUpdates()
            if (hasUpdate) {
                Log.d(TAG, "Config update available")
                // Prompt user or auto-reload
                localizationController.reload()
            }
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    private fun hasRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Request required permissions
     */
    private fun requestRequiredPermissions() {
        val permissions = getRequiredPermissions()
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    /**
     * Get list of required permissions based on Android version
     */
    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 11 and below
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // Step detection (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        return permissions
    }
    
    // Placeholder UI methods - implement based on your app
    
    private fun updateMapMarker(x: Double, y: Double) {
        // Update your map view to show user's position
        // Example: mapView.updateUserMarker(x, y)
    }
    
    private fun showWeakSignalWarning() {
        // Show UI warning about weak signal
        // Example: Toast.makeText(this, "Weak signal", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        // Restart localization if it was running
        // localizationController.start()
    }
    
    override fun onPause() {
        super.onPause()
        // Optionally stop localization to save battery
        // localizationController.stop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        localizationController.stop()
        localizationController.cleanup()
    }
}
