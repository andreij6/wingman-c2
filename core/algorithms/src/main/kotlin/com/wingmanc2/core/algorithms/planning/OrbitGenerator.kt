package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.model.LocalPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object OrbitGenerator {

    fun orbit(
        centerX: Double,
        centerY: Double,
        radiusM: Double,
        entryBearingRad: Double,
        points: Int = DEFAULT_POINTS,
        clockwise: Boolean = true,
    ): List<LocalPoint> {
        require(points >= 3)
        val step = (if (clockwise) -2.0 else 2.0) * PI / points
        val stepCos = cos(step)
        val stepSin = sin(step)
        var ux = cos(entryBearingRad)
        var uy = sin(entryBearingRad)
        return List(points + 1) {
            val point = LocalPoint(centerX + radiusM * ux, centerY + radiusM * uy)
            val rx = ux * stepCos - uy * stepSin
            uy = ux * stepSin + uy * stepCos
            ux = rx
            point
        }
    }

    const val DEFAULT_POINTS = 16
}
