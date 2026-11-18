package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.GeoPoint

internal enum class FlightRegime(val speedMps: Double, val watts: Double) {
    CRUISE(FlightModel.CRUISE_MPS, FlightModel.CRUISE_W),
    DASH(FlightModel.DASH_MPS, FlightModel.DASH_W),
    LOITER(FlightModel.LOITER_MPS, FlightModel.LOITER_W),
}

internal data class Waypoint(val x: Double, val y: Double, val altitudeAglM: Double)

internal class Route private constructor(val waypoints: List<Waypoint>, val legs: List<FlightRegime>) {
    val start get() = waypoints.first()
    val end get() = waypoints.last()

    fun toGeoPoints(): List<GeoPoint> = waypoints.map { OperationalArea.projection.toGeo(it.x, it.y) }

    class Builder(start: Waypoint) {
        private val waypoints = mutableListOf(start)
        private val legs = mutableListOf<FlightRegime>()

        fun to(waypoint: Waypoint, regime: FlightRegime): Builder = apply {
            if (waypoint == waypoints.last()) return@apply
            waypoints += waypoint
            legs += regime
        }

        fun build(): Route = Route(waypoints.toList(), legs.toList())
    }
}
