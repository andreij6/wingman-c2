package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.OperationalArea

internal class RouteTrack private constructor(
    private val xs: DoubleArray,
    private val ys: DoubleArray,
    private val altitudes: DoubleArray,
    private val loop: Boolean,
) {
    var index = 0
        private set

    val size: Int get() = xs.size
    val finished: Boolean get() = index >= xs.size
    val x: Double get() = xs[index.coerceAtMost(xs.lastIndex)]
    val y: Double get() = ys[index.coerceAtMost(xs.lastIndex)]
    val altitudeAglM: Double get() = altitudes[index.coerceAtMost(xs.lastIndex)]

    fun advance() {
        index = if (loop && index == xs.lastIndex) 0 else index + 1
    }

    companion object {
        fun of(route: FlightRoute): RouteTrack {
            val projection = OperationalArea.projection
            val points = route.waypoints
            return RouteTrack(
                xs = DoubleArray(points.size) { projection.xOf(points[it]) },
                ys = DoubleArray(points.size) { projection.yOf(points[it]) },
                altitudes = route.altitudesAglM.toDoubleArray(),
                loop = route.loop,
            )
        }

        fun direct(x: Double, y: Double, altitudeAglM: Double) =
            RouteTrack(doubleArrayOf(x), doubleArrayOf(y), doubleArrayOf(altitudeAglM), loop = false)
    }
}
