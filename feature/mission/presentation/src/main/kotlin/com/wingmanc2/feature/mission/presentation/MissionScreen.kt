package com.wingmanc2.feature.mission.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wingmanc2.core.designsystem.component.ButtonVariant
import com.wingmanc2.core.designsystem.component.ChipLeading
import com.wingmanc2.core.designsystem.component.MapTag
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.component.WingmanButton
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.StatusTone
import com.wingmanc2.core.ui.UserMessageHost
import com.wingmanc2.core.ui.WingmanScreen
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.core.ui.map.TacticalMap
import com.wingmanc2.core.ui.of

@Composable
internal fun MissionScreen(
    contentPadding: PaddingValues,
    onOpenPlanner: () -> Unit,
    viewModel: MissionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val live = viewModel.live.collectAsStateWithLifecycle()
    WingmanScreen(title = "Mission", subtitle = state.subtitle, bannerGap = 0.dp) {
        when (val current = state) {
            MissionUiState.Loading -> Unit
            MissionUiState.NoMission -> EmptyMission(
                onOpenPlanner = onOpenPlanner,
                modifier = Modifier.weight(1f).padding(bottom = contentPadding.calculateBottomPadding()),
            )
            is MissionUiState.Active -> ActiveMission(
                state = current,
                live = live,
                contentPadding = contentPadding,
                onToggleHold = viewModel::toggleHold,
                onReplan = {
                    viewModel.replan()
                    onOpenPlanner()
                },
                onReturnToBase = viewModel::returnToBase,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    }
}

@Composable
private fun ColumnScope.ActiveMission(
    state: MissionUiState.Active,
    live: State<MissionLiveUi?>,
    contentPadding: PaddingValues,
    onToggleHold: () -> Unit,
    onReplan: () -> Unit,
    onReturnToBase: () -> Unit,
    onMessageShown: () -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val panelMaxHeight = (maxHeight - MapMinHeight).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize()) {
            MissionMap(state, { live.value?.asset }, { live.value?.clock }, Modifier.fillMaxWidth().weight(1f))
            Column(
                modifier = Modifier
                    .heightIn(max = panelMaxHeight)
                    .padding(
                        start = WingmanSpacing.margin,
                        end = WingmanSpacing.margin,
                        top = WingmanSpacing.sm,
                        bottom = WingmanSpacing.margin + contentPadding.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.spacedBy(WingmanSpacing.sm),
            ) {
                TelemetryStrip({ live.value?.telemetry })
                DecisionTimelineCard(state.decisions, state.decisionsMeta, Modifier.weight(1f, fill = false))
                OverrideRow(state.holdLabel, state.canCommand, onToggleHold, onReplan, onReturnToBase)
            }
        }
        UserMessageHost(
            message = state.userMessage,
            onShown = onMessageShown,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
        )
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
        reservedTop = MapChromeHeight,
    ) {
        MissionStatusChip(state.status, state.statusTone, Modifier.align(Alignment.TopStart).padding(WingmanSpacing.sm))
        MissionClock(clock, Modifier.align(Alignment.TopEnd).padding(WingmanSpacing.sm))
    }
}

@Composable
private fun MissionStatusChip(status: String, tone: StatusTone, modifier: Modifier) {
    val color = WingmanTheme.colors.of(tone)
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

private val MapMinHeight = 160.dp
private val MapChromeHeight = 56.dp

