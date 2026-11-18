package com.wingmanc2.feature.intel.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.model.EntityGraph
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class IntelUiState(
    val latency: String = "",
    val predicates: ImmutableList<String> = persistentListOf(),
    val graphMeta: String = "",
    val graph: EntityGraph? = null,
    val lineage: LineageUi? = null,
    val focusId: Long? = null,
)

@Immutable
internal data class LineageUi(
    val entityKey: String,
    val rows: ImmutableList<LineageRow>,
    val fusedTitle: String,
    val fusedDetail: String,
)

@Immutable
internal data class LineageRow(val id: Long, val format: String, val payload: String)
