package com.wingmanc2.feature.mission.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.component.SectionHeader
import com.wingmanc2.core.designsystem.component.WingmanCard
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import kotlinx.collections.immutable.ImmutableList

private val RailWidth = 16.dp
private val DotRadius = 5.dp
private val DotCenterY = 10.dp
private val RailStroke = 1.5.dp

@Composable
internal fun DecisionTimelineCard(decisions: ImmutableList<DecisionUi>, meta: String, modifier: Modifier = Modifier) {
    WingmanCard(modifier.fillMaxWidth()) {
        SectionHeader("Autonomy decisions", meta = meta)
        Spacer(Modifier.height(WingmanSpacing.sm))
        if (decisions.isEmpty()) {
            Text("No autonomous actions yet.", style = WingmanTheme.typography.bodySmall, color = WingmanTheme.colors.text2)
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(decisions.first().id) { listState.scrollToItem(0) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 184.dp), state = listState) {
                itemsIndexed(decisions, key = { _, decision -> decision.id }) { index, decision ->
                    DecisionRow(decision, railAbove = index > 0, railBelow = index < decisions.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun DecisionRow(decision: DecisionUi, railAbove: Boolean, railBelow: Boolean) {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimelineRail(decision.tone.color(), colors.line, railAbove, railBelow, Modifier.width(RailWidth).fillMaxHeight())
        Text(
            text = decision.time,
            modifier = Modifier.padding(start = WingmanSpacing.grid, top = 2.dp).width(64.dp),
            style = typography.dataSmall,
            fontWeight = FontWeight.Bold,
            color = colors.muted,
            maxLines = 1,
        )
        Column(Modifier.weight(1f).padding(bottom = WingmanSpacing.sm)) {
            Text(decision.title, style = typography.body, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(decision.rationale, style = typography.bodySmall, color = colors.text2)
        }
    }
}

@Composable
private fun TimelineRail(dot: Color, rail: Color, above: Boolean, below: Boolean, modifier: Modifier) {
    Spacer(
        modifier.drawBehind {
            val x = size.width / 2f
            val dotY = DotCenterY.toPx()
            val stroke = RailStroke.toPx()
            if (above) drawLine(rail, Offset(x, 0f), Offset(x, dotY), stroke)
            if (below) drawLine(rail, Offset(x, dotY), Offset(x, size.height), stroke)
            drawCircle(dot, DotRadius.toPx(), Offset(x, dotY))
        },
    )
}
