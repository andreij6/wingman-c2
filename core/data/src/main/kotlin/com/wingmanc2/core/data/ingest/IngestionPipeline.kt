package com.wingmanc2.core.data.ingest

import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.algorithms.fusion.Association
import com.wingmanc2.core.algorithms.fusion.ConfidenceFusion
import com.wingmanc2.core.algorithms.fusion.FeedNormalizer
import com.wingmanc2.core.algorithms.fusion.NormalizedObservation
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.database.store.EdgeStore
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.ObservationStore
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawFeedMessage
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.RelationType
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.model.SyncState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class IngestionPipeline @Inject constructor(
    private val normalizer: FeedNormalizer,
    private val hasher: FeatureHasher,
    private val index: EntityIndex,
    private val entities: EntityStore,
    private val edges: EdgeStore,
    private val observations: ObservationStore,
) {
    private val mutex = Mutex()
    private val identities = HashMap<String, Long>()
    private var nextSerial = 0L

    suspend fun ingest(message: RawFeedMessage, subjectKey: String? = null): OntologyEntity? = mutex.withLock {
        val observation = normalizer.normalize(message.format, message.payload, message.sensorKey, index.landmarks)
        val association = identify(observation, subjectKey) ?: index.resolve(observation, hasher.embed(observation.descriptor))
        val existing = (association as? Association.Matched)?.let { entities.get(it.entityId) }
        if (existing == null && !observation.located) {
            observations.putAll(listOf(observationOf(message, ORPHAN_ENTITY_ID, lineageNote(observation, UNLOCATED_NOTE))))
            return@withLock null
        }
        val payloadBytes = message.payload.toByteArray().size + ENVELOPE_BYTES
        val written = if (existing == null) {
            val created = newEntity(observation)
            entities.upsertWithOutbox(created.key, payloadBytes, message.receivedAtMs) { current -> current ?: created }
        } else {
            entities.upsertWithOutbox(existing.key, payloadBytes, message.receivedAtMs) { current -> (current ?: existing).corroboratedBy(observation) }
        }
        val stored = checkNotNull(written).entity
        index.register(stored, written.embedding)
        observation.identity?.let { identities[it] = stored.id }
        observations.putAll(listOf(observationOf(message, stored.id, lineageNote(observation, fusionNote(association)))))
        index.idOf(message.sensorKey)?.takeIf { it != stored.id }?.let { sensorId ->
            edges.link(
                OntologyEdge(
                    id = 0,
                    sourceId = sensorId,
                    targetId = stored.id,
                    relation = RelationType.CORROBORATES,
                    weight = observation.confidence,
                    label = RelationType.CORROBORATES.name,
                    provenance = "${message.sensorKey} · ${message.format.name}",
                ),
            )
        }
        stored
    }

    private fun identify(observation: NormalizedObservation, subjectKey: String?): Association.Matched? {
        val id = subjectKey?.let(index::idOf)
            ?: observation.attributes["name"]?.let(index::idOf)
            ?: observation.identity?.let(identities::get)
            ?: return null
        return Association.Matched(id, IDENTITY_SCORE, 0.0)
    }

    private fun observationOf(message: RawFeedMessage, entityId: Long, note: String) =
        RawObservation(0, entityId, message.format, message.payload, note, message.receivedAtMs)

    private fun OntologyEntity.corroboratedBy(observation: NormalizedObservation): OntologyEntity {
        val projection = OperationalArea.projection
        val moved = if (kind in MOVABLE && observation.uncertaintyM <= MAX_POSITION_UNCERTAINTY_M) {
            val gain = POSITION_GAIN * REFERENCE_UNCERTAINTY_M / (REFERENCE_UNCERTAINTY_M + observation.uncertaintyM)
            val current = projection.toLocal(position)
            projection.toGeo(
                current.x + (observation.position.x - current.x) * gain,
                current.y + (observation.position.y - current.y) * gain,
            )
        } else {
            position
        }
        return copy(
            position = moved,
            confidence = ConfidenceFusion.fuse(confidence, observation.confidence, EVIDENCE_RELIABILITY),
            affiliation = if (affiliation == Affiliation.UNKNOWN && observation.affiliation != Affiliation.NEUTRAL) observation.affiliation else affiliation,
        )
    }

    private suspend fun newEntity(observation: NormalizedObservation): OntologyEntity {
        val key = nextKey(prefixOf(observation.kind))
        return OntologyEntity(
            id = 0,
            key = key,
            kind = observation.kind,
            affiliation = observation.affiliation,
            label = key,
            subtitle = subtitleOf(observation),
            position = OperationalArea.projection.toGeo(observation.position),
            confidence = observation.confidence,
            verified = false,
            attributes = attributesOf(observation),
            footprint = emptyList(),
            threatRadiusM = 0.0,
            headingDeg = observation.attributes["heading_deg"]?.toDoubleOrNull(),
            version = 0,
            updatedAtMs = 0,
            syncState = SyncState.PENDING,
        )
    }

    private suspend fun nextKey(prefix: String): String {
        if (nextSerial < NEW_KEY_BASE) nextSerial = NEW_KEY_BASE + entities.count()
        while (true) {
            val key = "$prefix-${nextSerial++}"
            if (entities.findByKey(key) == null) return key
        }
    }

    private fun prefixOf(kind: EntityKind): String = when (kind) {
        EntityKind.TRACK, EntityKind.EMITTER -> "TRK"
        EntityKind.REPORT -> "SPOT"
        else -> "UNK"
    }

    private fun subtitleOf(observation: NormalizedObservation): String {
        val attributes = observation.attributes
        return when (observation.kind) {
            EntityKind.EMITTER -> "RF emitter · ${attributes["band"]?.let { "$it-band" } ?: "unknown band"} · unresolved"
            EntityKind.REPORT -> "Spot report · ${attributes["subject"] ?: "unspecified"}"
            else -> "${observation.format.platformLabel()} track · ${attributes["name"] ?: "uncorrelated"}"
        }
    }

    private fun attributesOf(observation: NormalizedObservation): Map<String, String> {
        val source = observation.attributes
        return buildMap {
            observation.frequencyGhz.takeUnless(Double::isNaN)?.let { put(EntityAttributes.FREQ, String.format(Locale.ROOT, "%.1f GHz", it)) }
            source["az_deg"]?.toDoubleOrNull()?.let { put(EntityAttributes.BEARING, String.format(Locale.ROOT, "%03.0f°", it)) }
            source["band"]?.let { put(EntityAttributes.BAND, "$it-band") }
            source["alt_m"]?.let { put(EntityAttributes.ALT, "$it m") }
            source["speed_mps"]?.let { put(EntityAttributes.SPEED, "$it m/s") }
            source["subject"]?.let { put(EntityAttributes.SUBJECT, it) }
            if (observation.format == SourceFormat.ADSB_JSON) put(EntityAttributes.DOMAIN, EntityAttributes.AIR)
            put(EntityAttributes.SOURCE, observation.format.name)
        }
    }

    private fun fusionNote(association: Association): String = when (association) {
        is Association.Matched -> String.format(Locale.ROOT, "Fused · score %.2f · Δ%.0f m", association.score, association.distanceM)
        is Association.Unmatched -> String.format(Locale.ROOT, "New entity · best match %.2f", association.bestScore)
    }

    private fun lineageNote(observation: NormalizedObservation, fusion: String): String {
        if (observation.missingFields.isEmpty()) return fusion
        return "${observation.missingFields.joinToString(", ")} missing · tolerated · $fusion"
    }

    private fun SourceFormat.platformLabel(): String = when (this) {
        SourceFormat.ADSB_JSON -> "Air"
        SourceFormat.AIS_JSON -> "Surface"
        else -> "Sensor"
    }

    private companion object {
        const val IDENTITY_SCORE = 1f
        const val ORPHAN_ENTITY_ID = 0L
        const val UNLOCATED_NOTE = "Unlocated · held unassociated"
        const val ENVELOPE_BYTES = 96
        const val NEW_KEY_BASE = 100L
        const val EVIDENCE_RELIABILITY = 0.12f
        const val POSITION_GAIN = 0.3
        const val REFERENCE_UNCERTAINTY_M = 50.0
        const val MAX_POSITION_UNCERTAINTY_M = 400.0
        val MOVABLE = setOf(EntityKind.TRACK, EntityKind.EMITTER, EntityKind.REPORT)
    }
}
