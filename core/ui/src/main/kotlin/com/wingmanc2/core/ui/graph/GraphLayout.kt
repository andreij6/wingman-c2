package com.wingmanc2.core.ui.graph

import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RelationType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal class GraphLayout(
    val nodes: List<OntologyEntity>,
    val edges: List<OntologyEdge>,
    private val u: FloatArray,
    private val v: FloatArray,
    val ghostParents: IntArray,
    private val ghostDx: FloatArray,
    private val ghostDy: FloatArray,
) {
    val focus: OntologyEntity get() = nodes[0]

    fun x(node: Int, width: Float): Float = u[node] * width

    fun y(node: Int, height: Float): Float = v[node] * height

    fun isAboveFocus(node: Int): Boolean = v[node] < v[0]

    fun ghostX(ghost: Int, width: Float, density: Float): Float = x(ghostParents[ghost], width) + ghostDx[ghost] * density

    fun ghostY(ghost: Int, height: Float, density: Float): Float = y(ghostParents[ghost], height) + ghostDy[ghost] * density

    companion object {
        private const val SPREAD_X = 0.34f
        private const val SPREAD_Y = 0.23f
        private const val FOCUS_V = 0.5f
        private val SQRT2 = sqrt(2.0)
        private val RelationRank = IntArray(RelationType.entries.size).also { rank ->
            listOf(RelationType.CORROBORATES, RelationType.NEAR, RelationType.DETECTED_IN, RelationType.INFERRED, RelationType.TASKED_TO)
                .forEachIndexed { i, relation -> rank[relation.ordinal] = i }
        }

        fun of(graph: EntityGraph, maxNeighbors: Int, maxGhosts: Int): GraphLayout? {
            val byId = HashMap<Long, OntologyEntity>(graph.nodes.size * 2)
            for (node in graph.nodes) byId[node.id] = node
            val focus = byId[graph.focusId] ?: return null
            val incident = LinkedHashMap<Long, OntologyEdge>()
            for (edge in graph.edges) {
                val other = when (graph.focusId) {
                    edge.sourceId -> edge.targetId
                    edge.targetId -> edge.sourceId
                    else -> continue
                }
                if (other != graph.focusId && other in byId) incident.putIfAbsent(other, edge)
            }
            val ranked = incident.entries
                .sortedWith(compareBy({ RelationRank[it.value.relation.ordinal] }, { byId.getValue(it.key).key }))
                .take(maxNeighbors)
            val count = ranked.size + 1
            val nodes = ArrayList<OntologyEntity>(count).apply {
                add(focus)
                ranked.forEach { add(byId.getValue(it.key)) }
            }
            val u = FloatArray(count)
            val v = FloatArray(count)
            u[0] = 0.5f
            v[0] = FOCUS_V
            for (k in ranked.indices) {
                val theta = PI + (k + 0.5) * 2 * PI / ranked.size
                u[k + 1] = 0.5f + SPREAD_X * (cos(theta) * SQRT2).coerceIn(-1.0, 1.0).toFloat()
                v[k + 1] = FOCUS_V + SPREAD_Y * (sin(theta) * SQRT2).coerceIn(-1.0, 1.0).toFloat()
            }
            val ghostCount = (graph.totalNodes - count).coerceIn(0, maxGhosts)
            val parents = ghostSlots(ranked.size).take(ghostCount).toIntArray()
            val dx = FloatArray(parents.size)
            val dy = FloatArray(parents.size)
            var focusGhosts = 0
            for (g in parents.indices) {
                val parent = parents[g]
                if (parent == 0) {
                    dx[g] = if (focusGhosts == 0) -40f else 50f
                    dy[g] = if (focusGhosts == 0) 100f else 108f
                    focusGhosts++
                } else {
                    val side = sign(u[parent] - 0.5f).takeIf { it != 0f } ?: 1f
                    dx[g] = side * if (v[parent] < FOCUS_V) 40f else 46f
                    dy[g] = if (v[parent] < FOCUS_V) 42f else -20f
                }
            }
            return GraphLayout(nodes, ranked.map { it.value }, u, v, parents, dx, dy)
        }

        private fun ghostSlots(neighbors: Int): List<Int> = buildList {
            if (neighbors > 0) add(1)
            if (neighbors > 1) add(2)
            add(0)
            add(0)
            for (n in 3..neighbors) add(n)
        }
    }
}
