package com.KFUPM.ai_indoor_nav_mobile.localization

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.KFUPM.ai_indoor_nav_mobile.localization.models.ImuData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * IMU Tracker for step detection and heading
 */
class ImuTracker(private val context: Context) {
    private val TAG = "ImuTracker"
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    // Fallback sensors if rotation vector not available
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Step counter
    private var stepCount = 0
    private var lastStepTimestampMs = 0L
    private val debounceThresholdMs = 200L // Prevent double-counting
    
    // Heading state
    private var currentHeadingRad: Double? = null
    private val headingEmaAlpha = 0.2 // Smoothing factor
    
    // For accelerometer + magnetometer fallback
    private var gravityValues = FloatArray(3)
    private var magneticValues = FloatArray(3)
    
    // IMU data state flow
    private val _imuData = MutableStateFlow(ImuData(0, null))
    val imuData: StateFlow<ImuData> = _imuData
    
    private var isTracking = false
    
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
                val currentTimeMs = System.currentTimeMillis()
                if (currentTimeMs - lastStepTimestampMs > debounceThresholdMs) {
                    stepCount++
                    lastStepTimestampMs = currentTimeMs
                    Log.d(TAG, "Step detected, total: $stepCount")
                }
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not needed
        }
    }
    
    private val headingListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let { 
                when (it.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        updateHeadingFromRotationVector(it.values)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravityValues = it.values.clone()
                        updateHeadingFromAccelMag()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        magneticValues = it.values.clone()
                        updateHeadingFromAccelMag()
                    }
                }
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                Log.w(TAG, "Sensor accuracy unreliable")
            }
        }
    }
    
    /**
     * Start tracking IMU data
     */
    fun startTracking() {
        if (isTracking) {
            Log.w(TAG, "Already tracking")
            return
        }
        
        // Register step detector
        stepDetector?.let {
            sensorManager.registerListener(
                stepListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Step detector registered")
        } ?: Log.w(TAG, "Step detector not available")
        
        // Register heading sensors
        rotationVectorSensor?.let {
            sensorManager.registerListener(
                headingListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
            Log.d(TAG, "Rotation vector sensor registered")
        } ?: run {
            // Fallback to accelerometer + magnetometer
            accelerometer?.let { acc ->
                magnetometer?.let { mag ->
                    sensorManager.registerListener(headingListener, acc, SensorManager.SENSOR_DELAY_GAME)
                    sensorManager.registerListener(headingListener, mag, SensorManager.SENSOR_DELAY_GAME)
                    Log.d(TAG, "Accelerometer + Magnetometer registered as fallback")
                }
            }
        }
        
        isTracking = true
    }
    
    /**
     * Stop tracking
     */
    fun stopTracking() {
        if (!isTracking) return
        
        sensorManager.unregisterListener(stepListener)
        sensorManager.unregisterListener(headingListener)
        isTracking = false
        Log.d(TAG, "IMU tracking stopped")
    }
    
    /**
     * Get steps since last tick and reset counter
     */
    fun getStepsSinceLastTick(): Int {
        val steps = stepCount
        stepCount = 0
        return steps
    }
    
    /**
     * Get current heading
     */
    fun getCurrentHeading(): Double? {
        return currentHeadingRad
    }
    
    /**
     * Get current IMU data and reset step counter
     */
    fun getImuDataAndReset(): ImuData {
        val steps = getStepsSinceLastTick()
        val heading = getCurrentHeading()
        return ImuData(steps, heading)
    }
    
    /**
     * Update heading from rotation vector sensor
     */
    private fun updateHeadingFromRotationVector(rotationVector: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        
        // orientation[0] is azimuth (heading) in radians
        val azimuth = orientation[0].toDouble()
        updateHeadingWithEma(azimuth)
    }
    
    /**
     * Update heading from accelerometer + magnetometer
     */
    private fun updateHeadingFromAccelMag() {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            gravityValues,
            magneticValues
        )
        
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            val azimuth = orientation[0].toDouble()
            updateHeadingWithEma(azimuth)
        }
    }
    
    /**
     * Apply EMA smoothing to heading
     */
    private fun updateHeadingWithEma(newHeading: Double) {
        val normalized = normalizeAngle(newHeading)
        
        currentHeadingRad = if (currentHeadingRad == null) {
            normalized
        } else {
            // Handle angle wrapping for circular mean
            val diff = normalizeAngle(normalized - currentHeadingRad!!)
            normalizeAngle(currentHeadingRad!! + headingEmaAlpha * diff)
        }
    }
    
    /**
     * Normalize angle to [-π, π]
     */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI
        while (normalized < -PI) normalized += 2 * PI
        return normalized
    }
    
    /**
     * Reset all counters
     */
    fun reset() {
        stepCount = 0
        lastStepTimestampMs = 0L
        currentHeadingRad = null
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopTracking()
        scope.cancel()
    }
}
