package com.wingmanc2.feature.mission.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.planning.reserveFloorPct
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.OntologyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

class ObserveMissionUseCase @Inject constructor(
    private val missions: MissionRepository,
    private val ontology: OntologyRepository,
) {
    operator fun invoke(): Flow<MissionPicture?> = missions.activeMission.flatMapLatest { plan ->
        if (plan == null) {
            flowOf(null)
        } else {
            combine(
                missions.observeDecisions(plan.id),
                ontology.observeEntity(plan.targetId).map { it?.toMissionTarget() }.distinctUntilChanged(),
                ontology.observeEntity(plan.assetId).map { it?.key.orEmpty() }.distinctUntilChanged(),
                popUpThreats(plan.targetId),
            ) { decisions, target, assetKey, threats -> picture(plan, decisions, target, assetKey, threats) }
        }
    }

    private fun popUpThreats(targetId: Long): Flow<List<MissionTarget>> = ontology.observeEntities()
        .map { entities ->
            entities.mapNotNull { entity ->
                entity.takeIf { it.affiliation == Affiliation.HOSTILE && it.threatRadiusM > 0.0 && it.id != targetId }?.toMissionTarget()
            }
        }
        .distinctUntilChanged()

    private fun OntologyEntity.toMissionTarget() =
        MissionTarget(id, key, kind, affiliation, OperationalArea.projection.toLocal(position), threatRadiusM)

    private fun picture(
        plan: MissionPlan,
        decisions: List<AutonomyDecision>,
        target: MissionTarget?,
        assetKey: String,
        threats: List<MissionTarget>,
    ) = MissionPicture(
        missionId = plan.id,
        coaCode = plan.coa.code,
        assetId = plan.assetId,
        assetKey = assetKey,
        status = plan.status,
        startedAtMs = plan.startedAtMs,
        route = plan.coa.waypoints.map(OperationalArea.projection::toLocal),
        target = target,
        threats = threats,
        decisions = decisions.sortedByDescending(AutonomyDecision::tPlusMs),
        reserveFloorPct = plan.intent.reserveFloorPct().roundToInt(),
    )
}
