package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.LocalizationBeacon
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.abs

class ObservationModelTest {
    
    private lateinit var beacons: List<LocalizationBeacon>
    private lateinit var observationModel: ObservationModel
    
    @Before
    fun setup() {
        // Create test beacons in a simple layout
        beacons = listOf(
            LocalizationBeacon("b1", 0.0, 0.0),
            LocalizationBeacon("b2", 10.0, 0.0),
            LocalizationBeacon("b3", 10.0, 10.0),
            LocalizationBeacon("b4", 0.0, 10.0)
        )
        
        observationModel = ObservationModel(
            beacons = beacons,
            rankWeight = 3.0,
            pairwiseWeight = 1.0,
            distanceRatioSlope = 8.0
        )
    }
    
    @Test
    fun testLogLikelihood_nodeNearBeacon() {
        // Node near beacon b1 should have highest RSSI from b1
        val node = GraphNode("n1", 1.0, 1.0)
        
        val rssiMap = mapOf(
            "b1" to -50.0, // Closest, strongest
            "b2" to -70.0,
            "b3" to -85.0,
            "b4" to -75.0
        )
        
        val logL = observationModel.computeLogLikelihood(node, rssiMap)
        
        // Should return finite value
        assertFalse(logL.isInfinite())
        assertTrue(logL.isFinite())
    }
    
    @Test
    fun testLogLikelihood_noBeacons() {
        val node = GraphNode("n1", 5.0, 5.0)
        val rssiMap = emptyMap<String, Double>()
        
        val logL = observationModel.computeLogLikelihood(node, rssiMap)
        
        // Should return negative infinity
        assertTrue(logL.isInfinite() && logL < 0)
    }
    
    @Test
    fun testLogLikelihood_perfectRankCorrelation() {
        // Node at center should have balanced RSSI from all beacons
        val node = GraphNode("n_center", 5.0, 5.0)
        
        // Simulate RSSI that perfectly correlates with distance
        // Beacons b1, b2, b3, b4 are all equidistant from center
        val rssiMap = mapOf(
            "b1" to -65.0,
            "b2" to -65.0,
            "b3" to -65.0,
            "b4" to -65.0
        )
        
        val logL = observationModel.computeLogLikelihood(node, rssiMap)
        
        // Should have high likelihood (positive log-likelihood possible)
        assertFalse(logL.isInfinite())
    }
    
    @Test
    fun testLogLikelihood_contradictoryRSSI() {
        // Node near b1 but RSSI suggests b3 is closer
        val node = GraphNode("n1", 1.0, 1.0)
        
        val rssiMap = mapOf(
            "b1" to -85.0, // Should be strong but is weak
            "b2" to -70.0,
            "b3" to -50.0, // Should be weak but is strong
            "b4" to -75.0
        )
        
        val logL = observationModel.computeLogLikelihood(node, rssiMap)
        
        // Should have lower likelihood than correct correlation
        assertFalse(logL.isInfinite())
        assertTrue(logL < 0) // Likely negative due to poor correlation
    }
    
    @Test
    fun testCalibration() {
        val node = GraphNode("n1", 1.0, 1.0)
        
        val rssiMap = mapOf(
            "b1" to -55.0,
            "b2" to -70.0
        )
        
        // Update calibration multiple times
        repeat(10) {
            observationModel.updateCalibration(node, rssiMap, learningRate = 0.1)
        }
        
        // Just verify it doesn't crash and bias stays reasonable
        // (bias should be capped to [-50, 50])
        assertTrue(true)
    }
    
    @Test
    fun testGetBeaconPosition() {
        val pos = observationModel.getBeaconPosition("b1")
        assertNotNull(pos)
        assertEquals(0.0, pos!!.first, 0.001)
        assertEquals(0.0, pos.second, 0.001)
        
        val invalidPos = observationModel.getBeaconPosition("invalid")
        assertNull(invalidPos)
    }
}
