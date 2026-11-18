package com.wingmanc2.core.data.index

import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.algorithms.fusion.Association
import com.wingmanc2.core.algorithms.fusion.EntityResolver
import com.wingmanc2.core.algorithms.fusion.NormalizedObservation
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.index.NeighborBuffer
import com.wingmanc2.core.algorithms.index.SpatialHashIndex
import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.database.embedding.semanticText
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EntityIndex @Inject constructor(
    private val resolver: EntityResolver,
    private val hasher: FeatureHasher,
) {
    private val spatial = SpatialHashIndex(CELL_SIZE_M, EXPECTED_ENTITIES)
    private val neighbors = NeighborBuffer()
    private val idsByKey = ConcurrentHashMap<String, Long>()
    private val positions = ConcurrentHashMap<String, LocalPoint>()

    val landmarks: Map<String, LocalPoint> get() = positions

    fun register(entity: OntologyEntity) {
        if (entity.kind == EntityKind.CLASS) return
        val local = OperationalArea.projection.toLocal(entity.position)
        idsByKey[entity.key] = entity.id
        positions[EntityKeys.normalize(entity.key)] = local
        synchronized(spatial) { spatial.put(entity.id, local.x, local.y) }
        if (entity.kind in FUSIBLE) resolver.register(entity.id, entity.kind, local, hasher.embed(entity.semanticText()))
    }

    fun idOf(key: String): Long? = idsByKey[key] ?: idsByKey[EntityKeys.normalize(key)]

    fun positionOf(key: String): LocalPoint? = positions[EntityKeys.normalize(key)]

    fun within(center: LocalPoint, radiusM: Double): Map<Long, Double> = synchronized(spatial) {
        val hits = spatial.queryRadius(center.x, center.y, radiusM, neighbors)
        HashMap<Long, Double>(hits * 2).apply { for (i in 0 until hits) put(neighbors.id(i), neighbors.distanceM(i)) }
    }

    fun resolve(observation: NormalizedObservation, embedding: FloatArray): Association = resolver.resolve(observation, embedding)

    private companion object {
        const val CELL_SIZE_M = 250.0
        const val EXPECTED_ENTITIES = 256
        val FUSIBLE = setOf(EntityKind.TRACK, EntityKind.EMITTER, EntityKind.ASSET, EntityKind.REPORT)
    }
}
