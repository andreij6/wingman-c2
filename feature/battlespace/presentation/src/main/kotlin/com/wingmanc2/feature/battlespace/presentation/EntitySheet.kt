package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.component.ButtonVariant
import com.wingmanc2.core.designsystem.component.KeyValue
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.component.WingmanButton
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.AffiliationSymbol
import com.wingmanc2.core.ui.entityColor

@Composable
internal fun EntitySheet(
    sheet: EntitySheetUi,
    onDismiss: () -> Unit,
    onViewLineage: () -> Unit,
    onPlanRecon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WingmanTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, WingmanShapes.sheet)
            .border(1.dp, colors.line, WingmanShapes.sheet)
            .draggable(
                state = rememberDraggableState {},
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> if (velocity > 0f) onDismiss() },
            )
            .padding(horizontal = WingmanSpacing.margin)
            .padding(bottom = WingmanSpacing.margin),
    ) {
        DragHandle(onDismiss)
        SheetHeader(sheet)
        SheetFacts(sheet, Modifier.padding(top = WingmanSpacing.margin))
        if (sheet.fusedFrom != null) {
            Text(
                text = sheet.fusedFrom,
                modifier = Modifier.padding(top = WingmanSpacing.sm),
                style = WingmanTheme.typography.label,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.padding(top = WingmanSpacing.margin),
            horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.margin),
        ) {
            WingmanButton("View lineage", onViewLineage, Modifier.weight(1f), variant = ButtonVariant.Outlined)
            WingmanButton("Plan recon", onPlanRecon, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DragHandle(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WingmanSpacing.lg)
            .clickable(role = Role.Button, onClickLabel = "Close details", onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = WingmanSpacing.grid)
                .size(width = 32.dp, height = 4.dp)
                .background(WingmanTheme.colors.muted, HandleShape),
        )
    }
}

@Composable
private fun SheetHeader(sheet: EntitySheetUi) {
    val colors = WingmanTheme.colors
    val accent = colors.entityColor(sheet.kind, sheet.affiliation)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.14f), WingmanShapes.small),
            contentAlignment = Alignment.Center,
        ) {
            AffiliationSymbol(sheet.affiliation, kind = sheet.kind, isAir = sheet.isAir, size = 13.dp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = WingmanSpacing.sm),
        ) {
            Text(sheet.key, style = WingmanTheme.typography.headline, color = colors.text, maxLines = 1)
            Text(
                text = sheet.subtitle,
                style = WingmanTheme.typography.bodySmall,
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TintChip(sheet.confidence, if (sheet.verified) colors.ok else colors.warn)
    }
}

@Composable
private fun SheetFacts(sheet: EntitySheetUi, modifier: Modifier) {
    Row(modifier.fillMaxWidth()) {
        KeyValue("Freq", sheet.frequency, Modifier.weight(1f))
        KeyValue("Bearing", sheet.bearing, Modifier.weight(1f))
        KeyValue("Last seen", sheet.lastSeen, Modifier.weight(1f))
    }
}

private val HandleShape = RoundedCornerShape(2.dp)
