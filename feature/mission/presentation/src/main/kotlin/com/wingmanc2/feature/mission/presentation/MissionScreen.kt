package com.wingmanc2.feature.mission.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wingmanc2.core.designsystem.component.ButtonVariant
import com.wingmanc2.core.designsystem.component.ChipLeading
import com.wingmanc2.core.designsystem.component.MapTag
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.component.WingmanButton
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.WingmanScreen
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.core.ui.map.TacticalMap

@Composable
internal fun MissionScreen(viewModel: MissionViewModel, contentPadding: PaddingValues, onOpenPlanner: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry = viewModel.telemetry.collectAsStateWithLifecycle()
    val clock = viewModel.clock.collectAsStateWithLifecycle()
    val liveAsset = viewModel.liveAsset.collectAsStateWithLifecycle()
    WingmanScreen(title = "Mission", subtitle = state.subtitle) {
        when (val current = state) {
            MissionUiState.Loading -> Unit
            MissionUiState.NoMission -> EmptyMission(
                onOpenPlanner = onOpenPlanner,
                modifier = Modifier.weight(1f).padding(bottom = contentPadding.calculateBottomPadding()),
            )
            is MissionUiState.Active -> ActiveMission(
                state = current,
                telemetry = { telemetry.value },
                clock = { clock.value },
                liveAsset = { liveAsset.value },
                contentPadding = contentPadding,
                onToggleHold = viewModel::toggleHold,
                onReplan = { viewModel.replan(onOpenPlanner) },
                onReturnToBase = viewModel::returnToBase,
            )
        }
    }
}

@Composable
private fun ColumnScope.ActiveMission(
    state: MissionUiState.Active,
    telemetry: () -> TelemetryUi?,
    clock: () -> String?,
    liveAsset: () -> MapLiveAsset?,
    contentPadding: PaddingValues,
    onToggleHold: () -> Unit,
    onReplan: () -> Unit,
    onReturnToBase: () -> Unit,
) {
    MissionMap(state, liveAsset, clock, Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp))
    Column(
        modifier = Modifier.padding(
            start = WingmanSpacing.margin,
            end = WingmanSpacing.margin,
            top = WingmanSpacing.sm,
            bottom = WingmanSpacing.margin + contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(WingmanSpacing.sm),
    ) {
        TelemetryStrip(telemetry)
        DecisionTimelineCard(state.decisions, state.decisionsMeta)
        OverrideRow(state.holdLabel, state.canCommand, onToggleHold, onReplan, onReturnToBase)
    }
}

@Composable
private fun MissionMap(state: MissionUiState.Active, liveAsset: () -> MapLiveAsset?, clock: () -> String?, modifier: Modifier) {
    TacticalMap(
        layers = state.layers,
        modifier = modifier,
        framing = state.framing,
        liveAsset = liveAsset,
        showScaleBar = false,
    ) {
        MissionStatusChip(state.status, state.statusTone, Modifier.align(Alignment.TopStart).padding(WingmanSpacing.sm))
        MissionClock(clock, Modifier.align(Alignment.TopEnd).padding(WingmanSpacing.sm))
    }
}

@Composable
private fun MissionStatusChip(status: String, tone: Tone, modifier: Modifier) {
    val color = tone.color()
    val scrim = WingmanTheme.colors.scrim
    val container = remember(color, scrim) { color.copy(alpha = 0.12f).compositeOver(scrim) }
    TintChip(text = status, color = color, modifier = modifier, leading = ChipLeading.Dot, height = 26.dp, containerColor = container)
}

@Composable
private fun MissionClock(clock: () -> String?, modifier: Modifier) {
    val text = clock() ?: return
    MapTag(text, WingmanTheme.colors.text, modifier)
}

@Composable
private fun OverrideRow(
    holdLabel: String,
    canCommand: Boolean,
    onToggleHold: () -> Unit,
    onReplan: () -> Unit,
    onReturnToBase: () -> Unit,
) {
    val typography = WingmanTheme.typography
    val style = remember(typography) { typography.button.copy(fontSize = 13.5.sp) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid)) {
        WingmanButton(holdLabel, onToggleHold, Modifier.weight(1f), ButtonVariant.Tonal, enabled = canCommand, height = 48.dp, textStyle = style)
        WingmanButton("Replan", onReplan, Modifier.weight(0.8f), ButtonVariant.Tonal, height = 48.dp, textStyle = style)
        WingmanButton("Return to base", onReturnToBase, Modifier.weight(1.15f), ButtonVariant.Danger, enabled = canCommand, height = 48.dp, textStyle = style)
    }
}

@Composable
private fun EmptyMission(onOpenPlanner: () -> Unit, modifier: Modifier) {
    val colors = WingmanTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = WingmanSpacing.margin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(WingmanIcons.Drone, contentDescription = null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(WingmanSpacing.sm))
        Text("No active mission", style = WingmanTheme.typography.headline, color = colors.text)
        Spacer(Modifier.height(WingmanSpacing.xs))
        Text(
            text = "Approve a course of action in Plan to put a wingman on task.",
            style = WingmanTheme.typography.body,
            color = colors.text2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(WingmanSpacing.lg))
        WingmanButton("Open planner", onOpenPlanner, icon = WingmanIcons.Route)
    }
}

@Composable
internal fun Tone.color(): Color {
    val colors = WingmanTheme.colors
    return when (this) {
        Tone.Ok -> colors.ok
        Tone.Warn -> colors.warn
        Tone.Critical -> colors.critical
        Tone.Ai -> colors.ai
        Tone.Friend -> colors.friend
        Tone.Muted -> colors.muted
    }
}
