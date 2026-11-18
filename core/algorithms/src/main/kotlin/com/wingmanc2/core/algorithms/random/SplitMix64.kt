package com.wingmanc2.core.algorithms.random

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class SplitMix64(seed: Long) : Random() {
    private val state = AtomicLong(seed)

    override fun nextLong(): Long = Mix64.splitMix(state.addAndGet(Mix64.GOLDEN_GAMMA))

    override fun nextBits(bitCount: Int): Int = if (bitCount == 0) 0 else (nextLong() ushr (64 - bitCount)).toInt()

    override fun nextDouble(): Double = Mix64.unitDouble(nextLong())
}
