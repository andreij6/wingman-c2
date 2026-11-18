package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.geo.Terrain
import com.wingmanc2.core.model.TelemetryFrame
import com.wingmanc2.core.simulation.SplitMix64
import com.wingmanc2.core.simulation.bearingDeg
import com.wingmanc2.core.simulation.nextGaussian
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.hypot
import kotlin.math.max

internal class FlightSession(
    route: FlightRoute,
    private val dtS: Double,
    seed: Long,
    private val events: MutableSharedFlow<SimEvent>,
) {
    private val projection = OperationalArea.projection
    private val noise = SplitMix64(seed)
    private val detectedThreats = HashSet<String>()
    private lateinit var track: RouteTrack
    private var homeX = 0.0
    private var homeY = 0.0
    private var targetX = Double.NaN
    private var targetY = Double.NaN
    private var orbitX = 0.0
    private var orbitY = 0.0
    private var orbitRadiusM = ORBIT_RADIUS_M
    private var phase = FlightPhase.EN_ROUTE
    private var resumePhase = FlightPhase.EN_ROUTE
    private var targetAglM = 0.0
    private var simTimeS = 0.0
    private var nextCaptureS = 0.0
    private var captures = 0
    private var batteryFloorReported = false

    init {
        load(route)
    }

    private val airframe = Airframe(
        x = homeX,
        y = homeY,
        headingDeg = bearingDeg(track.x - homeX, track.y - homeY),
        mslM = Terrain.elevation(homeX, homeY) + targetAglM,
        batteryPct = FlightModel.START_BATTERY_PCT,
    )

    private val hasTarget: Boolean get() = !targetX.isNaN()

    fun execute(command: FlightCommand) {
        when (command) {
            FlightCommand.Hold -> hold()
            FlightCommand.Resume -> if (phase == FlightPhase.HOLDING) phase = resumePhase
            FlightCommand.ReturnToBase -> returnToBase()
            is FlightCommand.Replan -> {
                load(command.route)
                phase = FlightPhase.EN_ROUTE
            }
        }
    }

    fun step(nowMs: Long, threats: List<PopupThreat>): TelemetryFrame {
        simTimeS += dtS
        airframe.step(desiredCourseDeg(), phase, targetAglM, WindField.east(simTimeS), WindField.north(simTimeS), dtS)
        if (phase.navigating) captureWaypoint(nowMs)
        captureImagery(nowMs)
        detectThreats(nowMs, threats)
        checkBattery(nowMs)
        return frame(nowMs)
    }

    private fun load(route: FlightRoute) {
        track = RouteTrack.of(route)
        homeX = projection.xOf(route.home)
        homeY = projection.yOf(route.home)
        targetX = route.target?.let(projection::xOf) ?: Double.NaN
        targetY = route.target?.let(projection::yOf) ?: Double.NaN
        targetAglM = track.altitudeAglM
    }

    private fun hold() {
        if (!phase.navigating) return
        resumePhase = phase
        phase = FlightPhase.HOLDING
        orbitAround(airframe.x, airframe.y, ORBIT_RADIUS_M)
    }

    private fun returnToBase() {
        track = RouteTrack.direct(homeX, homeY, targetAglM)
        phase = FlightPhase.RETURNING
        resumePhase = FlightPhase.RETURNING
    }

    private fun desiredCourseDeg(): Double {
        if (phase.navigating) return bearingDeg(track.x - airframe.x, track.y - airframe.y)
        val dx = orbitX - airframe.x
        val dy = orbitY - airframe.y
        val radialError = (hypot(dx, dy) - orbitRadiusM) / orbitRadiusM
        return bearingDeg(dx, dy) - 90.0 + (radialError * 90.0).coerceIn(-MAX_ORBIT_CORRECTION_DEG, MAX_ORBIT_CORRECTION_DEG)
    }

    private fun captureWaypoint(nowMs: Long) {
        if (hypot(track.x - airframe.x, track.y - airframe.y) > CAPTURE_RADIUS_M) return
        events.tryEmit(SimEvent.WaypointReached(nowMs, track.index, track.size, projection.toGeo(track.x, track.y)))
        track.advance()
        if (!track.finished) {
            targetAglM = track.altitudeAglM
            return
        }
        if (phase == FlightPhase.RETURNING) {
            phase = FlightPhase.HOME
            orbitAround(homeX, homeY, ORBIT_RADIUS_M)
            events.tryEmit(SimEvent.HomeReached(nowMs, projection.toGeo(homeX, homeY)))
        } else {
            phase = FlightPhase.LOITERING
            if (hasTarget) {
                orbitAround(targetX, targetY, max(hypot(track.x - targetX, track.y - targetY), ORBIT_RADIUS_M))
            } else {
                orbitAround(track.x, track.y, ORBIT_RADIUS_M)
            }
        }
    }

    private fun orbitAround(x: Double, y: Double, radiusM: Double) {
        orbitX = x
        orbitY = y
        orbitRadiusM = radiusM
    }

    private fun captureImagery(nowMs: Long) {
        if (!hasTarget || simTimeS < nextCaptureS) return
        val range = hypot(targetX - airframe.x, targetY - airframe.y)
        if (range > CAPTURE_RANGE_M) return
        nextCaptureS = simTimeS + CAPTURE_INTERVAL_S
        captures++
        events.tryEmit(SimEvent.Captured(nowMs, captures, projection.toGeo(airframe.x, airframe.y), range))
    }

    private fun detectThreats(nowMs: Long, threats: List<PopupThreat>) {
        for (i in threats.indices) {
            val threat = threats[i]
            if (threat.key in detectedThreats) continue
            val dx = projection.xOf(threat.position) - airframe.x
            val dy = projection.yOf(threat.position) - airframe.y
            val distance = hypot(dx, dy)
            if (distance > threat.threatRadiusM + DETECTION_RANGE_M) continue
            detectedThreats += threat.key
            events.tryEmit(SimEvent.ThreatDetected(nowMs, threat.key, threat.position, distance, bearingDeg(dx, dy)))
        }
    }

    private fun checkBattery(nowMs: Long) {
        if (batteryFloorReported || airframe.batteryPct > FlightModel.RTB_FLOOR_PCT) return
        batteryFloorReported = true
        events.tryEmit(SimEvent.BatteryFloor(nowMs, airframe.batteryPct))
    }

    private fun frame(nowMs: Long): TelemetryFrame {
        val agl = airframe.aglM
        return TelemetryFrame(
            tMs = nowMs,
            position = projection.toGeo(airframe.x, airframe.y),
            headingDeg = airframe.headingDeg,
            altitudeAglM = agl,
            airspeedMps = airframe.airspeedMps,
            climbMps = airframe.climbMps,
            batteryPct = airframe.batteryPct,
            linkSnrDb = LinkBudget.snrDb(airframe.x, airframe.y, agl, homeX, homeY) + noise.nextGaussian() * SNR_JITTER_DB,
            waypointIndex = track.index,
            waypointCount = track.size,
        )
    }

    private companion object {
        const val CAPTURE_RADIUS_M = 80.0
        const val ORBIT_RADIUS_M = 140.0
        const val MAX_ORBIT_CORRECTION_DEG = 75.0
        const val CAPTURE_RANGE_M = 450.0
        const val CAPTURE_INTERVAL_S = 15.0
        const val DETECTION_RANGE_M = 700.0
        const val SNR_JITTER_DB = 0.6
    }
}
