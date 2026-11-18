package com.wingmanc2.core.data.ingest

import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.algorithms.fusion.Association
import com.wingmanc2.core.algorithms.fusion.ConfidenceFusion
import com.wingmanc2.core.algorithms.fusion.FeedNormalizer
import com.wingmanc2.core.algorithms.fusion.NormalizedObservation
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.database.store.EdgeStore
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.ObservationStore
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.OutboxOp
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.RelationType
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.model.SyncState
import com.wingmanc2.core.simulation.feed.MessyFeedSimulator
import com.wingmanc2.core.simulation.feed.RawFeedMessage
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.ATTR_BAND
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.ATTR_BEARING
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.ATTR_DOMAIN
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.ATTR_FREQ
import com.wingmanc2.core.simulation.scenario.ScenarioKeys.DOMAIN_AIR
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class IngestionPipeline @Inject constructor(
    private val feed: MessyFeedSimulator,
    private val normalizer: FeedNormalizer,
    private val hasher: FeatureHasher,
    private val index: EntityIndex,
    private val entities: EntityStore,
    private val edges: EdgeStore,
    private val observations: ObservationStore,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    fun start() {
        scope.launch(default) { feed.feed().collect { ingest(it) } }
    }

    suspend fun ingest(message: RawFeedMessage, subjectKey: String? = null): OntologyEntity = mutex.withLock {
        val observation = normalizer.normalize(message.format, message.payload, index.landmarks)
        val association = subjectKey?.let(index::idOf)?.let { Association.Matched(it, SUBJECT_SCORE, 0.0) }
            ?: index.resolve(observation, hasher.embed(observation.descriptor))
        val existing = (association as? Association.Matched)?.let { entities.get(it.entityId) }
        val stored = entities.upsertWithOutbox(
            entity = existing?.corroboratedBy(observation) ?: newEntity(observation),
            op = if (existing == null) OutboxOp.INSERT else OutboxOp.UPSERT,
            payloadBytes = message.payload.toByteArray().size + ENVELOPE_BYTES,
            nowMs = message.receivedAtMs,
        )
        index.register(stored)
        observations.putAll(
            listOf(RawObservation(0, stored.id, message.format, message.payload, lineageNote(observation, association), message.receivedAtMs)),
        )
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
            affiliation = if (affiliation == Affiliation.UNKNOWN) observation.affiliation else affiliation,
        )
    }

    private suspend fun newEntity(observation: NormalizedObservation): OntologyEntity {
        val key = "${prefixOf(observation.kind)}-${NEW_KEY_BASE + entities.count()}"
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
            source["freq_ghz"]?.toDoubleOrNull()?.let { put(ATTR_FREQ, String.format(Locale.ROOT, "%.1f GHz", it)) }
            source["az_deg"]?.toDoubleOrNull()?.let { put(ATTR_BEARING, String.format(Locale.ROOT, "%03.0f°", it)) }
            source["band"]?.let { put(ATTR_BAND, "$it-band") }
            source["alt_m"]?.let { put("Alt", "$it m") }
            source["speed_mps"]?.let { put("Speed", "$it m/s") }
            source["subject"]?.let { put("Subject", it) }
            if (observation.format == SourceFormat.ADSB_JSON) put(ATTR_DOMAIN, DOMAIN_AIR)
            put("Source", observation.format.name)
        }
    }

    private fun lineageNote(observation: NormalizedObservation, association: Association): String {
        val fusion = when (association) {
            is Association.Matched -> String.format(Locale.ROOT, "Fused · score %.2f · Δ%.0f m", association.score, association.distanceM)
            is Association.Unmatched -> String.format(Locale.ROOT, "New entity · best match %.2f", association.bestScore)
        }
        if (observation.missingFields.isEmpty()) return fusion
        return "${observation.missingFields.joinToString(", ")} missing · tolerated · $fusion"
    }

    private fun SourceFormat.platformLabel(): String = when (this) {
        SourceFormat.ADSB_JSON -> "Air"
        SourceFormat.AIS_JSON -> "Surface"
        else -> "Sensor"
    }

    private companion object {
        const val SUBJECT_SCORE = 1f
        const val ENVELOPE_BYTES = 96
        const val NEW_KEY_BASE = 100
        const val EVIDENCE_RELIABILITY = 0.12f
        const val POSITION_GAIN = 0.3
        const val REFERENCE_UNCERTAINTY_M = 50.0
        const val MAX_POSITION_UNCERTAINTY_M = 400.0
        val MOVABLE = setOf(EntityKind.TRACK, EntityKind.EMITTER, EntityKind.REPORT)
    }
}
