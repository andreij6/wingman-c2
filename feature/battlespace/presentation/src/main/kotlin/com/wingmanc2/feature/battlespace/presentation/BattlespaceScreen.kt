package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wingmanc2.core.designsystem.component.AffiliationFilterChip
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.WingmanScreen
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.core.ui.map.TacticalMap
import com.wingmanc2.feature.battlespace.domain.AffiliationFilter
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun BattlespaceScreen(
    contentPadding: PaddingValues,
    onViewLineage: () -> Unit,
    onPlanRecon: () -> Unit,
    viewModel: BattlespaceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val liveAsset = viewModel.liveAsset.collectAsStateWithLifecycle()
    BattlespaceContent(
        state = state,
        query = viewModel.query,
        liveAsset = liveAsset,
        contentPadding = contentPadding,
        actions = remember(viewModel, onViewLineage, onPlanRecon) {
            BattlespaceActions(
                onQuerySubmit = viewModel::onQuerySubmitted,
                onQueryClear = viewModel::onQueryCleared,
                onFilterSelect = viewModel::onFilterSelected,
                onSymbolTap = viewModel::onEntitySelected,
                onSheetDismiss = viewModel::onSheetDismissed,
                onLayersClick = viewModel::onLayersToggled,
                onRecenterClick = viewModel::onRecenter,
                onViewLineage = { id ->
                    viewModel.onEntitySelected(id)
                    onViewLineage()
                },
                onPlanRecon = { id ->
                    viewModel.onEntitySelected(id)
                    onPlanRecon()
                },
            )
        },
    )
}

@Immutable
internal class BattlespaceActions(
    val onQuerySubmit: () -> Unit,
    val onQueryClear: () -> Unit,
    val onFilterSelect: (AffiliationFilter) -> Unit,
    val onSymbolTap: (Long) -> Unit,
    val onSheetDismiss: () -> Unit,
    val onLayersClick: () -> Unit,
    val onRecenterClick: () -> Unit,
    val onViewLineage: (Long) -> Unit,
    val onPlanRecon: (Long) -> Unit,
)

@Composable
private fun BattlespaceContent(
    state: BattlespaceUiState,
    query: TextFieldState,
    liveAsset: State<MapLiveAsset?>,
    contentPadding: PaddingValues,
    actions: BattlespaceActions,
) {
    WingmanScreen(
        title = "Battlespace",
        subtitle = "Sector 4 · ${state.trackCount} tracks · offline tiles",
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        NovaSearchBar(
            query = query,
            matches = state.novaMatches,
            onSubmit = actions.onQuerySubmit,
            onClear = actions.onQueryClear,
            modifier = Modifier.padding(horizontal = WingmanSpacing.margin),
        )
        FilterChips(
            chips = state.filters,
            onSelect = actions.onFilterSelect,
            modifier = Modifier.padding(vertical = WingmanSpacing.sm),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TacticalMap(
                layers = state.layers,
                modifier = Modifier.fillMaxSize(),
                framing = state.framing,
                liveAsset = { liveAsset.value },
                onSymbolTap = actions.onSymbolTap,
                coordinateLabel = state.sheet?.coordinate,
                onLayersClick = actions.onLayersClick,
                onRecenterClick = actions.onRecenterClick,
            )
            EntitySheetHost(state.sheet, actions, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun FilterChips(
    chips: ImmutableList<FilterChipUi>,
    onSelect: (AffiliationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WingmanTheme.colors
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = WingmanSpacing.margin),
        horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid),
    ) {
        items(chips, key = { it.filter }) { chip ->
            AffiliationFilterChip(
                label = chip.label,
                color = colors.filterColor(chip.filter),
                selected = chip.selected,
                onClick = { onSelect(chip.filter) },
            )
        }
    }
}

private fun WingmanColors.filterColor(filter: AffiliationFilter): Color =
    filter.affiliation?.let(::affiliation) ?: primary

@Composable
private fun EntitySheetHost(sheet: EntitySheetUi?, actions: BattlespaceActions, modifier: Modifier) {
    val last = remember { LastValue<EntitySheetUi>() }
    if (sheet != null) last.value = sheet
    AnimatedVisibility(
        visible = sheet != null,
        modifier = modifier,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        last.value?.let { shown ->
            EntitySheet(
                sheet = shown,
                onDismiss = actions.onSheetDismiss,
                onViewLineage = { actions.onViewLineage(shown.id) },
                onPlanRecon = { actions.onPlanRecon(shown.id) },
            )
        }
    }
}

private class LastValue<T : Any> {
    var value: T? = null
}
