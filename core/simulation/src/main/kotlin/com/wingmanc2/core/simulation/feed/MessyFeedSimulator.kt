package com.wingmanc2.core.simulation.feed

import com.wingmanc2.core.algorithms.random.SplitMix64
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.model.RawFeedMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessyFeedSimulator @Inject constructor(private val clock: Clock) {
    private val burstSeeds = SplitMix64(BURST_SEED)

    fun feed(seed: Long = FEED_SEED, meanIntervalMs: Long = MEAN_INTERVAL_MS): Flow<RawFeedMessage> = flow {
        val composer = FeedComposer(SplitMix64(seed))
        while (true) {
            delay(composer.nextGapMs(meanIntervalMs))
            emit(composer.next(clock.nowMs()))
        }
    }

    fun burst(count: Int = BURST_SIZE): List<RawFeedMessage> {
        val composer = FeedComposer(SplitMix64(burstSeeds.nextLong()))
        val now = clock.nowMs()
        return List(count) { composer.next(now - (count - 1 - it) * BURST_SPACING_MS) }
    }

    fun conflictingReport(): RawFeedMessage = FeedComposer.conflictingReport(clock.nowMs())

    companion object {
        const val BURST_SIZE = 25
        private const val FEED_SEED = 0xFEED_07L
        private const val BURST_SEED = 0xB0257L
        private const val MEAN_INTERVAL_MS = 2_400L
        private const val BURST_SPACING_MS = 40L
    }
}
