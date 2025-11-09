package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphEdge
import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.IndoorGraph
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.abs
import kotlin.math.sqrt

class GraphModelTest {
    
    private lateinit var graphModel: GraphModel
    
    @Before
    fun setup() {
        val nodes = listOf(
            GraphNode("n1", 0.0, 0.0),
            GraphNode("n2", 3.0, 0.0),
            GraphNode("n3", 3.0, 4.0)
        )
        
        val edges = listOf(
            GraphEdge("n1", "n2", 3.0, 0.5),
            GraphEdge("n2", "n3", 4.0, 0.6)
        )
        
        val graph = IndoorGraph(nodes, edges)
        graphModel = GraphModel(graph)
    }
    
    @Test
    fun testGetNode() {
        val node = graphModel.getNode("n1")
        assertNotNull(node)
        assertEquals(0.0, node!!.x, 0.001)
        assertEquals(0.0, node.y, 0.001)
        
        val invalidNode = graphModel.getNode("invalid")
        assertNull(invalidNode)
    }
    
    @Test
    fun testGetAllNodes() {
        val nodes = graphModel.getAllNodes()
        assertEquals(3, nodes.size)
    }
    
    @Test
    fun testGetNeighbors() {
        val neighbors = graphModel.getNeighbors("n1")
        assertEquals(1, neighbors.size)
        assertEquals("n2", neighbors[0].first)
        
        val n2Neighbors = graphModel.getNeighbors("n2")
        assertEquals(2, n2Neighbors.size) // Both forward and reverse edges
    }
    
    @Test
    fun testGetEdge() {
        val edge = graphModel.getEdge("n1", "n2")
        assertNotNull(edge)
        assertEquals(3.0, edge!!.lengthM, 0.001)
        
        val invalidEdge = graphModel.getEdge("n1", "n3")
        assertNull(invalidEdge)
    }
    
    @Test
    fun testGetDistanceBetweenNodes() {
        val dist = graphModel.getDistanceBetweenNodes("n1", "n2")
        assertNotNull(dist)
        assertEquals(3.0, dist!!, 0.001)
        
        val dist2 = graphModel.getDistanceBetweenNodes("n1", "n3")
        assertNotNull(dist2)
        assertEquals(5.0, dist2!!, 0.001) // 3-4-5 triangle
    }
    
    @Test
    fun testGetNodesWithinRadius() {
        val nodes = graphModel.getNodesWithinRadius(0.0, 0.0, 4.0)
        
        // n1 (dist=0) and n2 (dist=3) should be included
        // n3 (dist=5) should be excluded
        assertEquals(2, nodes.size)
        assertTrue(nodes.any { it.id == "n1" })
        assertTrue(nodes.any { it.id == "n2" })
    }
    
    @Test
    fun testGetNodesWithinRadiusOfNode() {
        val nodes = graphModel.getNodesWithinRadiusOfNode("n1", 4.0)
        
        assertEquals(2, nodes.size)
        assertTrue(nodes.any { it.id == "n1" })
        assertTrue(nodes.any { it.id == "n2" })
    }
    
    @Test
    fun testGetEdgeDirection() {
        val direction = graphModel.getEdgeDirection(GraphEdge("n1", "n2", 3.0))
        assertNotNull(direction)
        
        // Direction from (0,0) to (3,0) should be (1,0)
        assertEquals(1.0, direction!!.first, 0.001)
        assertEquals(0.0, direction.second, 0.001)
    }
    
    @Test
    fun testGetEdgeHeading() {
        val heading = graphModel.getEdgeHeading(GraphEdge("n1", "n2", 3.0))
        assertNotNull(heading)
        
        // Heading from (0,0) to (3,0) should be 0 (East)
        assertEquals(0.0, heading!!, 0.001)
    }
    
    @Test
    fun testValidate() {
        assertTrue(graphModel.validate())
        
        // Test invalid graph
        val invalidEdges = listOf(
            GraphEdge("n1", "invalid_node", 1.0)
        )
        
        val invalidGraph = IndoorGraph(
            nodes = listOf(GraphNode("n1", 0.0, 0.0)),
            edges = invalidEdges
        )
        
        val invalidGraphModel = GraphModel(invalidGraph)
        assertFalse(invalidGraphModel.validate())
    }
    
    @Test
    fun testGetStats() {
        val stats = graphModel.getStats()
        assertEquals(3, stats.nodeCount)
        assertEquals(2, stats.edgeCount)
        assertTrue(stats.avgDegree > 0)
    }
}
