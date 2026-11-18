package com.wingmanc2.core.algorithms.fusion

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

object ConfidenceFusion {
    private const val FLOOR = 0.02f
    private const val CEILING = 0.99f
    private const val MISSING_FIELD_PENALTY = 0.85f

    fun fuse(prior: Float, evidence: Float, reliability: Float = 1f): Float {
        val logOdds = logit(prior.clamped()) + reliability * max(0.0, logit(evidence.clamped()))
        return (1.0 / (1.0 + exp(-logOdds))).toFloat().clamped()
    }

    internal fun penalized(base: Float, missingFields: Int): Float {
        var confidence = base
        repeat(missingFields) { confidence *= MISSING_FIELD_PENALTY }
        return confidence.clamped()
    }

    private fun logit(p: Float): Double = ln(p / (1.0 - p))

    private fun Float.clamped() = coerceIn(FLOOR, CEILING)
}
