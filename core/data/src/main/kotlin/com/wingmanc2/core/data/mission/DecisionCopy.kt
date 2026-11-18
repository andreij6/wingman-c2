package com.wingmanc2.core.data.mission

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.IntentConstraint
import com.wingmanc2.core.model.LinkMode
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object DecisionCopy {
    private val COMPASS = arrayOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")

    fun approvalRationale(intent: CommanderIntent): String {
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

    fun rerouteTitle(previous: List<GeoPoint>, replanned: List<GeoPoint>): String {
        val shift = largestDeviation(previous, replanned) ?: return "Re-routed around threat"
        return "Re-routed +${shift.first.roundToInt()} m ${shift.second}"
    }

    fun rerouteRationale(bearingDeg: Double, profile: CoaProfile, standoffM: Double): String {
        val manoeuvre = when (profile) {
            CoaProfile.STEALTH -> "ridge masks"
            CoaProfile.DIRECT -> "climb holds"
            CoaProfile.ENDURANCE -> "orbit holds"
        }
        return String.format(Locale.ROOT, "RF spike az %03d° → %s %d m standoff", bearingDeg.roundToInt().mod(360), manoeuvre, standoffM.roundToInt())
    }

    fun captureRationale(mode: LinkMode): String = when (mode) {
        LinkMode.ONLINE -> "Stored on device · replicating to cloud mirror"
        LinkMode.DEGRADED -> "Stored on device · queued for sync (link degraded)"
        LinkMode.DENIED -> "Stored on device · queued for sync (link denied)"
    }

    fun threatRationale(radiusM: Double, distanceM: Double, bearingDeg: Double): String = String.format(
        Locale.ROOT,
        "%d m threat ring · %d m from UAS at az %03d°",
        radiusM.roundToInt(),
        distanceM.roundToInt(),
        bearingDeg.roundToInt().mod(360),
    )

    fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
        val projection = OperationalArea.projection
        val deg = Math.toDegrees(atan2(projection.xOf(to) - projection.xOf(from), projection.yOf(to) - projection.yOf(from)))
        return if (deg < 0) deg + 360.0 else deg
    }

    fun distanceM(from: GeoPoint, to: GeoPoint): Double {
        val projection = OperationalArea.projection
        return hypot(projection.xOf(to) - projection.xOf(from), projection.yOf(to) - projection.yOf(from))
    }

    private fun largestDeviation(previous: List<GeoPoint>, replanned: List<GeoPoint>): Pair<Double, String>? {
        if (previous.size < 2 || replanned.size < 2) return null
        val projection = OperationalArea.projection
        val xs = DoubleArray(previous.size) { projection.xOf(previous[it]) }
        val ys = DoubleArray(previous.size) { projection.yOf(previous[it]) }
        var best = 0.0
        var bestDx = 0.0
        var bestDy = 0.0
        for (i in 1 until replanned.size) {
            val px = projection.xOf(replanned[i])
            val py = projection.yOf(replanned[i])
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
        val bearing = Math.toDegrees(atan2(bestDx, bestDy)).let { if (it < 0) it + 360.0 else it }
        return best to COMPASS[((bearing + 22.5) / 45.0).toInt() % COMPASS.size]
    }
}
