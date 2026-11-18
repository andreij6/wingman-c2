package com.wingmanc2.core.simulation

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

internal class SplitMix64(seed: Long) : Random() {
    private val state = AtomicLong(seed)

    override fun nextLong(): Long {
        var z = state.addAndGet(GOLDEN_GAMMA)
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    override fun nextBits(bitCount: Int): Int = if (bitCount == 0) 0 else (nextLong() ushr (64 - bitCount)).toInt()

    override fun nextDouble(): Double = (nextLong() ushr 11) * DOUBLE_UNIT

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}

internal fun Random.nextGaussian(): Double = sqrt(-2.0 * ln(1.0 - nextDouble())) * cos(2.0 * PI * nextDouble())

internal fun Random.chance(probability: Double): Boolean = nextDouble() < probability
