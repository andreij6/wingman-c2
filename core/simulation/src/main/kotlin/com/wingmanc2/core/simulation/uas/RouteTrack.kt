package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.OperationalArea

internal class RouteTrack private constructor(
    private val xs: DoubleArray,
    private val ys: DoubleArray,
    private val altitudes: DoubleArray,
    private val loopFrom: Int?,
) {
    var index = 0
        private set

    val size: Int get() = xs.size
    val finished: Boolean get() = index >= xs.size
    val looping: Boolean get() = loopFrom != null && index >= loopFrom
    val x: Double get() = xs[index.coerceAtMost(xs.lastIndex)]
    val y: Double get() = ys[index.coerceAtMost(xs.lastIndex)]
    val altitudeAglM: Double get() = altitudes[index.coerceAtMost(xs.lastIndex)]

    fun advance() {
        index = if (loopFrom != null && index == xs.lastIndex) loopFrom else index + 1
    }

    companion object {
        fun of(route: FlightRoute): RouteTrack {
            val points = route.waypoints.map(OperationalArea.projection::toLocal)
            return RouteTrack(
                xs = DoubleArray(points.size) { points[it].x },
                ys = DoubleArray(points.size) { points[it].y },
                altitudes = route.altitudesAglM.toDoubleArray(),
                loopFrom = route.loopFrom,
            )
        }

        fun direct(x: Double, y: Double, altitudeAglM: Double) =
            RouteTrack(doubleArrayOf(x), doubleArrayOf(y), doubleArrayOf(altitudeAglM), loopFrom = null)
    }
}
