package com.wingmanc2.feature.mission.domain

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.IntentConstraint
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.OntologyEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ObserveMissionUseCase @Inject constructor(
    private val missions: MissionRepository,
    private val ontology: OntologyRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<MissionPicture?> = missions.activeMission.flatMapLatest { plan ->
        if (plan == null) {
            flowOf(null)
        } else {
            combine(
                missions.observeDecisions(plan.id),
                ontology.observeEntity(plan.targetId),
                ontology.observeEntity(plan.assetId),
            ) { decisions, target, asset -> picture(plan, decisions, target, asset) }
        }
    }

    private fun picture(plan: MissionPlan, decisions: List<AutonomyDecision>, target: OntologyEntity?, asset: OntologyEntity?) =
        MissionPicture(
            missionId = plan.id,
            coaCode = plan.coa.code,
            assetId = plan.assetId,
            assetKey = asset?.key.orEmpty(),
            status = plan.status,
            startedAtMs = plan.startedAtMs,
            route = plan.coa.waypoints.map(OperationalArea.projection::toLocal),
            target = target?.let {
                MissionTarget(it.id, it.key, it.kind, it.affiliation, OperationalArea.projection.toLocal(it.position), it.threatRadiusM)
            },
            decisions = decisions.sortedByDescending(AutonomyDecision::tPlusMs),
            reserveFloorPct = plan.intent.constraints.firstNotNullOfOrNull { (it as? IntentConstraint.ReserveMin)?.percent }
                ?: FlightModel.RTB_FLOOR_PCT.toInt(),
        )
}
