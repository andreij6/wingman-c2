package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.Terrain
import com.wingmanc2.core.algorithms.geo.bearingDeg
import com.wingmanc2.core.algorithms.geo.normalizeDeg
import com.wingmanc2.core.algorithms.geo.signedDeltaDeg
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class Airframe(
    x: Double,
    y: Double,
    headingDeg: Double,
    mslM: Double,
    batteryPct: Double,
) {
    var x = x
        private set
    var y = y
        private set
    var headingDeg = headingDeg
        private set
    var mslM = mslM
        private set
    var batteryPct = batteryPct
        private set
    var airspeedMps = FlightModel.CRUISE_MPS
        private set
    var climbMps = 0.0
        private set

    val aglM: Double get() = mslM - Terrain.elevation(x, y)

    fun step(courseDeg: Double, phase: FlightPhase, targetAglM: Double, windEast: Double, windNorth: Double, dtS: Double) {
        steer(courseDeg, windEast, windNorth, dtS)
        airspeedMps += (phase.speedMps - airspeedMps).coerceIn(-ACCEL_MPS2 * dtS, ACCEL_MPS2 * dtS)
        val headingRad = Math.toRadians(headingDeg)
        val vx = airspeedMps * sin(headingRad) + windEast
        val vy = airspeedMps * cos(headingRad) + windNorth
        val terrainAhead = max(Terrain.elevation(x, y), Terrain.elevation(x + vx * LOOKAHEAD_S, y + vy * LOOKAHEAD_S))
        climbMps = ((terrainAhead + targetAglM - mslM) * ALTITUDE_GAIN).coerceIn(-FlightModel.CLIMB_MPS, FlightModel.CLIMB_MPS)
        x += vx * dtS
        y += vy * dtS
        mslM = max(mslM + climbMps * dtS, Terrain.elevation(x, y) + MIN_CLEARANCE_M)
        val watts = phase.watts + FlightModel.CLIMB_W_PER_MPS * max(climbMps, 0.0)
        batteryPct = max(0.0, batteryPct - FlightModel.drainPctPerSecond(watts) * dtS)
    }

    private fun steer(courseDeg: Double, windEast: Double, windNorth: Double, dtS: Double) {
        val courseRad = Math.toRadians(courseDeg)
        val crabbedDeg = bearingDeg(airspeedMps * sin(courseRad) - windEast, airspeedMps * cos(courseRad) - windNorth)
        val maxTurn = FlightModel.MAX_TURN_RATE_DPS * dtS
        headingDeg = normalizeDeg(headingDeg + signedDeltaDeg(headingDeg, crabbedDeg).coerceIn(-maxTurn, maxTurn))
    }

    private companion object {
        const val ACCEL_MPS2 = 1.5
        const val LOOKAHEAD_S = 12.0
        const val ALTITUDE_GAIN = 0.25
        const val MIN_CLEARANCE_M = 25.0
    }
}
