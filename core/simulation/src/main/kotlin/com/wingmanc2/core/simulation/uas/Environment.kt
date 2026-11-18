package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.Terrain
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

internal object WindField {
    fun east(tS: Double): Double = 2.4 + 1.1 * sin(tS * 0.13) + 0.5 * sin(tS * 0.47 + 0.8)

    fun north(tS: Double): Double = -1.6 + 0.9 * sin(tS * 0.09 + 2.1) + 0.4 * sin(tS * 0.61)
}

internal object LinkBudget {
    private const val BASE_SNR_DB = 34.0
    private const val REFERENCE_M = 150.0
    private const val RIDGE_MASK_DB = 14.0
    private const val MASK_CLEAR_AGL_M = 260.0

    fun snrDb(x: Double, y: Double, aglM: Double, homeX: Double, homeY: Double): Double {
        val pathLoss = 10.0 * log10(1.0 + hypot(x - homeX, y - homeY) / REFERENCE_M)
        val masked = aglM < MASK_CLEAR_AGL_M && !Terrain.sameSideOfRidge(x, y, homeX, homeY)
        return BASE_SNR_DB - pathLoss - if (masked) RIDGE_MASK_DB else 0.0
    }
}
