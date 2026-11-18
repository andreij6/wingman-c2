package com.wingmanc2.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.component.ChipLeading
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState

val LocalLinkState = compositionLocalOf { LinkState(mode = LinkMode.ONLINE, rttMs = null, lossPct = 0, queued = 0, lastAckMs = null) }

val LinkState.requiresBanner: Boolean
    get() = mode != LinkMode.ONLINE

val LinkMode.label: String
    get() = when (this) {
        LinkMode.ONLINE -> "Online"
        LinkMode.DEGRADED -> "Degraded"
        LinkMode.DENIED -> "Denied"
    }

private val LinkState.chipLabel: String
    get() = when (mode) {
        LinkMode.DENIED -> "${mode.label} · $queued queued"
        else -> rttMs?.let { "${mode.label} · $it ms" } ?: mode.label
    }

@Composable
internal fun LinkChip(state: LinkState, modifier: Modifier = Modifier) {
    val mode = state.mode
    TintChip(
        text = state.chipLabel,
        color = WingmanTheme.colors.linkColor(mode),
        modifier = modifier.semantics { stateDescription = mode.label },
        leading = ChipLeading.Dot,
    )
}

@Composable
internal fun DdilBanner(queued: Int, modifier: Modifier = Modifier, mode: LinkMode = LinkMode.DENIED) {
    val warn = WingmanTheme.colors.warn
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(warn.copy(alpha = 0.12f))
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(warn))
        Text(
            text = ddilMessage(mode, queued),
            modifier = Modifier.padding(start = 13.dp, end = 16.dp),
            style = WingmanTheme.typography.label,
            color = warn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ddilMessage(mode: LinkMode, queued: Int): String {
    val changes = if (queued == 1) "1 change" else "$queued changes"
    return when (mode) {
        LinkMode.DENIED -> "Link denied — working from on-device store. $changes queued."
        LinkMode.DEGRADED -> "Link degraded — replicating slowly. $changes queued."
        LinkMode.ONLINE -> "Link restored — draining $changes."
    }
}
