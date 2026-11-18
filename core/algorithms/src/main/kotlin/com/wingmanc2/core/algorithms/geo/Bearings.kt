package com.wingmanc2.core.algorithms.geo

import kotlin.math.atan2
import kotlin.math.sqrt

fun bearingDeg(dx: Double, dy: Double): Double = normalizeDeg(Math.toDegrees(atan2(dx, dy)))

fun normalizeDeg(deg: Double): Double {
    val wrapped = deg % 360.0
    return if (wrapped < 0.0) wrapped + 360.0 else wrapped
}

fun signedDeltaDeg(fromDeg: Double, toDeg: Double): Double {
    val delta = normalizeDeg(toDeg - fromDeg)
    return if (delta > 180.0) delta - 360.0 else delta
}

fun compassIndex(bearingDeg: Double): Int = ((normalizeDeg(bearingDeg) + 22.5) / 45.0).toInt() % 8

fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val vx = bx - ax
    val vy = by - ay
    val len2 = vx * vx + vy * vy
    val t = if (len2 == 0.0) 0.0 else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0.0, 1.0)
    val dx = px - (ax + t * vx)
    val dy = py - (ay + t * vy)
    return sqrt(dx * dx + dy * dy)
}
