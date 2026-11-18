package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.index.NeighborBuffer
import com.wingmanc2.core.algorithms.index.SpatialHashIndex
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

sealed interface Association {
    data class Matched(val entityId: Long, val score: Float, val distanceM: Double) : Association
    data class Unmatched(val bestScore: Float) : Association
}

class EntityResolver @Inject constructor() {
    private val index = SpatialHashIndex(CELL_SIZE_M, EXPECTED_ENTITIES)
    private val candidates = CandidateTable(EXPECTED_ENTITIES)
    private val neighbors = NeighborBuffer()
    private val unitQuery = FloatArray(EMBEDDING_DIM)

    @Synchronized
    fun register(id: Long, kind: EntityKind, position: LocalPoint, embedding: FloatArray) {
        index.put(id, position.x, position.y)
        candidates.upsert(id, kind, embedding)
    }

    @Synchronized
    fun forget(id: Long): Boolean = index.remove(id) and candidates.remove(id)

    @Synchronized
    fun resolve(observation: NormalizedObservation, embedding: FloatArray): Association {
        normalizeInto(embedding, unitQuery)
        val gate = max(gateFor(observation.kind), observation.uncertaintyM)
        val sigma = gate * 0.5
        val inverseTwoSigmaSq = 1.0 / (2.0 * sigma * sigma)
        val hits = index.queryRadius(observation.position.x, observation.position.y, gate, neighbors)
        var bestId = NONE
        var bestScore = 0f
        var bestDistance = 0.0
        for (i in 0 until hits) {
            val id = neighbors.id(i)
            val kind = candidates.kindOf(id) ?: continue
            val compatibility = compatibility(observation.kind, kind)
            if (compatibility == 0f) continue
            val distance = neighbors.distanceM(i)
            val spatial = exp(-distance * distance * inverseTwoSigmaSq).toFloat()
            val semantic = candidates.similarity(id, unitQuery).coerceAtLeast(0f)
            val score = SPATIAL_WEIGHT * spatial + KIND_WEIGHT * compatibility + SEMANTIC_WEIGHT * semantic
            if (score > bestScore) {
                bestScore = score
                bestId = id
                bestDistance = distance
            }
        }
        return if (bestId != NONE && bestScore >= MATCH_THRESHOLD) Association.Matched(bestId, bestScore, bestDistance) else Association.Unmatched(bestScore)
    }

    private fun gateFor(kind: EntityKind): Double = when (kind) {
        EntityKind.EMITTER -> 700.0
        EntityKind.REPORT -> 600.0
        EntityKind.ZONE -> 800.0
        EntityKind.TRACK -> 400.0
        EntityKind.ASSET -> 300.0
        EntityKind.SENSOR -> 200.0
        EntityKind.CLASS -> 0.0
    }

    private fun compatibility(observed: EntityKind, existing: EntityKind): Float = when {
        observed == existing -> 1f
        observed == EntityKind.CLASS || existing == EntityKind.CLASS -> 0f
        observed == EntityKind.REPORT || existing == EntityKind.REPORT -> 0.7f
        observed in MOBILE && existing in MOBILE -> 0.6f
        else -> 0f
    }

    private fun normalizeInto(source: FloatArray, target: FloatArray) {
        require(source.size == EMBEDDING_DIM) { "Embedding must have $EMBEDDING_DIM dimensions" }
        var sumSq = 0f
        for (v in source) sumSq += v * v
        val inverse = if (sumSq == 0f) 0f else 1f / sqrt(sumSq)
        for (d in 0 until EMBEDDING_DIM) target[d] = source[d] * inverse
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val CELL_SIZE_M = 250.0
        const val EXPECTED_ENTITIES = 256
        const val SPATIAL_WEIGHT = 0.45f
        const val KIND_WEIGHT = 0.25f
        const val SEMANTIC_WEIGHT = 0.30f
        const val MATCH_THRESHOLD = 0.45f
        val MOBILE = setOf(EntityKind.TRACK, EntityKind.EMITTER, EntityKind.ASSET)
    }
}
