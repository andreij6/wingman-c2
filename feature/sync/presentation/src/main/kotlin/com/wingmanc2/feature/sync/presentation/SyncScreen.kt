package com.wingmanc2.feature.sync.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.ScenarioInject
import com.wingmanc2.core.ui.UserMessageHost
import com.wingmanc2.core.ui.WingmanScreen

@Composable
internal fun SyncScreen(contentPadding: PaddingValues, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncScreen(
        state = state,
        contentPadding = contentPadding,
        onSelectLinkMode = viewModel::onLinkModeSelected,
        onResolve = viewModel::onResolve,
        onInject = viewModel::onInject,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
private fun SyncScreen(
    state: SyncUiState,
    contentPadding: PaddingValues,
    onSelectLinkMode: (LinkMode) -> Unit,
    onResolve: (Long, ConflictResolution) -> Unit,
    onInject: (ScenarioInject) -> Unit,
    onMessageShown: () -> Unit,
) {
    WingmanScreen(title = "Sync", subtitle = "Edge store ↔ cloud mirror", bannerGap = 0.dp) {
        when (state) {
            SyncUiState.Loading -> Unit
            is SyncUiState.Ready -> Box(Modifier.weight(1f)) {
                SyncContent(state, contentPadding, onSelectLinkMode, onResolve, onInject)
                UserMessageHost(
                    message = state.userMessage,
                    onShown = onMessageShown,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
                )
            }
        }
    }
}

@Composable
private fun SyncContent(
    state: SyncUiState.Ready,
    contentPadding: PaddingValues,
    onSelectLinkMode: (LinkMode) -> Unit,
    onResolve: (Long, ConflictResolution) -> Unit,
    onInject: (ScenarioInject) -> Unit,
) {
    val gap = WingmanSpacing.sm
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WingmanSpacing.margin,
            end = WingmanSpacing.margin,
            top = contentPadding.calculateTopPadding() + gap,
            bottom = contentPadding.calculateBottomPadding() + WingmanSpacing.margin,
        ),
    ) {
        item(key = "link", contentType = "link") {
            LinkConditionCard(state.linkMode, state.linkStats, onSelectLinkMode)
        }
        item(key = "stores", contentType = "stores") {
            StoreTiles(state.edge, state.cloud, state.queued, Modifier.padding(top = gap))
        }
        item(key = "outbox-header", contentType = "outbox-header") {
            OutboxHeader(state.outboxSummary, Modifier.padding(top = gap))
        }
        if (state.outbox.isEmpty()) {
            item(key = "outbox-empty", contentType = "outbox-empty") { OutboxEmpty() }
        }
        itemsIndexed(state.outbox, key = { _, row -> row.id }, contentType = { _, _ -> "outbox-row" }) { index, row ->
            OutboxRow(row, showDivider = index > 0, onResolve = onResolve)
        }
        item(key = "outbox-footer", contentType = "outbox-footer") { OutboxFooter(state.hiddenCount) }
        item(key = "injects", contentType = "injects") {
            ScenarioInjectsCard(onInject, Modifier.padding(top = gap))
        }
    }
}

