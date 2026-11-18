package com.wingmanc2.feature.sync.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.ui.StatusTone
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class StoreTileUi(
    val title: String,
    val version: String,
    val objects: String,
    val status: String,
    val tone: StatusTone,
)

@Immutable
internal data class OutboxRowUi(
    val id: Long,
    val idLabel: String,
    val title: String,
    val detail: String,
    val statusLabel: String,
    val tone: StatusTone,
    val settled: Boolean,
    val resolvable: Boolean,
)

@Immutable
internal sealed interface SyncUiState {
    data object Loading : SyncUiState

    data class Ready(
        val linkMode: LinkMode,
        val linkStats: String,
        val edge: StoreTileUi,
        val cloud: StoreTileUi,
        val queued: Int,
        val outboxSummary: String,
        val outbox: ImmutableList<OutboxRowUi>,
        val hiddenCount: Int,
        val userMessage: String?,
    ) : SyncUiState
}
