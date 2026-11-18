package com.wingmanc2.core.data.scenario

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.data.ingest.IngestionPipeline
import com.wingmanc2.core.data.mission.MissionJournal
import com.wingmanc2.core.data.runtime.ScenarioBootstrap
import com.wingmanc2.core.data.runtime.toEntity
import com.wingmanc2.core.database.store.EdgeStore
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.ObservationStore
import com.wingmanc2.core.database.store.OutboxStore
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.domain.ScenarioController
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.RelationType
import com.wingmanc2.core.model.ScenarioInject
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.model.SyncState
import com.wingmanc2.core.simulation.cloud.SimulatedCloudMirror
import com.wingmanc2.core.simulation.feed.MessyFeedSimulator
import com.wingmanc2.core.simulation.link.LinkSimulator
import com.wingmanc2.core.simulation.scenario.ScenarioKeys
import com.wingmanc2.core.simulation.scenario.ScenarioSeed
import com.wingmanc2.core.simulation.uas.PopupThreat
import com.wingmanc2.core.simulation.uas.UasPhysicsEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

internal class SimulatedScenarioController @Inject constructor(
    private val bootstrap: ScenarioBootstrap,
    private val ingestion: IngestionPipeline,
    private val feed: MessyFeedSimulator,
    private val mirror: SimulatedCloudMirror,
    private val link: LinkSimulator,
    private val engine: UasPhysicsEngine,
    private val missions: MissionRepository,
    private val journal: MissionJournal,
    private val entities: EntityStore,
    private val edges: EdgeStore,
    private val observations: ObservationStore,
    private val outbox: OutboxStore,
    private val index: EntityIndex,
    private val clock: Clock,
) : ScenarioController {

    override suspend fun inject(inject: ScenarioInject) {
        bootstrap.awaitReady()
        withContext(NonCancellable) {
            when (inject) {
                ScenarioInject.POPUP_RF_THREAT -> {
                    val threat = spawnPopupThreat()
                    missions.activeMission.value?.let { journal.reportThreat(it, threat, ScenarioKeys.RF_RX2) }
                }
                ScenarioInject.CONFLICTING_REPORT -> {
                    val remoteVersion = mirror.scriptConflict(ScenarioKeys.TRK_07)
                    ingestion.ingest(feed.conflictingReport(), subjectKey = ScenarioKeys.TRK_07)
                    outbox.markOpenConflict(ScenarioKeys.TRK_07, remoteVersion)
                }
                ScenarioInject.SENSOR_BURST -> feed.burst().forEach { ingestion.ingest(it) }
                ScenarioInject.RESTORE_LINK -> link.setMode(LinkMode.ONLINE)
            }
        }
    }

    private suspend fun spawnPopupThreat(): OntologyEntity {
        val seed = ScenarioSeed.popupThreat
        val now = clock.nowMs()
        val stored = checkNotNull(
            entities.upsertWithOutbox(seed.key, POPUP_PAYLOAD_BYTES, now) { existing ->
                existing ?: seed.toEntity(id = 0, updatedAtMs = now, syncState = SyncState.PENDING)
            },
        )
        val threat = stored.entity
        index.register(threat, stored.embedding)
        engine.injectThreat(PopupThreat(threat.key, threat.position, threat.threatRadiusM))
        val sensor = ScenarioSeed.entity(ScenarioKeys.RF_RX2).position
        observations.putAll(listOf(RawObservation(0, threat.id, SourceFormat.RF_PING, pingPayload(sensor, threat), POPUP_NOTE, now)))
        index.idOf(ScenarioKeys.RF_RX2)?.let { sensorId ->
            edges.link(OntologyEdge(0, threat.id, sensorId, RelationType.DETECTED_IN, DETECTION_WEIGHT, RelationType.DETECTED_IN.name, POPUP_NOTE))
        }
        return threat
    }

    private fun pingPayload(sensor: GeoPoint, threat: OntologyEntity): String = String.format(
        Locale.ROOT,
        """{"src":"rf_rx2","band":"%s","az":%d,"range_m":%d}""",
        threat.attributes[EntityAttributes.FREQ]?.substringBefore(' ')?.let { "${it}G" } ?: "S",
        OperationalArea.projection.bearingDeg(sensor, threat.position).roundToInt().mod(360),
        OperationalArea.projection.distanceM(sensor, threat.position).roundToInt(),
    )

    private companion object {
        const val POPUP_PAYLOAD_BYTES = 640
        const val DETECTION_WEIGHT = 0.62f
        const val POPUP_NOTE = "RF-RX2 bearing + range fix · pop-up"
    }
}
