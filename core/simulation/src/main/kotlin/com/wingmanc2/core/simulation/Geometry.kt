package com.wingmanc2.core.simulation

import kotlin.math.atan2

internal fun bearingDeg(dx: Double, dy: Double): Double {
    val deg = Math.toDegrees(atan2(dx, dy))
    return if (deg < 0.0) deg + 360.0 else deg
}

internal fun normalizeDeg(deg: Double): Double {
    val wrapped = deg % 360.0
    return if (wrapped < 0.0) wrapped + 360.0 else wrapped
}

internal fun signedDeltaDeg(fromDeg: Double, toDeg: Double): Double {
    val delta = normalizeDeg(toDeg - fromDeg)
    return if (delta > 180.0) delta - 360.0 else delta
}

internal fun compassPoint(bearing: Double): String = COMPASS[((normalizeDeg(bearing) + 22.5) / 45.0).toInt() % 8]

private val COMPASS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
