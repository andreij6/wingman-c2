package com.wingmanc2.core.data.mission

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.geo.bearingDeg
import com.wingmanc2.core.algorithms.geo.compassIndex
import com.wingmanc2.core.common.formatClock
import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.DecisionKind
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.IntentConstraint
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.MissionStatus
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.TelemetryFrame
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class DecisionDraft(val kind: DecisionKind, val title: String, val rationale: String, val provenance: String)

internal object DecisionCopy {
    private const val PROVENANCE_OPERATOR = "Operator · human-on-the-loop"
    private val COMPASS = arrayOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")

    fun approval(intent: CommanderIntent, coa: CourseOfAction) = DecisionDraft(
        kind = DecisionKind.APPROVAL,
        title = "${coa.code} approved by operator",
        rationale = approvalRationale(intent),
        provenance = "Operator · ${coa.title}",
    )

    fun hold(frame: TelemetryFrame?) = DecisionDraft(
        kind = DecisionKind.HOLD,
        title = "Holding · orbit at current position",
        rationale = "Operator override · ${batteryText(frame)} remaining",
        provenance = PROVENANCE_OPERATOR,
    )

    fun resume(plan: MissionPlan, frame: TelemetryFrame?) = DecisionDraft(
        kind = DecisionKind.RESUME,
        title = "Operator resumed ${plan.coa.code}",
        rationale = "Route reacquired · ${waypointText(frame)}",
        provenance = PROVENANCE_OPERATOR,
    )

    fun replan(coa: CourseOfAction) = DecisionDraft(
        kind = DecisionKind.REPLAN,
        title = "Re-planned ${coa.code} from current position",
        rationale = "${coa.minStandoffM.roundToInt()} m standoff · ${coa.projectedReservePct}% reserve · ETA ${formatClock(coa.etaSeconds)}",
        provenance = "Operator · CoaPlanner A*",
    )

    fun returnToBase(frame: TelemetryFrame?) = DecisionDraft(
        kind = DecisionKind.RTB,
        title = "Return to base",
        rationale = "Operator override · ${batteryText(frame)} remaining",
        provenance = PROVENANCE_OPERATOR,
    )

    fun reroute(previous: CourseOfAction, replanned: CourseOfAction, threatKey: String, bearingDeg: Double) = DecisionDraft(
        kind = DecisionKind.REROUTE,
        title = rerouteTitle(previous.waypoints, replanned.waypoints),
        rationale = rerouteRationale(bearingDeg, replanned.profile, replanned.minStandoffM),
        provenance = "UAS physics · $threatKey detected · CoaPlanner A*",
    )

    fun threatWhileCommitted(status: MissionStatus, threatKey: String, distanceM: Double, bearingDeg: Double) = DecisionDraft(
        kind = DecisionKind.THREAT,
        title = "$threatKey detected · ${if (status == MissionStatus.RTB) "RTB maintained" else "holding orbit"}",
        rationale = String.format(Locale.ROOT, "%d m from UAS at az %03d° · no auto-replan", distanceM.roundToInt(), bearingDeg.roundToInt().mod(360)),
        provenance = "UAS physics · threat monitor",
    )

    fun capture(frameNumber: Int, mode: LinkMode, rangeToTargetM: Double) = DecisionDraft(
        kind = DecisionKind.CAPTURE,
        title = "Captured EO frame #$frameNumber",
        rationale = captureRationale(mode),
        provenance = "EO/IR gimbal · range ${rangeToTargetM.roundToInt()} m",
    )

    fun batteryFloor(batteryPct: Double) = DecisionDraft(
        kind = DecisionKind.RTB,
        title = "Auto-RTB · battery ${batteryPct.roundToInt()}%",
        rationale = "Reserve floor reached · returning to base",
        provenance = "UAS physics · battery monitor",
    )

    fun recovered(frame: TelemetryFrame?) = DecisionDraft(
        kind = DecisionKind.RTB,
        title = "Recovered at home",
        rationale = "Mission complete · ${batteryText(frame)} remaining",
        provenance = "UAS physics · home reached",
    )

    fun popupThreat(threat: OntologyEntity, from: GeoPoint, sensorKey: String) = DecisionDraft(
        kind = DecisionKind.THREAT,
        title = "Pop-up emitter ${threat.key} · ${threat.attributes[EntityAttributes.BAND] ?: "RF"}",
        rationale = String.format(
            Locale.ROOT,
            "%d m threat ring · %d m from UAS at az %03d°",
            threat.threatRadiusM.roundToInt(),
            OperationalArea.projection.distanceM(from, threat.position).roundToInt(),
            OperationalArea.projection.bearingDeg(from, threat.position).roundToInt().mod(360),
        ),
        provenance = "$sensorKey · scenario inject",
    )

    private fun approvalRationale(intent: CommanderIntent): String {
        val locks = intent.constraints.mapNotNull { constraint ->
            when (constraint) {
                is IntentConstraint.StandoffMin -> "≥${constraint.meters.roundToInt()} m standoff"
                is IntentConstraint.ReserveMin -> "≥${constraint.percent}% reserve"
                is IntentConstraint.MaxAltitude -> "≤${constraint.metersAgl.roundToInt()} m AGL"
                IntentConstraint.AvoidRadar, IntentConstraint.ZeroRf -> null
            }
        }
        return if (locks.isEmpty()) "Locked: planner defaults" else "Locked: ${locks.joinToString(" · ")}"
    }

    private fun rerouteTitle(previous: List<GeoPoint>, replanned: List<GeoPoint>): String {
        val shift = largestDeviation(previous, replanned) ?: return "Re-routed around threat"
        return "Re-routed +${shift.first.roundToInt()} m ${shift.second}"
    }

    private fun rerouteRationale(bearingDeg: Double, profile: CoaProfile, standoffM: Double): String {
        val manoeuvre = when (profile) {
            CoaProfile.STEALTH -> "ridge masks"
            CoaProfile.DIRECT -> "climb holds"
            CoaProfile.ENDURANCE -> "orbit holds"
        }
        return String.format(Locale.ROOT, "RF spike az %03d° → %s %d m standoff", bearingDeg.roundToInt().mod(360), manoeuvre, standoffM.roundToInt())
    }

    private fun captureRationale(mode: LinkMode): String = when (mode) {
        LinkMode.ONLINE -> "Stored on device · replicating to cloud mirror"
        LinkMode.DEGRADED -> "Stored on device · queued for sync (link degraded)"
        LinkMode.DENIED -> "Stored on device · queued for sync (link denied)"
    }

    private fun batteryText(frame: TelemetryFrame?): String = frame?.let { "battery ${it.batteryPct.roundToInt()}%" } ?: "battery nominal"

    private fun waypointText(frame: TelemetryFrame?): String =
        frame?.let { "waypoint ${(it.waypointIndex + 1).coerceAtMost(it.waypointCount)} of ${it.waypointCount}" } ?: "route resumed"

    private fun largestDeviation(previous: List<GeoPoint>, replanned: List<GeoPoint>): Pair<Double, String>? {
        if (previous.size < 2 || replanned.size < 2) return null
        val projection = OperationalArea.projection
        val path = previous.map(projection::toLocal)
        val xs = DoubleArray(path.size) { path[it].x }
        val ys = DoubleArray(path.size) { path[it].y }
        var best = 0.0
        var bestDx = 0.0
        var bestDy = 0.0
        for (i in 1 until replanned.size) {
            val point = projection.toLocal(replanned[i])
            val px = point.x
            val py = point.y
            var nearestSq = Double.POSITIVE_INFINITY
            var nx = px
            var ny = py
            for (s in 0 until xs.size - 1) {
                val vx = xs[s + 1] - xs[s]
                val vy = ys[s + 1] - ys[s]
                val len2 = vx * vx + vy * vy
                val t = if (len2 == 0.0) 0.0 else (((px - xs[s]) * vx + (py - ys[s]) * vy) / len2).coerceIn(0.0, 1.0)
                val cx = xs[s] + t * vx
                val cy = ys[s] + t * vy
                val dSq = (px - cx) * (px - cx) + (py - cy) * (py - cy)
                if (dSq < nearestSq) {
                    nearestSq = dSq
                    nx = cx
                    ny = cy
                }
            }
            val d = sqrt(nearestSq)
            if (d > best) {
                best = d
                bestDx = px - nx
                bestDy = py - ny
            }
        }
        if (best < 1.0) return null
        return best to COMPASS[compassIndex(bearingDeg(bestDx, bestDy))]
    }
}
