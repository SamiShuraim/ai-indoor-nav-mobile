package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class HmmEngineTest {
    
    private lateinit var graphModel: GraphModel
    private lateinit var observationModel: ObservationModel
    private lateinit var transitionModel: TransitionModel
    private lateinit var hmmEngine: HmmEngine
    private lateinit var beacons: List<LocalizationBeacon>
    
    @Before
    fun setup() {
        // Create test graph
        val nodes = listOf(
            GraphNode("n1", 0.0, 0.0),
            GraphNode("n2", 5.0, 0.0),
            GraphNode("n3", 10.0, 0.0)
        )
        
        val edges = listOf(
            GraphEdge("n1", "n2", 5.0, 0.5),
            GraphEdge("n2", "n3", 5.0, 0.5)
        )
        
        val graph = IndoorGraph(nodes, edges)
        graphModel = GraphModel(graph)
        
        // Create test beacons
        beacons = listOf(
            LocalizationBeacon("b1", 0.0, 0.0),
            LocalizationBeacon("b2", 10.0, 0.0)
        )
        
        observationModel = ObservationModel(beacons)
        transitionModel = TransitionModel(graphModel)
        
        hmmEngine = HmmEngine(
            graphModel = graphModel,
            observationModel = observationModel,
            transitionModel = transitionModel,
            hysteresisK = 2
        )
    }
    
    @Test
    fun testInitialization_withNode() {
        hmmEngine.initialize("n1")
        
        val currentNode = hmmEngine.getCurrentNode()
        assertEquals("n1", currentNode)
    }
    
    @Test
    fun testInitialization_uniform() {
        hmmEngine.initialize()
        
        // Should initialize but no specific node yet
        val currentNode = hmmEngine.getCurrentNode()
        assertNull(currentNode) // Will be decided after first update
    }
    
    @Test
    fun testUpdate_singleTick() {
        hmmEngine.initialize("n1")
        
        val rssiMap = mapOf(
            "b1" to -50.0,
            "b2" to -80.0
        )
        
        val imuData = ImuData(stepsSinceLastTick = 0, headingRad = null)
        
        val result = hmmEngine.update(rssiMap, imuData)
        
        assertNotNull(result.nodeId)
        assertTrue(result.confidence >= 0.0 && result.confidence <= 1.0)
        assertEquals(2, result.visibleBeaconCount)
    }
    
    @Test
    fun testHysteresis_requiresKTicks() {
        hmmEngine.initialize("n1")
        
        // Create RSSI pattern that strongly suggests n3
        val rssiMapForN3 = mapOf(
            "b1" to -85.0,
            "b2" to -50.0 // Very close to b2 which is near n3
        )
        
        val imuData = ImuData(stepsSinceLastTick = 5, headingRad = 0.0)
        
        // First tick - should stay at n1 due to hysteresis
        val result1 = hmmEngine.update(rssiMapForN3, imuData)
        assertEquals("n1", result1.nodeId)
        
        // Second tick - might still be at n1 (K=2)
        val result2 = hmmEngine.update(rssiMapForN3, imuData)
        // After K ticks, might change to n3 or intermediate nodes
        assertNotNull(result2.nodeId)
    }
    
    @Test
    fun testUpdate_noBeacons() {
        hmmEngine.initialize("n1")
        
        val rssiMap = emptyMap<String, Double>()
        val imuData = ImuData(stepsSinceLastTick = 0, headingRad = null)
        
        val result = hmmEngine.update(rssiMap, imuData)
        
        // Should maintain last known position
        assertEquals("n1", result.nodeId)
        assertEquals(0, result.visibleBeaconCount)
    }
    
    @Test
    fun testPathHistory() {
        hmmEngine.initialize("n1")
        
        val history = hmmEngine.getPathHistory()
        assertEquals(1, history.size)
        assertEquals("n1", history[0])
    }
    
    @Test
    fun testTopPosteriors() {
        hmmEngine.initialize("n1")
        
        val rssiMap = mapOf(
            "b1" to -60.0,
            "b2" to -70.0
        )
        
        val imuData = ImuData(stepsSinceLastTick = 0, headingRad = null)
        val result = hmmEngine.update(rssiMap, imuData)
        
        val topPosteriors = hmmEngine.getTopPosteriors(result.posteriors, 3)
        
        assertTrue(topPosteriors.isNotEmpty())
        assertTrue(topPosteriors.size <= 3)
        
        // Should be sorted by probability (descending)
        if (topPosteriors.size > 1) {
            assertTrue(topPosteriors[0].second >= topPosteriors[1].second)
        }
    }
    
    @Test
    fun testReset() {
        hmmEngine.initialize("n1")
        
        val rssiMap = mapOf("b1" to -60.0, "b2" to -70.0)
        val imuData = ImuData(stepsSinceLastTick = 0, headingRad = null)
        hmmEngine.update(rssiMap, imuData)
        
        hmmEngine.reset()
        
        val currentNode = hmmEngine.getCurrentNode()
        assertNull(currentNode)
        
        val history = hmmEngine.getPathHistory()
        assertTrue(history.isEmpty())
    }
}
