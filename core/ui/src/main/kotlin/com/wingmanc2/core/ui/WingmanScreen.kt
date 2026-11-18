package com.wingmanc2.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme

@Composable
fun WingmanScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    bannerGap: Dp = WingmanSpacing.sm,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(WingmanTheme.colors.background)) {
        WingmanTopBar(title, subtitle, actions)
        LinkBanner(bannerGap)
        content()
    }
}

@Composable
private fun ColumnScope.LinkBanner(bannerGap: Dp) {
    val link = LocalLinkState.current
    AnimatedVisibility(
        visible = link.requiresBanner,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        DdilBanner(queued = link.queued, mode = link.mode, modifier = Modifier.padding(bottom = bannerGap))
    }
}

@Composable
private fun WingmanTopBar(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = WingmanSpacing.margin, end = WingmanSpacing.margin, top = WingmanSpacing.grid, bottom = WingmanSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = WingmanTheme.typography.title,
                color = WingmanTheme.colors.text,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = WingmanTheme.typography.bodySmall,
                color = WingmanTheme.colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
        LinkChip(LocalLinkState.current, Modifier.padding(top = 6.dp))
    }
}
