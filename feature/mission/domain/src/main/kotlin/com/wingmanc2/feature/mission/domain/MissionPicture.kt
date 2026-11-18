package com.wingmanc2.feature.mission.domain

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.MissionStatus
import com.wingmanc2.core.model.TelemetryFrame

data class MissionTarget(
    val id: Long,
    val key: String,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val position: LocalPoint,
    val threatRadiusM: Double,
)

data class MissionPicture(
    val missionId: Long,
    val coaCode: String,
    val assetId: Long,
    val assetKey: String,
    val status: MissionStatus,
    val startedAtMs: Long,
    val route: List<LocalPoint>,
    val target: MissionTarget?,
    val threats: List<MissionTarget>,
    val decisions: List<AutonomyDecision>,
    val reserveFloorPct: Int,
) {
    val home: LocalPoint? get() = route.firstOrNull()

    fun waypoints(flownCount: Int?): List<LocalPoint> = when {
        flownCount != null && flownCount in 1..route.size -> route.subList(route.size - flownCount, route.size)
        route.size > 1 -> route.subList(1, route.size)
        else -> route
    }
}

data class LiveTelemetry(val frame: TelemetryFrame, val position: LocalPoint)
