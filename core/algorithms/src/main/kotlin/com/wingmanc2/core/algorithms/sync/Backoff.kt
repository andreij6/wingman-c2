package com.wingmanc2.core.algorithms.sync

class Backoff(
    private val baseMs: Long = 250,
    private val capMs: Long = 30_000,
    private val seed: Long = 0x5EED_C2L,
) {
    init {
        require(baseMs > 0 && capMs >= baseMs)
    }

    fun ceilingMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 62)
        return if (baseMs > capMs ushr shift) capMs else baseMs shl shift
    }

    fun delayMs(attempt: Int, salt: Long = 0): Long {
        val ceiling = ceilingMs(attempt)
        val unit = (mix(seed xor salt * SALT_STRIDE + attempt * GOLDEN_GAMMA) ushr 11) * DOUBLE_UNIT
        return (unit * (ceiling + 1)).toLong()
    }

    private fun mix(input: Long): Long {
        var z = input + GOLDEN_GAMMA
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val SALT_STRIDE = 0x2545F4914F6CDD1DL
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}
