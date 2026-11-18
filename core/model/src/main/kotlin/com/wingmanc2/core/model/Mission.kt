package com.wingmanc2.core.model

enum class MissionStatus { DRAFT, EN_ROUTE, LOITER, REPLANNING, RTB, COMPLETE }

data class MissionPlan(
    val id: Long,
    val intent: CommanderIntent,
    val targetId: Long,
    val assetId: Long,
    val coa: CourseOfAction,
    val status: MissionStatus,
    val startedAtMs: Long,
)

data class TelemetryFrame(
    val tMs: Long,
    val position: GeoPoint,
    val headingDeg: Double,
    val altitudeAglM: Double,
    val airspeedMps: Double,
    val climbMps: Double,
    val batteryPct: Double,
    val linkSnrDb: Double,
    val waypointIndex: Int,
    val waypointCount: Int,
)

enum class DecisionKind { APPROVAL, REROUTE, CAPTURE, HOLD, RESUME, REPLAN, RTB, THREAT }

data class AutonomyDecision(
    val id: Long,
    val missionId: Long,
    val tPlusMs: Long,
    val kind: DecisionKind,
    val title: String,
    val rationale: String,
    val provenance: String,
)

enum class MissionCommand { HOLD, RESUME, REPLAN, RTB }
