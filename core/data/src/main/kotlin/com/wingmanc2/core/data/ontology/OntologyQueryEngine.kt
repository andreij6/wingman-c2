package com.wingmanc2.core.data.ontology

import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ParsedQuery
import com.wingmanc2.core.model.QueryPredicate
import com.wingmanc2.core.model.QueryResult
import kotlinx.coroutines.flow.first
import java.util.EnumSet
import javax.inject.Inject

internal class OntologyQueryEngine @Inject constructor(
    private val streams: OntologyStreams,
    private val entityStore: EntityStore,
    private val index: EntityIndex,
    private val hasher: FeatureHasher,
    private val clock: Clock,
) {
    private class Ranked(val entity: OntologyEntity, val textDistance: Double, val spatialDistance: Double)

    suspend fun run(query: ParsedQuery): QueryResult {
        val started = System.nanoTime()
        val kinds = EnumSet.noneOf(EntityKind::class.java)
        val affiliations = EnumSet.noneOf(Affiliation::class.java)
        var verified: Boolean? = null
        var seenAfterMs = Long.MIN_VALUE
        var spatial: Map<Long, Double>? = null
        val anchors = HashSet<Long>()
        val terms = ArrayList<String>()
        for (predicate in query.predicates) {
            when (predicate) {
                is QueryPredicate.KindIs -> kinds += predicate.kind
                is QueryPredicate.AffiliationIs -> affiliations += predicate.affiliation
                is QueryPredicate.VerifiedIs -> verified = predicate.verified
                is QueryPredicate.SeenWithin -> seenAfterMs = maxOf(seenAfterMs, clock.nowMs() - predicate.minutes * MS_PER_MINUTE)
                is QueryPredicate.Text -> terms += predicate.term
                is QueryPredicate.Near -> {
                    index.idOf(predicate.anchorKey)?.let(anchors::add)
                    val hits = index.positionOf(predicate.anchorKey)?.let { index.within(it, predicate.radiusM) }.orEmpty()
                    val previous = spatial
                    spatial = if (previous == null) hits else previous.filterKeys(hits::containsKey)
                }
            }
        }
        val text = if (terms.isEmpty()) null else semanticNeighbours(terms.joinToString(" "))
        val near = spatial
        val ranked = ArrayList<Ranked>()
        for (entity in streams.entities.first()) {
            if (entity.id in anchors) continue
            if (kinds.isEmpty()) {
                if (entity.kind == EntityKind.CLASS) continue
            } else if (entity.kind !in kinds) {
                continue
            }
            if (affiliations.isNotEmpty() && entity.affiliation !in affiliations) continue
            if (verified != null && entity.verified != verified) continue
            if (entity.updatedAtMs < seenAfterMs) continue
            val spatialDistance = if (near == null) 0.0 else near[entity.id] ?: continue
            val textDistance = if (text == null) 0.0 else text[entity.id] ?: continue
            ranked += Ranked(entity, textDistance, spatialDistance)
        }
        ranked.sortWith(compareBy<Ranked>({ it.textDistance }, { it.spatialDistance }).thenByDescending { it.entity.confidence })
        return QueryResult(query, ranked.map(Ranked::entity), (System.nanoTime() - started) / NANOS_PER_MICRO)
    }

    private suspend fun semanticNeighbours(text: String): Map<Long, Double> =
        entityStore.similar(hasher.embed(text), TEXT_NEIGHBOURS)
            .filter { it.distance <= MAX_TEXT_DISTANCE }
            .associate { it.entity.id to it.distance }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val NANOS_PER_MICRO = 1_000L
        const val TEXT_NEIGHBOURS = 16
        const val MAX_TEXT_DISTANCE = 0.92
    }
}
