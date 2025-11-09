package com.KFUPM.ai_indoor_nav_mobile.localization

import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphEdge
import com.KFUPM.ai_indoor_nav_mobile.localization.models.GraphNode
import com.KFUPM.ai_indoor_nav_mobile.localization.models.IndoorGraph
import kotlin.math.sqrt

/**
 * Graph model for navigation nodes and edges
 */
class GraphModel(private val graph: IndoorGraph) {
    
    // Node lookup
    private val nodeMap: Map<String, GraphNode> = graph.nodes.associateBy { it.id }
    
    // Adjacency list: nodeId -> list of (neighborId, edge)
    private val adjacency: Map<String, List<Pair<String, GraphEdge>>> = buildAdjacency()
    
    /**
     * Build adjacency list from edges
     */
    private fun buildAdjacency(): Map<String, List<Pair<String, GraphEdge>>> {
        val adj = mutableMapOf<String, MutableList<Pair<String, GraphEdge>>>()
        
        for (edge in graph.edges) {
            // Forward edge
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to to edge)
            
            // Reverse edge (with inverted forward bias if asymmetric)
            val reverseEdge = edge.copy(
                from = edge.to,
                to = edge.from,
                forwardBias = 1.0 - edge.forwardBias
            )
            adj.getOrPut(edge.to) { mutableListOf() }.add(edge.from to reverseEdge)
        }
        
        return adj
    }
    
    /**
     * Get node by ID
     */
    fun getNode(nodeId: String): GraphNode? {
        return nodeMap[nodeId]
    }
    
    /**
     * Get all nodes
     */
    fun getAllNodes(): List<GraphNode> {
        return graph.nodes
    }
    
    /**
     * Get neighbors of a node
     */
    fun getNeighbors(nodeId: String): List<Pair<String, GraphEdge>> {
        return adjacency[nodeId] ?: emptyList()
    }
    
    /**
     * Get edge between two nodes (if exists)
     */
    fun getEdge(fromId: String, toId: String): GraphEdge? {
        return adjacency[fromId]?.find { it.first == toId }?.second
    }
    
    /**
     * Compute Euclidean distance between two nodes
     */
    fun getDistanceBetweenNodes(nodeId1: String, nodeId2: String): Double? {
        val node1 = nodeMap[nodeId1] ?: return null
        val node2 = nodeMap[nodeId2] ?: return null
        return euclideanDistance(node1.x, node1.y, node2.x, node2.y)
    }
    
    /**
     * Get nodes within radius of a point
     */
    fun getNodesWithinRadius(x: Double, y: Double, radiusM: Double): List<GraphNode> {
        return graph.nodes.filter { node ->
            euclideanDistance(node.x, node.y, x, y) <= radiusM
        }
    }
    
    /**
     * Get nodes within radius of another node
     */
    fun getNodesWithinRadiusOfNode(nodeId: String, radiusM: Double): List<GraphNode> {
        val centerNode = nodeMap[nodeId] ?: return emptyList()
        return getNodesWithinRadius(centerNode.x, centerNode.y, radiusM)
    }
    
    /**
     * Compute unit vector for an edge (direction from -> to)
     */
    fun getEdgeDirection(edge: GraphEdge): Pair<Double, Double>? {
        val fromNode = nodeMap[edge.from] ?: return null
        val toNode = nodeMap[edge.to] ?: return null
        
        val dx = toNode.x - fromNode.x
        val dy = toNode.y - fromNode.y
        val length = sqrt(dx * dx + dy * dy)
        
        if (length < 1e-6) return Pair(0.0, 0.0)
        
        return Pair(dx / length, dy / length)
    }
    
    /**
     * Compute heading angle for an edge (in radians, 0 = East, π/2 = North)
     */
    fun getEdgeHeading(edge: GraphEdge): Double? {
        val direction = getEdgeDirection(edge) ?: return null
        return kotlin.math.atan2(direction.second, direction.first)
    }
    
    /**
     * Check if graph is valid
     */
    fun validate(): Boolean {
        // Check all edges reference valid nodes
        for (edge in graph.edges) {
            if (edge.from !in nodeMap || edge.to !in nodeMap) {
                return false
            }
        }
        return true
    }
    
    /**
     * Euclidean distance helper
     */
    private fun euclideanDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get graph statistics
     */
    fun getStats(): GraphStats {
        return GraphStats(
            nodeCount = graph.nodes.size,
            edgeCount = graph.edges.size,
            avgDegree = adjacency.values.map { it.size }.average()
        )
    }
}

/**
 * Graph statistics
 */
data class GraphStats(
    val nodeCount: Int,
    val edgeCount: Int,
    val avgDegree: Double
)
