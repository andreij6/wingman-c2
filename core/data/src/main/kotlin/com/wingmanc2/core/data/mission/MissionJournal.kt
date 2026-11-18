package com.wingmanc2.core.data.mission

import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.database.store.DecisionStore
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ScenarioProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MissionJournal @Inject constructor(
    private val decisions: DecisionStore,
    private val telemetry: TelemetryHub,
    private val profile: ScenarioProfile,
    private val clock: Clock,
) {
    fun observe(missionId: Long): Flow<List<AutonomyDecision>> = decisions.observe(missionId)

    suspend fun log(plan: MissionPlan, atMs: Long, draft: DecisionDraft) {
        decisions.append(
            AutonomyDecision(
                id = 0,
                missionId = plan.id,
                tPlusMs = (atMs - plan.startedAtMs).coerceAtLeast(0L),
                kind = draft.kind,
                title = draft.title,
                rationale = draft.rationale,
                provenance = draft.provenance,
            ),
        )
    }

    suspend fun reportThreat(plan: MissionPlan, threat: OntologyEntity, sensorKey: String) {
        val frame = telemetry.latest
        log(plan, frame?.tMs ?: clock.nowMs(), DecisionCopy.popupThreat(threat, frame?.position ?: profile.home, sensorKey))
    }
}
