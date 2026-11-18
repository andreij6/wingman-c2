package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.geo.Terrain
import com.wingmanc2.core.algorithms.geo.bearingDeg
import com.wingmanc2.core.algorithms.random.SplitMix64
import com.wingmanc2.core.model.TelemetryFrame
import com.wingmanc2.core.simulation.nextGaussian
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.hypot
import kotlin.math.roundToLong

internal class FlightSession(
    route: FlightRoute,
    private val startMs: Long,
    private val dtS: Double,
    seed: Long,
    private val events: MutableSharedFlow<SimEvent>,
) {
    private val projection = OperationalArea.projection
    private val noise = SplitMix64(seed)
    private val detectedThreats = HashSet<String>()
    private var loaded = LoadedRoute.of(route)
    private val airframe = launch(loaded)
    private var orbitX = 0.0
    private var orbitY = 0.0
    private var phase = FlightPhase.EN_ROUTE
    private var resumePhase = FlightPhase.EN_ROUTE
    private var simTimeS = 0.0
    private var nextCaptureS = 0.0
    private var captures = 0
    private var batteryFloorReported = false

    private val track: RouteTrack get() = loaded.track

    private val nowMs: Long get() = startMs + (simTimeS * 1_000.0).roundToLong()

    fun execute(command: FlightCommand) {
        when (command) {
            FlightCommand.Hold -> hold()
            FlightCommand.Resume -> if (phase == FlightPhase.HOLDING) phase = resumePhase
            FlightCommand.ReturnToBase -> returnToBase()
            is FlightCommand.Replan -> if (resumePhase != FlightPhase.RETURNING) {
                loaded = LoadedRoute.of(command.route)
                phase = FlightPhase.EN_ROUTE
            }
        }
    }

    fun step(threats: List<PopupThreat>): TelemetryFrame {
        simTimeS += dtS
        airframe.step(desiredCourseDeg(), phase, track.altitudeAglM, WindField.east(simTimeS), WindField.north(simTimeS), dtS)
        val now = nowMs
        if (phase.navigating) captureWaypoint(now)
        captureImagery(now)
        detectThreats(now, threats)
        checkBattery(now)
        return frame(now)
    }

    private fun hold() {
        if (!phase.navigating) return
        resumePhase = phase
        phase = FlightPhase.HOLDING
        orbitAround(airframe.x, airframe.y)
    }

    private fun returnToBase() {
        loaded = loaded.copy(track = RouteTrack.direct(loaded.homeX, loaded.homeY, track.altitudeAglM))
        phase = FlightPhase.RETURNING
        resumePhase = FlightPhase.RETURNING
    }

    private fun desiredCourseDeg(): Double {
        if (phase.navigating) return bearingDeg(track.x - airframe.x, track.y - airframe.y)
        val dx = orbitX - airframe.x
        val dy = orbitY - airframe.y
        val radialError = (hypot(dx, dy) - ORBIT_RADIUS_M) / ORBIT_RADIUS_M
        return bearingDeg(dx, dy) - 90.0 + (radialError * 90.0).coerceIn(-MAX_ORBIT_CORRECTION_DEG, MAX_ORBIT_CORRECTION_DEG)
    }

    private fun captureWaypoint(nowMs: Long) {
        if (hypot(track.x - airframe.x, track.y - airframe.y) > CAPTURE_RADIUS_M) return
        events.tryEmit(SimEvent.WaypointReached(nowMs, track.index, track.size, projection.toGeo(track.x, track.y)))
        track.advance()
        if (!track.finished) {
            if (phase == FlightPhase.EN_ROUTE && track.looping) phase = FlightPhase.ORBITING
            return
        }
        if (phase == FlightPhase.RETURNING) {
            phase = FlightPhase.HOME
            orbitAround(loaded.homeX, loaded.homeY)
            events.tryEmit(SimEvent.HomeReached(nowMs, projection.toGeo(loaded.homeX, loaded.homeY)))
        } else {
            phase = FlightPhase.LOITERING
            orbitAround(track.x, track.y)
        }
    }

    private fun orbitAround(x: Double, y: Double) {
        orbitX = x
        orbitY = y
    }

    private fun captureImagery(nowMs: Long) {
        if (!loaded.hasTarget || simTimeS < nextCaptureS) return
        val range = hypot(loaded.targetX - airframe.x, loaded.targetY - airframe.y)
        if (range > FlightModel.EO_RANGE_M) return
        nextCaptureS = simTimeS + CAPTURE_INTERVAL_S
        captures++
        events.tryEmit(SimEvent.Captured(nowMs, captures, projection.toGeo(airframe.x, airframe.y), range))
    }

    private fun detectThreats(nowMs: Long, threats: List<PopupThreat>) {
        for (i in threats.indices) {
            val threat = threats[i]
            if (threat.key in detectedThreats) continue
            val at = projection.toLocal(threat.position)
            val dx = at.x - airframe.x
            val dy = at.y - airframe.y
            val distance = hypot(dx, dy)
            if (distance > threat.threatRadiusM + DETECTION_RANGE_M) continue
            detectedThreats += threat.key
            events.tryEmit(SimEvent.ThreatDetected(nowMs, threat.key, threat.position, distance, bearingDeg(dx, dy)))
        }
    }

    private fun checkBattery(nowMs: Long) {
        if (batteryFloorReported || airframe.batteryPct > loaded.reserveFloorPct) return
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
            linkSnrDb = LinkBudget.snrDb(airframe.x, airframe.y, agl, loaded.homeX, loaded.homeY) + noise.nextGaussian() * SNR_JITTER_DB,
            waypointIndex = track.index,
            waypointCount = track.size,
        )
    }

    private companion object {
        const val CAPTURE_RADIUS_M = 80.0
        const val ORBIT_RADIUS_M = 140.0
        const val MAX_ORBIT_CORRECTION_DEG = 75.0
        const val CAPTURE_INTERVAL_S = 15.0
        const val DETECTION_RANGE_M = 700.0
        const val SNR_JITTER_DB = 0.6

        fun launch(route: LoadedRoute) = Airframe(
            x = route.homeX,
            y = route.homeY,
            headingDeg = bearingDeg(route.track.x - route.homeX, route.track.y - route.homeY),
            mslM = Terrain.elevation(route.homeX, route.homeY) + route.track.altitudeAglM,
            batteryPct = FlightModel.START_BATTERY_PCT,
        )
    }
}
