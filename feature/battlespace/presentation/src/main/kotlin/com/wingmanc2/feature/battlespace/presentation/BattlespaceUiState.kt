package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.ui.map.MapFraming
import com.wingmanc2.core.ui.map.MapLayers
import com.wingmanc2.feature.battlespace.domain.AffiliationFilter
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal sealed interface BattlespaceUiState {
    data object Loading : BattlespaceUiState

    data class Ready(
        val trackCount: Int,
        val filters: ImmutableList<FilterChipUi>,
        val layers: MapLayers,
        val framing: MapFraming,
        val novaMatches: Int?,
        val sheet: EntitySheetUi?,
        val userMessage: String?,
    ) : BattlespaceUiState
}

@Immutable
internal data class FilterChipUi(
    val filter: AffiliationFilter,
    val label: String,
    val selected: Boolean,
)

@Immutable
internal data class EntitySheetUi(
    val id: Long,
    val key: String,
    val subtitle: String,
    val affiliation: Affiliation,
    val kind: EntityKind,
    val isAir: Boolean,
    val confidence: String,
    val verified: Boolean,
    val frequency: String,
    val bearing: String,
    val lastSeen: String,
    val fusedFrom: String?,
    val coordinate: String,
)
