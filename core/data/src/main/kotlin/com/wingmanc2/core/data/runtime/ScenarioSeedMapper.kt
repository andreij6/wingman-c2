package com.wingmanc2.core.data.runtime

import com.wingmanc2.core.database.SeedData
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.SyncState
import com.wingmanc2.core.simulation.scenario.ScenarioSeed
import com.wingmanc2.core.simulation.scenario.SeedEntity

internal object ScenarioSeedMapper {
    private const val MS_PER_MINUTE = 60_000L

    fun seedData(nowMs: Long): SeedData {
        val idByKey = HashMap<String, Long>(ScenarioSeed.entities.size * 2)
        ScenarioSeed.entities.forEachIndexed { index, seed -> idByKey[seed.key] = index + 1L }
        val freshestMinutes = HashMap<String, Int>()
        for (observation in ScenarioSeed.observations) {
            freshestMinutes.merge(observation.entityKey, observation.minutesAgo, ::minOf)
        }
        val entities = ScenarioSeed.entities.map { seed ->
            seed.toEntity(
                id = idByKey.getValue(seed.key),
                updatedAtMs = nowMs - (freshestMinutes[seed.key] ?: 0) * MS_PER_MINUTE,
                syncState = SyncState.SYNCED,
            )
        }
        val edges = ScenarioSeed.edges.map { edge ->
            OntologyEdge(
                id = 0,
                sourceId = idByKey.getValue(edge.sourceKey),
                targetId = idByKey.getValue(edge.targetKey),
                relation = edge.relation,
                weight = edge.weight,
                label = edge.label,
                provenance = edge.provenance,
            )
        }
        val observations = ScenarioSeed.observations.map { observation ->
            RawObservation(
                id = 0,
                entityId = idByKey.getValue(observation.entityKey),
                format = observation.format,
                payload = observation.payload,
                note = observation.note,
                ingestedAtMs = nowMs - observation.minutesAgo * MS_PER_MINUTE,
            )
        }
        return SeedData(entities, edges, observations)
    }
}

internal fun SeedEntity.toEntity(id: Long, updatedAtMs: Long, syncState: SyncState): OntologyEntity = OntologyEntity(
    id = id,
    key = key,
    kind = kind,
    affiliation = affiliation,
    label = label,
    subtitle = subtitle,
    position = position,
    confidence = confidence,
    verified = verified,
    attributes = attributes,
    footprint = footprint,
    threatRadiusM = threatRadiusM,
    headingDeg = headingDeg,
    version = version,
    updatedAtMs = updatedAtMs,
    syncState = syncState,
)
