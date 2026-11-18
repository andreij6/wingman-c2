package com.wingmanc2.feature.sync.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.component.Overline
import com.wingmanc2.core.designsystem.component.SectionHeader
import com.wingmanc2.core.designsystem.component.SegmentedControl
import com.wingmanc2.core.designsystem.component.StatusDot
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.component.WingmanCard
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.ScenarioInject
import com.wingmanc2.core.ui.label
import com.wingmanc2.core.ui.linkColor
import com.wingmanc2.core.ui.of
import kotlinx.collections.immutable.toPersistentList

private val LinkOptions = LinkMode.entries.map { it.label }.toPersistentList()
private val InjectRows = ScenarioInject.entries.chunked(2)
private val TileShape = RoundedCornerShape(14.dp)
private val CardRadius = 16.dp

@Composable
internal fun LinkConditionCard(mode: LinkMode, stats: String, onSelect: (LinkMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = WingmanTheme.colors
    WingmanCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
        Overline("Link condition · simulated")
        Spacer(Modifier.height(10.dp))
        SegmentedControl(
            options = LinkOptions,
            selectedIndex = mode.ordinal,
            onSelect = { onSelect(LinkMode.entries[it]) },
            accent = colors.linkColor(mode),
        )
        Spacer(Modifier.height(WingmanSpacing.sm))
        Text(
            text = stats,
            style = WingmanTheme.typography.dataSmall.copy(fontSize = 11.5.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun StoreTiles(edge: StoreTileUi, cloud: StoreTileUi, queued: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StoreTile(edge, Modifier.weight(1f))
        QueueArrow(queued, Modifier.width(40.dp))
        StoreTile(cloud, Modifier.weight(1f))
    }
}

@Composable
private fun StoreTile(tile: StoreTileUi, modifier: Modifier = Modifier) {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    val tone = WingmanTheme.colors.of(tile.tone)
    WingmanCard(modifier, shape = TileShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(tile.title, style = typography.label, color = colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 2.dp)) {
            Text(tile.version, Modifier.alignByBaseline(), style = typography.data.copy(fontSize = 20.sp), color = colors.text, maxLines = 1)
            Spacer(Modifier.width(WingmanSpacing.grid))
            Text(
                text = tile.objects,
                modifier = Modifier.alignByBaseline(),
                style = typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.padding(top = WingmanSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(tone)
            Spacer(Modifier.width(5.dp))
            Text(tile.status, style = typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold), color = tone, maxLines = 1)
        }
    }
}

@Composable
private fun QueueArrow(queued: Int, modifier: Modifier = Modifier) {
    val color = if (queued > 0) WingmanTheme.colors.warn else WingmanTheme.colors.ok
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(queued.toString(), style = WingmanTheme.typography.tag.copy(fontSize = 11.sp), color = color, maxLines = 1)
        Spacer(Modifier.height(WingmanSpacing.xxs))
        Spacer(Modifier.size(24.dp, 12.dp).dashedArrow(color))
    }
}

private fun Modifier.dashedArrow(color: Color): Modifier = drawWithCache {
    val strokePx = 1.5.dp.toPx()
    val headPx = 5.dp.toPx()
    val midY = size.height / 2
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    val headStroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val head = Path().apply {
        moveTo(size.width - headPx, midY - headPx)
        lineTo(size.width - strokePx / 2, midY)
        lineTo(size.width - headPx, midY + headPx)
    }
    onDrawBehind {
        drawLine(color, Offset(0f, midY), Offset(size.width - strokePx, midY), strokePx, pathEffect = dash)
        drawPath(head, color, style = headStroke)
    }
}

private enum class CardSegment { Top, Middle, Bottom }

private fun Modifier.cardSegment(segment: CardSegment, container: Color, border: Color): Modifier = drawWithCache {
    val strokePx = 1.dp.toPx()
    val radius = CornerRadius(CardRadius.toPx())
    val overflow = 2 * radius.x + strokePx
    val top = if (segment == CardSegment.Top) strokePx / 2 else -overflow
    val bottom = if (segment == CardSegment.Bottom) size.height - strokePx / 2 else size.height + overflow
    val topLeft = Offset(strokePx / 2, top)
    val rectSize = Size(size.width - strokePx, bottom - top)
    val stroke = Stroke(strokePx)
    onDrawBehind {
        clipRect {
            drawRoundRect(container, topLeft, rectSize, radius)
            drawRoundRect(border, topLeft, rectSize, radius, style = stroke)
        }
    }
}

private fun Modifier.outboxSegment(segment: CardSegment, colors: WingmanColors): Modifier =
    cardSegment(segment, colors.surface, colors.line)

@Composable
internal fun OutboxHeader(summary: String, modifier: Modifier = Modifier) {
    SectionHeader(
        title = "Outbox",
        meta = summary,
        modifier = modifier
            .outboxSegment(CardSegment.Top, WingmanTheme.colors)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = WingmanSpacing.grid),
    )
}

@Composable
internal fun OutboxEmpty(modifier: Modifier = Modifier) {
    Text(
        text = "Outbox drained · edge and mirror converged",
        modifier = modifier
            .fillMaxWidth()
            .outboxSegment(CardSegment.Middle, WingmanTheme.colors)
            .padding(horizontal = 16.dp, vertical = WingmanSpacing.sm),
        style = WingmanTheme.typography.bodySmall,
        color = WingmanTheme.colors.muted,
    )
}

@Composable
internal fun OutboxFooter(hiddenCount: Int, modifier: Modifier = Modifier) {
    if (hiddenCount == 0) {
        Spacer(modifier.fillMaxWidth().height(CardRadius).outboxSegment(CardSegment.Bottom, WingmanTheme.colors))
        return
    }
    Text(
        text = "+$hiddenCount more queued",
        modifier = modifier
            .fillMaxWidth()
            .outboxSegment(CardSegment.Bottom, WingmanTheme.colors)
            .padding(start = 56.dp, end = 16.dp, top = WingmanSpacing.xxs, bottom = CardRadius - WingmanSpacing.xxs),
        style = WingmanTheme.typography.bodySmall,
        color = WingmanTheme.colors.muted,
    )
}

@Composable
internal fun OutboxRow(row: OutboxRowUi, showDivider: Boolean, onResolve: (Long, ConflictResolution) -> Unit, modifier: Modifier = Modifier) {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    Column(modifier.fillMaxWidth().outboxSegment(CardSegment.Middle, colors).padding(horizontal = 16.dp)) {
        if (showDivider) Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.idLabel,
                modifier = Modifier.width(40.dp).align(Alignment.Top).padding(top = 2.dp),
                style = typography.dataSmall,
                color = colors.muted,
                maxLines = 1,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.title,
                    style = typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.settled) colors.text2 else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.detail, style = typography.chipMono, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(WingmanSpacing.grid))
            TintChip(row.statusLabel, WingmanTheme.colors.of(row.tone), height = 24.dp, textStyle = typography.chip.copy(fontSize = 11.sp))
            if (row.resolvable) ResolveAction(row.id, onResolve)
        }
    }
}

@Composable
private fun ResolveAction(eventId: Long, onResolve: (Long, ConflictResolution) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = WingmanSpacing.xs)) {
        Text(
            text = "Resolve",
            modifier = Modifier
                .clip(WingmanShapes.pill)
                .clickable(role = Role.Button, onClickLabel = "Resolve conflict") { expanded = true }
                .padding(horizontal = WingmanSpacing.xs, vertical = WingmanSpacing.xs),
            style = WingmanTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WingmanTheme.colors.primary,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = WingmanShapes.small,
            containerColor = WingmanTheme.colors.surface2,
        ) {
            ConflictResolution.entries.forEach { resolution ->
                ResolutionItem(resolution) {
                    expanded = false
                    onResolve(eventId, resolution)
                }
            }
        }
    }
}

@Composable
private fun ResolutionItem(resolution: ConflictResolution, onClick: () -> Unit) {
    val colors = WingmanTheme.colors
    DropdownMenuItem(
        text = {
            Column {
                Text(resolution.label, style = WingmanTheme.typography.control, color = colors.text)
                Text(resolution.hint, style = WingmanTheme.typography.caption, color = colors.muted)
            }
        },
        leadingIcon = { Icon(resolution.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

private val ConflictResolution.label: String
    get() = when (this) {
        ConflictResolution.KEEP_EDGE -> "Keep edge"
        ConflictResolution.TAKE_CLOUD -> "Take cloud"
    }

private val ConflictResolution.hint: String
    get() = when (this) {
        ConflictResolution.KEEP_EDGE -> "Push the on-device version"
        ConflictResolution.TAKE_CLOUD -> "Overwrite with the mirror version"
    }

private val ConflictResolution.icon: ImageVector
    get() = when (this) {
        ConflictResolution.KEEP_EDGE -> WingmanIcons.Database
        ConflictResolution.TAKE_CLOUD -> WingmanIcons.Sync
    }

@Composable
internal fun ScenarioInjectsCard(onInject: (ScenarioInject) -> Unit, modifier: Modifier = Modifier) {
    WingmanCard(modifier.fillMaxWidth()) {
        SectionHeader("Scenario injects", meta = "Demo controls")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(WingmanSpacing.grid)) {
            InjectRows.forEach { injects ->
                Row(horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid)) {
                    injects.forEach { inject -> InjectButton(inject, onInject, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun InjectButton(inject: ScenarioInject, onInject: (ScenarioInject) -> Unit, modifier: Modifier = Modifier) {
    val accent = inject.accent()
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(WingmanShapes.small)
            .background(WingmanTheme.colors.surface3)
            .clickable(role = Role.Button) { onInject(inject) }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(inject.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = inject.label,
            style = WingmanTheme.typography.control,
            color = if (inject == ScenarioInject.RESTORE_LINK) accent else WingmanTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ScenarioInject.label: String
    get() = when (this) {
        ScenarioInject.POPUP_RF_THREAT -> "Pop-up RF threat"
        ScenarioInject.CONFLICTING_REPORT -> "Conflicting report"
        ScenarioInject.SENSOR_BURST -> "Sensor burst ×25"
        ScenarioInject.RESTORE_LINK -> "Restore link"
    }

private val ScenarioInject.icon: ImageVector
    get() = when (this) {
        ScenarioInject.POPUP_RF_THREAT -> WingmanIcons.Sensors
        ScenarioInject.CONFLICTING_REPORT -> WingmanIcons.Warning
        ScenarioInject.SENSOR_BURST -> WingmanIcons.SatelliteAlt
        ScenarioInject.RESTORE_LINK -> WingmanIcons.Sync
    }

@Composable
@ReadOnlyComposable
private fun ScenarioInject.accent(): Color = when (this) {
    ScenarioInject.POPUP_RF_THREAT -> WingmanTheme.colors.hostile
    ScenarioInject.CONFLICTING_REPORT -> WingmanTheme.colors.unknown
    ScenarioInject.SENSOR_BURST -> WingmanTheme.colors.friend
    ScenarioInject.RESTORE_LINK -> WingmanTheme.colors.ok
}

