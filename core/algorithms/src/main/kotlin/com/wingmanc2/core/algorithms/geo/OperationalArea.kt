package com.wingmanc2.core.algorithms.geo

import com.wingmanc2.core.model.GeoPoint

object OperationalArea {
    val origin = GeoPoint(38.7020, -78.5490)
    const val HALF_EXTENT_M = 2_000.0
    const val EXTENT_M = HALF_EXTENT_M * 2
    val projection = LocalProjection(origin)

    fun contains(x: Double, y: Double) = x >= -HALF_EXTENT_M && x <= HALF_EXTENT_M && y >= -HALF_EXTENT_M && y <= HALF_EXTENT_M

    fun u(x: Double) = (x + HALF_EXTENT_M) / EXTENT_M

    fun v(y: Double) = (HALF_EXTENT_M - y) / EXTENT_M
}
