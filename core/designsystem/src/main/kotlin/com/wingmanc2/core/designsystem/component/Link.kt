package com.wingmanc2.core.designsystem.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState

fun WingmanColors.linkColor(mode: LinkMode): Color = if (mode == LinkMode.ONLINE) ok else warn

fun LinkState.chipLabel(): String = when (mode) {
    LinkMode.ONLINE -> rttMs?.let { "Online · $it ms" } ?: "Online"
    LinkMode.DEGRADED -> rttMs?.let { "Degraded · $it ms" } ?: "Degraded"
    LinkMode.DENIED -> "Denied · $queued queued"
}

@Composable
fun LinkChip(state: LinkState, modifier: Modifier = Modifier) {
    TintChip(
        text = state.chipLabel(),
        color = WingmanTheme.colors.linkColor(state.mode),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        leading = ChipLeading.Dot,
    )
}

@Composable
fun DdilBanner(queued: Int, modifier: Modifier = Modifier, mode: LinkMode = LinkMode.DENIED) {
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
