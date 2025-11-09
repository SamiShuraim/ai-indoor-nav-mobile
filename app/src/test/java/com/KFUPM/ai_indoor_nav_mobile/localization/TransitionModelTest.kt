package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphEdge
import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.IndoorGraph
import com.KFUPM.ai_indoor_nav_mobile.localization.models.ImuData
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

class TransitionModelTest {
    
    private lateinit var graphModel: GraphModel
    private lateinit var transitionModel: TransitionModel
    
    @Before
    fun setup() {
        // Create simple linear graph: n1 -- n2 -- n3
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
        
        transitionModel = TransitionModel(
            graphModel = graphModel,
            maxWalkingSpeed = 1.8,
            forwardBiasLambda = 1.5,
            tickDeltaS = 1.0
        )
    }
    
    @Test
    fun testSelfTransition_noSteps() {
        val imuData = ImuData(stepsSinceLastTick = 0, headingRad = null)
        
        val transitions = transitionModel.computeLogTransitions("n1", imuData)
        
        // Should include self-transition
        assertTrue(transitions.containsKey("n1"))
        
        // Self-transition should have high weight when no steps
        val selfLogProb = transitions["n1"]!!
        assertTrue(selfLogProb.isFinite())
    }
    
    @Test
    fun testSelfTransition_withSteps() {
        val imuData = ImuData(stepsSinceLastTick = 5, headingRad = null)
        
        val transitions = transitionModel.computeLogTransitions("n1", imuData)
        
        // Should include self-transition and neighbor
        assertTrue(transitions.containsKey("n1"))
        assertTrue(transitions.containsKey("n2"))
    }
    
    @Test
    fun testSpeedGating() {
        // Try to transition to a far node in 1 second (not feasible at 1.8 m/s)
        val nodes = listOf(
            GraphNode("n1", 0.0, 0.0),
            GraphNode("n_far", 50.0, 0.0) // 50m away
        )
        
        val edges = listOf(
            GraphEdge("n1", "n_far", 50.0, 0.5)
        )
        
        val graph = IndoorGraph(nodes, edges)
        val gm = GraphModel(graph)
        
        val tm = TransitionModel(
            graphModel = gm,
            maxWalkingSpeed = 1.8,
            tickDeltaS = 1.0
        )
        
        val imuData = ImuData(stepsSinceLastTick = 5, headingRad = null)
        val transitions = tm.computeLogTransitions("n1", imuData)
        
        // n_far should NOT be in transitions (speed-gated)
        assertFalse(transitions.containsKey("n_far"))
    }
    
    @Test
    fun testForwardBias_alignedHeading() {
        // Heading pointing East (0 rad), edge also goes East
        val imuData = ImuData(stepsSinceLastTick = 3, headingRad = 0.0)
        
        val transitions = transitionModel.computeLogTransitions("n1", imuData)
        
        // Should include transition to n2
        assertTrue(transitions.containsKey("n2"))
        
        val n2LogProb = transitions["n2"]!!
        assertTrue(n2LogProb.isFinite())
    }
    
    @Test
    fun testForwardBias_oppositeHeading() {
        // Heading pointing West (π rad), edge goes East
        val imuData = ImuData(stepsSinceLastTick = 3, headingRad = PI)
        
        val transitions = transitionModel.computeLogTransitions("n1", imuData)
        
        // Should still include n2 but with lower probability
        assertTrue(transitions.containsKey("n2"))
        
        val n2LogProb = transitions["n2"]!!
        assertTrue(n2LogProb.isFinite())
    }
    
    @Test
    fun testNoHeading() {
        // No heading data - should not crash
        val imuData = ImuData(stepsSinceLastTick = 3, headingRad = null)
        
        val transitions = transitionModel.computeLogTransitions("n1", imuData)
        
        // Should work without heading
        assertTrue(transitions.isNotEmpty())
        assertTrue(transitions.containsKey("n2"))
    }
    
    @Test
    fun testMaxFeasibleDistance() {
        val maxDist = transitionModel.getMaxFeasibleDistance()
        assertEquals(1.8, maxDist, 0.01) // 1.8 m/s * 1 s
    }
    
    @Test
    fun testTransitionNormalization() {
        val imuData = ImuData(stepsSinceLastTick = 3, headingRad = 0.0)
        
        val transitions = transitionModel.computeLogTransitions("n2", imuData)
        
        // Convert to probabilities and sum
        val probs = transitions.values.map { exp(it) }
        val sum = probs.sum()
        
        // Should sum to approximately 1.0
        assertEquals(1.0, sum, 0.01)
    }
}
