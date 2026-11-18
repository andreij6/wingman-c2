package com.wingmanc2.core.algorithms.sync

import com.wingmanc2.core.algorithms.random.Mix64

class Backoff(
    private val baseMs: Long = 250,
    private val capMs: Long = 30_000,
    private val seed: Long = 0x5EED_C2L,
) {
    init {
        require(baseMs > 0 && capMs >= baseMs)
    }

    fun delayMs(attempt: Int, salt: Long = 0): Long {
        val ceiling = ceilingMs(attempt)
        val unit = Mix64.unitDouble(Mix64.splitMix((seed xor salt * SALT_STRIDE + attempt * Mix64.GOLDEN_GAMMA) + Mix64.GOLDEN_GAMMA))
        return (unit * (ceiling + 1)).toLong()
    }

    private fun ceilingMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 62)
        return if (baseMs > capMs ushr shift) capMs else baseMs shl shift
    }

    private companion object {
        const val SALT_STRIDE = 0x2545F4914F6CDD1DL
    }
}
