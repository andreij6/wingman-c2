package com.wingmanc2.core.algorithms.random

object Mix64 {
    const val GOLDEN_GAMMA = -0x61c8864680b583ebL
    private const val DOUBLE_UNIT = 1.0 / (1L shl 53)

    fun splitMix(z: Long): Long {
        var h = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
        return h xor (h ushr 31)
    }

    fun fmix(z: Long): Long {
        var h = (z xor (z ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        return h xor (h ushr 33)
    }

    fun unitDouble(bits: Long): Double = (bits ushr 11) * DOUBLE_UNIT
}
