package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.OperationalArea

internal data class LoadedRoute(
    val track: RouteTrack,
    val homeX: Double,
    val homeY: Double,
    val targetX: Double,
    val targetY: Double,
    val reserveFloorPct: Double,
) {
    val hasTarget: Boolean get() = !targetX.isNaN()

    companion object {
        fun of(route: FlightRoute): LoadedRoute {
            val projection = OperationalArea.projection
            val home = projection.toLocal(route.home)
            val target = route.target?.let(projection::toLocal)
            return LoadedRoute(
                track = RouteTrack.of(route),
                homeX = home.x,
                homeY = home.y,
                targetX = target?.x ?: Double.NaN,
                targetY = target?.y ?: Double.NaN,
                reserveFloorPct = route.reserveFloorPct,
            )
        }
    }
}
