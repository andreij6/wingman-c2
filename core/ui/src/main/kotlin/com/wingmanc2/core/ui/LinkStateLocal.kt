package com.wingmanc2.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState

val LocalLinkState = staticCompositionLocalOf { LinkState(mode = LinkMode.ONLINE, rttMs = null, lossPct = 0, queued = 0, lastAckMs = null) }

val LinkState.requiresBanner: Boolean
    get() = mode == LinkMode.DENIED || (mode == LinkMode.DEGRADED && queued > 0)
