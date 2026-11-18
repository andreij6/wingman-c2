package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.Terrain
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarHorizon(
    val x: Double,
    val y: Double,
    val radiusM: Double,
    antennaAglM: Double = ANTENNA_AGL_M,
) {
    val antennaMslM = Terrain.elevation(x, y) + antennaAglM
    private val rangeBins = ceil(radiusM / RANGE_STEP_M).toInt().coerceAtLeast(1)
    private val horizonSlope = FloatArray(AZIMUTH_BINS * rangeBins)

    init {
        for (azimuth in 0 until AZIMUTH_BINS) {
            val theta = (azimuth + 0.5) * BIN_RAD - PI
            val ux = cos(theta)
            val uy = sin(theta)
            val row = azimuth * rangeBins
            var maxSlope = Float.NEGATIVE_INFINITY
            for (bin in 0 until rangeBins) {
                horizonSlope[row + bin] = maxSlope
                val d = (bin + 1) * RANGE_STEP_M
                val slope = ((Terrain.elevation(x + ux * d, y + uy * d) - antennaMslM) / d).toFloat()
                if (slope > maxSlope) maxSlope = slope
            }
        }
    }

    fun covers(px: Double, py: Double): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= radiusM * radiusM
    }

    fun exposure(px: Double, py: Double, altitudeMslM: Double): Float {
        val dx = px - x
        val dy = py - y
        val d = sqrt(dx * dx + dy * dy)
        if (d > radiusM || !lineOfSight(dx, dy, d, altitudeMslM)) return 0f
        return (1.0 - FALLOFF * d / radiusM).toFloat()
    }

    private fun lineOfSight(dx: Double, dy: Double, d: Double, altitudeMslM: Double): Boolean {
        if (d < RANGE_STEP_M) return true
        val azimuth = ((atan2(dy, dx) + PI) / BIN_RAD).toInt().coerceAtMost(AZIMUTH_BINS - 1)
        val bin = (d / RANGE_STEP_M).toInt().coerceAtMost(rangeBins - 1)
        return (altitudeMslM - antennaMslM) / d >= horizonSlope[azimuth * rangeBins + bin]
    }

    companion object {
        const val ANTENNA_AGL_M = 12.0
        private const val AZIMUTH_BINS = 360
        private const val RANGE_STEP_M = 10.0
        private const val BIN_RAD = 2 * PI / AZIMUTH_BINS
        private const val FALLOFF = 0.5
    }
}
