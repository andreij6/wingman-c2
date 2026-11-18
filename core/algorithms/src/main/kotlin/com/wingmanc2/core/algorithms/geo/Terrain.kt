package com.wingmanc2.core.algorithms.geo

import kotlin.math.exp
import kotlin.math.sqrt

object Terrain {
    private const val AX = -2_000.0
    private const val AY = -1_906.0
    private const val BX = 2_000.0
    private const val BY = 1_023.0
    private const val CREST_M = 420.0
    private const val SIGMA_M = 260.0
    private const val BASE_M = 180.0
    private val dx = BX - AX
    private val dy = BY - AY
    private val invLen = 1.0 / sqrt(dx * dx + dy * dy)
    private val inv2Sigma2 = 1.0 / (2 * SIGMA_M * SIGMA_M)

    fun signedDistanceToCrest(x: Double, y: Double): Double = ((x - AX) * dy - (y - AY) * dx) * invLen

    fun elevation(x: Double, y: Double): Double {
        val d = signedDistanceToCrest(x, y)
        return BASE_M + CREST_M * exp(-d * d * inv2Sigma2)
    }

    fun sameSideOfRidge(ax: Double, ay: Double, bx: Double, by: Double): Boolean =
        (signedDistanceToCrest(ax, ay) >= 0) == (signedDistanceToCrest(bx, by) >= 0)
}
