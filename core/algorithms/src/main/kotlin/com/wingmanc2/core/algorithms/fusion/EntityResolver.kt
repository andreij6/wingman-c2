package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.index.NeighborBuffer
import com.wingmanc2.core.algorithms.index.SpatialHashIndex
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

sealed interface Association {
    data class Matched(val entityId: Long, val score: Float, val distanceM: Double) : Association
    data class Unmatched(val bestScore: Float) : Association
}

@Singleton
class EntityResolver @Inject constructor() {
    private val index = SpatialHashIndex(CELL_SIZE_M, EXPECTED_ENTITIES)
    private val candidates = CandidateTable(EXPECTED_ENTITIES)
    private val neighbors = NeighborBuffer()

    @Synchronized
    fun register(entity: OntologyEntity, unitEmbedding: FloatArray) {
        requireEmbedding(unitEmbedding)
        val position = OperationalArea.projection.toLocal(entity.position)
        index.put(entity.id, position.x, position.y)
        val frequency = entity.attributes.field(EntityAttributes.FREQ, EntityAttributes.BAND)?.let(::frequencyGhz) ?: Double.NaN
        candidates.upsert(entity.id, entity.kind, frequency, unitEmbedding)
    }

    @Synchronized
    fun within(center: LocalPoint, radiusM: Double): Map<Long, Double> {
        val hits = index.queryRadius(center.x, center.y, radiusM, neighbors)
        return HashMap<Long, Double>(hits * 2).apply { for (i in 0 until hits) put(neighbors.id(i), neighbors.distanceM(i)) }
    }

    @Synchronized
    fun resolve(observation: NormalizedObservation, unitEmbedding: FloatArray): Association {
        requireEmbedding(unitEmbedding)
        if (!observation.located) return Association.Unmatched(0f)
        val halfGate = gateFor(observation.kind) / 2
        val ray = observation.bearing
        val fixSigma = hypot(halfGate, observation.uncertaintyM)
        val hits = if (ray == null) {
            index.queryRadius(observation.position.x, observation.position.y, GATE_SIGMAS * fixSigma, neighbors)
        } else {
            index.queryRadius(ray.origin.x, ray.origin.y, ray.maxRangeM + GATE_SIGMAS * halfGate, neighbors)
        }
        val azimuth = (ray?.azimuthDeg ?: 0.0) * PI / 180.0
        val ux = sin(azimuth)
        val uy = cos(azimuth)
        var bestId = NONE
        var bestScore = 0f
        var bestResidual = 0.0
        for (i in 0 until hits) {
            val id = neighbors.id(i)
            val row = candidates.rowOf(id)
            val compatibility = compatibility(observation.kind, candidates.kindAt(row))
            if (compatibility == 0f || !sameEmitterBand(observation.frequencyGhz, candidates.frequencyGhzAt(row))) continue
            val residual: Double
            val sigma: Double
            if (ray == null) {
                residual = neighbors.distanceM(i)
                sigma = fixSigma
            } else {
                val along = neighbors.dx(i) * ux + neighbors.dy(i) * uy
                if (along <= 0.0) continue
                residual = abs(neighbors.dx(i) * uy - neighbors.dy(i) * ux)
                sigma = hypot(halfGate, AZIMUTH_SIGMA_RAD * along)
                if (residual > GATE_SIGMAS * sigma) continue
            }
            val spatial = (exp(-residual * residual / (2.0 * sigma * sigma)) * halfGate / sigma).toFloat()
            val semantic = candidates.similarityAt(row, unitEmbedding).coerceAtLeast(0f)
            val score = SPATIAL_WEIGHT * spatial + KIND_WEIGHT * compatibility + SEMANTIC_WEIGHT * semantic
            if (score > bestScore) {
                bestScore = score
                bestId = id
                bestResidual = residual
            }
        }
        return if (bestId != NONE && bestScore >= MATCH_THRESHOLD) Association.Matched(bestId, bestScore, bestResidual) else Association.Unmatched(bestScore)
    }

    private fun requireEmbedding(embedding: FloatArray) =
        require(embedding.size == EMBEDDING_DIM) { "Embedding must have $EMBEDDING_DIM dimensions" }

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
        existing !in FUSIBLE || observed !in FUSIBLE -> 0f
        observed == existing -> 1f
        observed == EntityKind.REPORT || existing == EntityKind.REPORT -> 0.7f
        else -> 0.6f
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val CELL_SIZE_M = 250.0
        const val EXPECTED_ENTITIES = 256
        const val SPATIAL_WEIGHT = 0.45f
        const val KIND_WEIGHT = 0.25f
        const val SEMANTIC_WEIGHT = 0.30f
        const val MATCH_THRESHOLD = 0.45f
        const val GATE_SIGMAS = 2.0
        const val AZIMUTH_SIGMA_RAD = 3.0 * PI / 180.0
        val FUSIBLE = setOf(EntityKind.TRACK, EntityKind.EMITTER, EntityKind.ASSET, EntityKind.REPORT)
    }
}
