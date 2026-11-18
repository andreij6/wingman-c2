package com.wingmanc2.core.domain

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.model.MissionCommand
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.ParsedQuery
import com.wingmanc2.core.model.QueryResult
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.ScenarioInject
import com.wingmanc2.core.model.StoreSnapshot
import com.wingmanc2.core.model.TelemetryFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface OntologyRepository {
    fun observeEntities(): Flow<List<OntologyEntity>>
    fun observeEntity(id: Long): Flow<OntologyEntity?>
    fun observeGraph(focusId: Long): Flow<EntityGraph>
    fun observeLineage(entityId: Long): Flow<List<RawObservation>>
    suspend fun findByKey(key: String): OntologyEntity?
    suspend fun query(query: ParsedQuery): QueryResult
}

interface FocusRepository {
    val focusedEntityId: StateFlow<Long?>
    fun focus(entityId: Long?)
}

interface MissionPlannerService {
    suspend fun plan(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        threats: List<OntologyEntity>,
        noFly: List<OntologyEntity>,
    ): List<CourseOfAction>
}

interface MissionRepository {
    val activeMission: StateFlow<MissionPlan?>
    fun observeDecisions(missionId: Long): Flow<List<AutonomyDecision>>
    suspend fun dispatch(intent: CommanderIntent, target: OntologyEntity, asset: OntologyEntity, coa: CourseOfAction): MissionPlan
    suspend fun command(command: MissionCommand)
}

interface TelemetryStreamSource {
    fun telemetry(): Flow<TelemetryFrame>
    fun trail(): Flow<List<TelemetryFrame>>
}

interface LinkStateRepository {
    val linkState: StateFlow<LinkState>
    fun setMode(mode: LinkMode)
}

interface SyncRepository {
    fun observeOutbox(): Flow<List<OutboxEvent>>
    fun observeEdge(): Flow<StoreSnapshot>
    fun observeCloud(): Flow<StoreSnapshot>
    suspend fun resolve(eventId: Long, resolution: ConflictResolution)
}

interface ScenarioController {
    suspend fun inject(inject: ScenarioInject)
}

interface AssetRegistry {
    suspend fun primaryAsset(): OntologyEntity
    suspend fun threats(): List<OntologyEntity>
    suspend fun noFlyZones(): List<OntologyEntity>
    fun affiliationCounts(): Flow<Map<Affiliation, Int>>
}
