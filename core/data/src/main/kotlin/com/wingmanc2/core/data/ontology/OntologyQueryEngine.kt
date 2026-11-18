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

    private class Filter(
        val kinds: Set<EntityKind>,
        val affiliations: Set<Affiliation>,
        val verified: Boolean?,
        val seenAfterMs: Long,
        val excluded: Set<Long>,
    ) {
        val narrows: Boolean get() = kinds.isNotEmpty() || affiliations.isNotEmpty() || verified != null || seenAfterMs != Long.MIN_VALUE

        fun accepts(entity: OntologyEntity): Boolean = when {
            entity.id in excluded -> false
            kinds.isEmpty() && entity.kind == EntityKind.CLASS -> false
            kinds.isNotEmpty() && entity.kind !in kinds -> false
            affiliations.isNotEmpty() && entity.affiliation !in affiliations -> false
            verified != null && entity.verified != verified -> false
            else -> entity.updatedAtMs >= seenAfterMs
        }
    }

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
        val filter = Filter(kinds, affiliations, verified, seenAfterMs, anchors)
        val near = spatial
        val ranked = when {
            terms.isNotEmpty() -> textSearch(terms.joinToString(" "), filter, near)
            near != null -> rank(entityStore.getAll(near.keys), filter, near, null)
            else -> rank(streams.entities.first(), filter, null, null)
        }
        ranked.sortWith(compareBy<Ranked>({ it.textDistance }, { it.spatialDistance }).thenByDescending { it.entity.confidence })
        return QueryResult(query, ranked.map(Ranked::entity), (System.nanoTime() - started) / NANOS_PER_MICRO)
    }

    private suspend fun textSearch(text: String, filter: Filter, near: Map<Long, Double>?): ArrayList<Ranked> {
        val embedding = hasher.embed(text)
        val narrows = filter.narrows || near != null
        var k = TEXT_NEIGHBOURS
        while (true) {
            val matches = entityStore.similar(embedding, k)
            val close = HashMap<Long, Double>(matches.size * 2)
            for (match in matches) {
                if (match.distance > MAX_TEXT_DISTANCE) continue
                if (near == null || match.entityId in near) close[match.entityId] = match.distance
            }
            val ranked = rank(entityStore.getAll(close.keys), filter, near, close)
            val exhausted = matches.size < k || matches.any { it.distance > MAX_TEXT_DISTANCE }
            if (!narrows || exhausted || ranked.size >= TEXT_NEIGHBOURS) return ranked
            k *= TEXT_GROWTH
        }
    }

    private fun rank(
        candidates: Collection<OntologyEntity>,
        filter: Filter,
        near: Map<Long, Double>?,
        text: Map<Long, Double>?,
    ): ArrayList<Ranked> {
        val ranked = ArrayList<Ranked>(candidates.size)
        for (entity in candidates) {
            if (!filter.accepts(entity)) continue
            val spatialDistance = if (near == null) 0.0 else near[entity.id] ?: continue
            val textDistance = if (text == null) 0.0 else text[entity.id] ?: continue
            ranked += Ranked(entity, textDistance, spatialDistance)
        }
        return ranked
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val NANOS_PER_MICRO = 1_000L
        const val TEXT_NEIGHBOURS = 16
        const val TEXT_GROWTH = 4
        const val MAX_TEXT_DISTANCE = 0.92
    }
}
