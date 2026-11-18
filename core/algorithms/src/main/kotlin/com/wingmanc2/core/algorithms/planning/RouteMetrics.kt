package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.Terrain
import com.wingmanc2.core.model.LocalPoint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

data class RouteMetrics(
    val lengthM: Double,
    val etaSeconds: Double,
    val landingBatteryPct: Double,
    val minStandoffM: Double,
    val exposureSeconds: Double,
    val maskedSeconds: Double,
    val maxAltitudeAglM: Double,
) {
    companion object {
        private const val SAMPLE_STEP_M = 10.0

        fun evaluate(route: Route, target: LocalPoint, radars: List<RadarHorizon>, home: LocalPoint): RouteMetrics {
            val flight = FlightIntegrator(radars, route.start)
            var length = 0.0
            var minStandoff = Double.POSITIVE_INFINITY
            var maxAltitude = route.start.altitudeAglM
            val points = route.waypoints
            for (leg in route.legs.indices) {
                val a = points[leg]
                val b = points[leg + 1]
                length += flight.fly(a, b, route.legs[leg], observe = true)
                minStandoff = minOf(minStandoff, distanceToSegment(target, a, b))
                maxAltitude = max(maxAltitude, b.altitudeAglM)
            }
            val eta = flight.seconds
            flight.fly(route.end, Waypoint(home.x, home.y, route.end.altitudeAglM), FlightRegime.CRUISE, observe = false)
            return RouteMetrics(
                lengthM = length,
                etaSeconds = eta,
                landingBatteryPct = FlightModel.START_BATTERY_PCT - flight.joules / (FlightModel.BATTERY_WH * 3_600.0) * 100.0,
                minStandoffM = if (minStandoff.isInfinite()) distance(target, route.start) else minStandoff,
                exposureSeconds = flight.exposureSeconds,
                maskedSeconds = flight.maskedSeconds,
                maxAltitudeAglM = maxAltitude,
            )
        }

        private fun distance(p: LocalPoint, w: Waypoint): Double {
            val dx = p.x - w.x
            val dy = p.y - w.y
            return sqrt(dx * dx + dy * dy)
        }

        private fun distanceToSegment(p: LocalPoint, a: Waypoint, b: Waypoint): Double {
            val vx = b.x - a.x
            val vy = b.y - a.y
            val len2 = vx * vx + vy * vy
            val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0.0, 1.0)
            val dx = p.x - (a.x + t * vx)
            val dy = p.y - (a.y + t * vy)
            return sqrt(dx * dx + dy * dy)
        }
    }

    private class FlightIntegrator(private val radars: List<RadarHorizon>, start: Waypoint) {
        var seconds = 0.0
        var joules = 0.0
        var exposureSeconds = 0.0
        var maskedSeconds = 0.0
        private var previousMslM = Terrain.elevation(start.x, start.y) + start.altitudeAglM

        fun fly(a: Waypoint, b: Waypoint, regime: FlightRegime, observe: Boolean): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = sqrt(dx * dx + dy * dy)
            val samples = max(1, ceil(length / SAMPLE_STEP_M).toInt())
            val ds = length / samples
            for (s in 1..samples) {
                val t = s.toDouble() / samples
                val x = a.x + t * dx
                val y = a.y + t * dy
                val msl = Terrain.elevation(x, y) + a.altitudeAglM + t * (b.altitudeAglM - a.altitudeAglM)
                val climbM = max(0.0, msl - previousMslM)
                previousMslM = msl
                val dt = max(ds / regime.speedMps, climbM / FlightModel.CLIMB_MPS)
                seconds += dt
                joules += regime.watts * dt + FlightModel.CLIMB_W_PER_MPS * climbM
                if (observe) classify(x, y, msl, dt)
            }
            return length
        }

        private fun classify(x: Double, y: Double, msl: Double, dt: Double) {
            var masked = false
            for (i in radars.indices) {
                val radar = radars[i]
                if (!radar.covers(x, y)) continue
                if (radar.exposure(x, y, msl) > 0f) {
                    exposureSeconds += dt
                    return
                }
                masked = true
            }
            if (masked) maskedSeconds += dt
        }
    }
}
