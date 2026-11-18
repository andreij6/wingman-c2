package com.wingmanc2.core.domain

import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.FlownTrack
import com.wingmanc2.core.model.MissionCommand
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.TelemetryFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MissionPlannerService {
    suspend fun plan(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        threats: List<OntologyEntity>,
        noFly: List<OntologyEntity>,
        startBatteryPct: Double? = null,
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
    fun flownTrack(): Flow<FlownTrack>
}
