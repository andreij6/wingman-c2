package com.wingmanc2.core.simulation.link

import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.simulation.SplitMix64
import com.wingmanc2.core.simulation.chance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class LinkQuality(val mode: LinkMode, val rttMs: Int?, val lossPct: Int)

sealed interface LinkSample {
    data class Delivered(val rttMs: Int) : LinkSample
    data object Lost : LinkSample
}

@Singleton
class LinkSimulator @Inject constructor() {
    private val jitter = SplitMix64(LINK_SEED)
    private val mutableQuality = MutableStateFlow(LinkProfile.of(LinkMode.ONLINE).nominal())

    val quality: StateFlow<LinkQuality> = mutableQuality.asStateFlow()

    val mode: LinkMode get() = mutableQuality.value.mode

    fun setMode(mode: LinkMode) {
        mutableQuality.value = LinkProfile.of(mode).nominal()
    }

    fun sample(): LinkSample {
        val current = mode
        val profile = LinkProfile.of(current)
        if (jitter.chance(profile.lossProbability)) return LinkSample.Lost
        val rtt = jitter.nextInt(profile.rttMinMs, profile.rttMaxMs + 1)
        mutableQuality.update { if (it.mode == current) it.copy(rttMs = rtt) else it }
        return LinkSample.Delivered(rtt)
    }

    private companion object {
        const val LINK_SEED = 0x11_4B_D0L
    }
}

internal enum class LinkProfile(val mode: LinkMode, val rttMinMs: Int, val rttMaxMs: Int, val lossProbability: Double) {
    ONLINE(LinkMode.ONLINE, 30, 60, 0.004),
    DEGRADED(LinkMode.DEGRADED, 250, 500, 0.2),
    DENIED(LinkMode.DENIED, 0, 0, 1.0);

    fun nominal() = LinkQuality(
        mode = mode,
        rttMs = if (lossProbability >= 1.0) null else (rttMinMs + rttMaxMs) / 2,
        lossPct = (lossProbability * 100).toInt(),
    )

    companion object {
        fun of(mode: LinkMode): LinkProfile = when (mode) {
            LinkMode.ONLINE -> ONLINE
            LinkMode.DEGRADED -> DEGRADED
            LinkMode.DENIED -> DENIED
        }
    }
}
