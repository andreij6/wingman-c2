package com.wingmanc2.feature.intel.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.model.EntityGraph
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal sealed interface IntelUiState {
    data object Loading : IntelUiState

    data class Ready(
        val latency: String,
        val predicates: ImmutableList<String>,
        val graphMeta: String,
        val graph: EntityGraph?,
        val lineage: LineageUi?,
        val focusId: Long?,
    ) : IntelUiState
}

@Immutable
internal data class LineageUi(
    val entityKey: String,
    val rows: ImmutableList<LineageRow>,
    val fusedTitle: String,
    val fusedDetail: String,
)

@Immutable
internal data class LineageRow(val id: Long, val format: String, val payload: String)
