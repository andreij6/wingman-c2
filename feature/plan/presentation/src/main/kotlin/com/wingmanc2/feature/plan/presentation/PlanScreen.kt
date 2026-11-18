package com.wingmanc2.feature.plan.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wingmanc2.core.designsystem.component.SectionHeader
import com.wingmanc2.core.designsystem.component.WingmanButton
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.UserMessageHost
import com.wingmanc2.core.ui.WingmanScreen

@Composable
internal fun PlanScreen(
    contentPadding: PaddingValues,
    onDispatched: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? PlanUiState.Ready
    LaunchedEffect(ready?.dispatched) {
        if (ready?.dispatched == true) {
            onDispatched()
            viewModel.onDispatchHandled()
        }
    }
    WingmanScreen(title = "Plan", subtitle = ready?.let { "SMARTS · recon ${it.targetKey}" } ?: "SMARTS") {
        when (val current = state) {
            PlanUiState.Loading -> Unit
            is PlanUiState.Ready -> PlanContent(
                state = current,
                intentDraft = viewModel.intentDraft,
                contentPadding = contentPadding,
                onEditIntent = viewModel::editIntent,
                onApplyIntent = viewModel::applyIntent,
                onSelect = viewModel::select,
                onApprove = viewModel::approve,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    }
}

@Composable
private fun ColumnScope.PlanContent(
    state: PlanUiState.Ready,
    intentDraft: TextFieldState,
    contentPadding: PaddingValues,
    onEditIntent: (String) -> Unit,
    onApplyIntent: () -> Unit,
    onSelect: (String) -> Unit,
    onApprove: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val resultAlpha by animateFloatAsState(if (state.planning) 0.55f else 1f, label = "planning")
    val dimmed = Modifier.graphicsLayer { alpha = resultAlpha }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = WingmanSpacing.margin,
                end = WingmanSpacing.margin,
                top = WingmanSpacing.xxs,
                bottom = contentPadding.calculateBottomPadding() + WingmanSpacing.margin,
            ),
            verticalArrangement = Arrangement.spacedBy(WingmanSpacing.grid),
        ) {
            item(key = "intent", contentType = "intent") { IntentCard(state.intent, intentDraft, onEditIntent, onApplyIntent) }
            item(key = "preview", contentType = "preview") {
                RoutePreviewPanel(state.preview, state.selectedCode, dimmed.padding(top = WingmanSpacing.xxs))
            }
            item(key = "header", contentType = "header") {
                SectionHeader(
                    title = "Courses of action",
                    meta = if (state.planning) "Planning…" else "Ranked by intent match",
                    metaColor = if (state.planning) WingmanTheme.colors.primary else WingmanTheme.colors.muted,
                    modifier = Modifier.padding(top = WingmanSpacing.xs),
                )
            }
            courses(state, onSelect, dimmed)
            item(key = "approve", contentType = "approve") { ApproveButton(state, onApprove) }
        }
        UserMessageHost(
            message = state.userMessage,
            onShown = onMessageShown,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

private fun LazyListScope.courses(state: PlanUiState.Ready, onSelect: (String) -> Unit, modifier: Modifier) {
    items(state.courses, key = { it.code }, contentType = { if (it.code == state.selectedCode) "expanded" else "collapsed" }) { coa ->
        if (coa.code == state.selectedCode) {
            ExpandedCoaCard(coa, modifier.animateItem())
        } else {
            CollapsedCoaRow(coa, onSelect, modifier.animateItem())
        }
    }
}

@Composable
private fun ApproveButton(state: PlanUiState.Ready, onApprove: () -> Unit) {
    val code = state.selectedCode
    val text = when {
        code == null -> "Approve & dispatch"
        state.dispatching -> "Dispatching $code…"
        else -> "Approve & dispatch $code"
    }
    WingmanButton(
        text = text,
        onClick = onApprove,
        enabled = state.canDispatch,
        height = 52.dp,
        textStyle = WingmanTheme.typography.button.copy(fontSize = 15.sp),
        modifier = Modifier.fillMaxWidth().padding(top = WingmanSpacing.grid),
    )
}

