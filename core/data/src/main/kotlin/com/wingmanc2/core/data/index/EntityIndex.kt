package com.wingmanc2.core.data.index

import com.wingmanc2.core.algorithms.fusion.Association
import com.wingmanc2.core.algorithms.fusion.EntityResolver
import com.wingmanc2.core.algorithms.fusion.NormalizedObservation
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EntityIndex @Inject constructor(
    private val resolver: EntityResolver,
) {
    private val idsByKey = ConcurrentHashMap<String, Long>()
    private val positions = ConcurrentHashMap<String, LocalPoint>()

    val landmarks: Map<String, LocalPoint> get() = positions

    fun register(entity: OntologyEntity, embedding: FloatArray) {
        if (entity.kind == EntityKind.CLASS) return
        idsByKey[entity.key] = entity.id
        positions[EntityKeys.normalize(entity.key)] = OperationalArea.projection.toLocal(entity.position)
        resolver.register(entity, embedding)
    }

    fun idOf(key: String): Long? = idsByKey[key] ?: idsByKey[EntityKeys.normalize(key)]

    fun positionOf(key: String): LocalPoint? = positions[EntityKeys.normalize(key)]

    fun within(center: LocalPoint, radiusM: Double): Map<Long, Double> = resolver.within(center, radiusM)

    fun resolve(observation: NormalizedObservation, embedding: FloatArray): Association = resolver.resolve(observation, embedding)
}
