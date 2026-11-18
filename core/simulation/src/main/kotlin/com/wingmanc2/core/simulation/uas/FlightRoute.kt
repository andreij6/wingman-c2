package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.model.GeoPoint

data class FlightRoute(
    val home: GeoPoint,
    val waypoints: List<GeoPoint>,
    val altitudesAglM: List<Double>,
    val target: GeoPoint? = null,
    val loopFrom: Int? = null,
    val reserveFloorPct: Double = FlightModel.RTB_FLOOR_PCT,
) {
    init {
        require(waypoints.isNotEmpty()) { "A route needs at least one waypoint" }
        require(altitudesAglM.size == waypoints.size) { "One target altitude per waypoint" }
        require(loopFrom == null || loopFrom in waypoints.indices) { "Loop must restart at a route waypoint" }
    }
}

data class PopupThreat(val key: String, val position: GeoPoint, val threatRadiusM: Double)

sealed interface FlightCommand {
    data object Hold : FlightCommand
    data object Resume : FlightCommand
    data object ReturnToBase : FlightCommand
    data class Replan(val route: FlightRoute) : FlightCommand
}

sealed interface SimEvent {
    val tMs: Long

    data class WaypointReached(override val tMs: Long, val index: Int, val count: Int, val position: GeoPoint) : SimEvent

    data class ThreatDetected(
        override val tMs: Long,
        val threatKey: String,
        val threatPosition: GeoPoint,
        val distanceM: Double,
        val bearingDeg: Double,
    ) : SimEvent

    data class Captured(override val tMs: Long, val frameNumber: Int, val position: GeoPoint, val rangeToTargetM: Double) : SimEvent

    data class BatteryFloor(override val tMs: Long, val batteryPct: Double) : SimEvent

    data class HomeReached(override val tMs: Long, val position: GeoPoint) : SimEvent
}
