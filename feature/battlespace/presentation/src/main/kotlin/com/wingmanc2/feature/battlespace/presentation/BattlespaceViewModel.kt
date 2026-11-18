package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.ui.map.MapBounds
import com.wingmanc2.core.ui.map.MapFraming
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.feature.battlespace.domain.AffiliationFilter
import com.wingmanc2.feature.battlespace.domain.NovaResult
import com.wingmanc2.feature.battlespace.domain.ObserveBattlespaceUseCase
import com.wingmanc2.feature.battlespace.domain.ObserveEntityDetailUseCase
import com.wingmanc2.feature.battlespace.domain.ObserveLiveAssetUseCase
import com.wingmanc2.feature.battlespace.domain.RunNovaQueryUseCase
import com.wingmanc2.feature.battlespace.domain.SelectEntityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BattlespaceViewModel @Inject constructor(
    observeBattlespace: ObserveBattlespaceUseCase,
    observeEntityDetail: ObserveEntityDetailUseCase,
    observeLiveAsset: ObserveLiveAssetUseCase,
    private val runNovaQuery: RunNovaQueryUseCase,
    private val selectEntity: SelectEntityUseCase,
    @Dispatcher(WingmanDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val query = TextFieldState()

    private val filter = MutableStateFlow(AffiliationFilter.ALL)
    private val nova = MutableStateFlow<NovaResult?>(null)
    private val overlaysVisible = MutableStateFlow(true)
    private val framing = MutableStateFlow(MapFraming.AreaOfOperations)
    private var queryJob: Job? = null

    val liveAsset: StateFlow<MapLiveAsset?> = observeLiveAsset()
        .map { it.toMapLiveAsset() }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val liveAssetId = liveAsset.map { it?.entityId }.distinctUntilChanged()

    private val sheet = observeEntityDetail().map { it?.toSheet() }

    private val picture = observeBattlespace(filter, nova)

    val uiState: StateFlow<BattlespaceUiState> = combine(picture, liveAssetId, overlaysVisible, framing, sheet) { picture, liveId, overlays, currentFraming, currentSheet ->
        BattlespaceUiState(
            trackCount = picture.counts.total,
            filters = picture.toFilterChips(),
            layers = picture.toLayers(liveId, overlays),
            framing = currentFraming,
            novaMatches = if (picture.novaActive) picture.tracks.size else null,
            sheet = currentSheet,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BattlespaceUiState())

    fun onFilterSelected(selected: AffiliationFilter) {
        filter.value = selected
    }

    fun onEntitySelected(entityId: Long) {
        selectEntity(entityId)
    }

    fun onSheetDismissed() {
        selectEntity(null)
    }

    fun onQuerySubmitted() {
        val raw = query.text.toString()
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            nova.value = try {
                runNovaQuery(raw)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                null
            }
        }
    }

    fun onQueryCleared() {
        queryJob?.cancel()
        query.clearText()
        nova.value = null
    }

    fun onLayersToggled() {
        overlaysVisible.update { !it }
    }

    fun onRecenter() {
        val anchor = liveAsset.value?.position ?: uiState.value.layers.symbols.firstOrNull { it.selected }?.position
        framing.update { current ->
            if (current != MapFraming.AreaOfOperations || anchor == null) MapFraming.AreaOfOperations else anchor.framing()
        }
    }

    private fun LocalPoint.framing(): MapFraming =
        MapFraming.Fit(MapBounds(x - RECENTER_HALF_SPAN_M, y - RECENTER_HALF_SPAN_M, x + RECENTER_HALF_SPAN_M, y + RECENTER_HALF_SPAN_M), paddingDp = 0f, cover = true)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENTER_HALF_SPAN_M = 900.0
    }
}
