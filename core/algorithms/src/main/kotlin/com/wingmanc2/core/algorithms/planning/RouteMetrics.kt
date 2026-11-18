package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.Terrain
import com.wingmanc2.core.algorithms.geo.distanceToSegment
import com.wingmanc2.core.model.LocalPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

internal class RouteMetrics(
    val lengthM: Double,
    val etaSeconds: Double,
    val landingBatteryPct: Double,
    val minStandoffM: Double,
    val exposureSeconds: Double,
    val exposureByRadar: DoubleArray,
    val maskedSeconds: Double,
    val maxAltitudeAglM: Double,
) {
    val dominantRadar: Int get() = exposureByRadar.indices.maxByOrNull { exposureByRadar[it] }?.takeIf { exposureByRadar[it] > 0.0 } ?: -1

    companion object {
        private const val SAMPLE_STEP_M = 10.0

        fun evaluate(
            route: Route,
            target: LocalPoint,
            radars: List<RadarHorizon>,
            home: LocalPoint,
            startBatteryPct: Double,
        ): RouteMetrics {
            val flight = FlightIntegrator(radars, route.start)
            var length = 0.0
            var eta = Double.NaN
            var minStandoff = Double.POSITIVE_INFINITY
            var maxAltitude = route.start.altitudeAglM
            val points = route.waypoints
            for (leg in route.legs.indices) {
                val a = points[leg]
                val b = points[leg + 1]
                if (eta.isNaN() && route.legs[leg] == FlightRegime.LOITER) eta = flight.seconds
                length += flight.fly(a, b, route.legs[leg], observe = true)
                minStandoff = minOf(minStandoff, distanceToSegment(target.x, target.y, a.x, a.y, b.x, b.y))
                maxAltitude = max(maxAltitude, b.altitudeAglM)
            }
            if (eta.isNaN()) eta = flight.seconds
            flight.fly(route.end, Waypoint(home.x, home.y, route.end.altitudeAglM), FlightRegime.CRUISE, observe = false)
            return RouteMetrics(
                lengthM = length,
                etaSeconds = eta,
                landingBatteryPct = startBatteryPct - flight.joules / (FlightModel.BATTERY_WH * 3_600.0) * 100.0,
                minStandoffM = if (minStandoff.isInfinite()) hypot(target.x - route.start.x, target.y - route.start.y) else minStandoff,
                exposureSeconds = flight.exposureSeconds,
                exposureByRadar = flight.exposureByRadar,
                maskedSeconds = flight.maskedSeconds,
                maxAltitudeAglM = maxAltitude,
            )
        }
    }

    private class FlightIntegrator(private val radars: List<RadarHorizon>, start: Waypoint) {
        var seconds = 0.0
        var joules = 0.0
        var exposureSeconds = 0.0
        val exposureByRadar = DoubleArray(radars.size)
        var maskedSeconds = 0.0
        private var previousMslM = Terrain.elevation(start.x, start.y) + start.altitudeAglM

        fun fly(a: Waypoint, b: Waypoint, regime: FlightRegime, observe: Boolean): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = hypot(dx, dy)
            val samples = max(1, ceil(max(length, abs(b.altitudeAglM - a.altitudeAglM)) / SAMPLE_STEP_M).toInt())
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
            var exposed = false
            for (i in radars.indices) {
                val radar = radars[i]
                if (!radar.covers(x, y)) continue
                if (radar.exposure(x, y, msl) > 0f) {
                    exposureByRadar[i] += dt
                    exposed = true
                } else {
                    masked = true
                }
            }
            when {
                exposed -> exposureSeconds += dt
                masked -> maskedSeconds += dt
            }
        }
    }
}
