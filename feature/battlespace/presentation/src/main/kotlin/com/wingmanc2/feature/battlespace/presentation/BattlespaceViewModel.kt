package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.FocusEntityUseCase
import com.wingmanc2.core.domain.QueryOntologyUseCase
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.ui.WhileUiSubscribed
import com.wingmanc2.core.ui.launchAction
import com.wingmanc2.core.ui.map.MapFraming
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.feature.battlespace.domain.AffiliationFilter
import com.wingmanc2.feature.battlespace.domain.NovaResult
import com.wingmanc2.feature.battlespace.domain.ObserveBattlespaceUseCase
import com.wingmanc2.feature.battlespace.domain.ObserveEntityDetailUseCase
import com.wingmanc2.feature.battlespace.domain.ObserveLiveAssetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class BattlespaceViewModel @Inject constructor(
    observeBattlespace: ObserveBattlespaceUseCase,
    observeEntityDetail: ObserveEntityDetailUseCase,
    observeLiveAsset: ObserveLiveAssetUseCase,
    private val queryOntology: QueryOntologyUseCase,
    private val focusEntity: FocusEntityUseCase,
    @Dispatcher(WingmanDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val query = TextFieldState()

    private val filter = MutableStateFlow(AffiliationFilter.ALL)
    private val nova = MutableStateFlow<NovaResult?>(null)
    private val overlaysVisible = MutableStateFlow(true)
    private val wholeArea = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<String?>(null)
    private var queryJob: Job? = null

    private val trail = observeLiveAsset.trail()
        .map<List<LocalPoint>, ImmutableList<LocalPoint>> { it.toImmutableList() }
        .onStart { emit(persistentListOf()) }

    val liveAsset: StateFlow<MapLiveAsset?> = combine(observeLiveAsset(), trail) { asset, points -> asset.toMapLiveAsset(points) }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, null)

    private val liveAssetId = liveAsset.map { it?.entityId }.distinctUntilChanged()

    private val sheet = observeEntityDetail().map { it?.toSheet() }

    private val picture = observeBattlespace(filter, nova)

    private val sectorFraming = flow { emit(picture.first().sectorFraming()) }

    private val chrome = combine(overlaysVisible, wholeArea, sectorFraming, userMessage) { overlays, whole, sector, message ->
        MapChrome(overlays, if (whole) MapFraming.AreaOfOperations else sector, message)
    }

    val uiState: StateFlow<BattlespaceUiState> = combine(picture, liveAssetId, chrome, sheet) { picture, liveId, chrome, currentSheet ->
        BattlespaceUiState.Ready(
            trackCount = picture.counts.total,
            filters = picture.toFilterChips(),
            layers = picture.toLayers(liveId, chrome.overlaysVisible),
            framing = chrome.framing,
            novaMatches = if (picture.novaActive) picture.tracks.size else null,
            sheet = currentSheet,
            userMessage = chrome.userMessage,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, BattlespaceUiState.Loading)

    fun onFilterSelected(selected: AffiliationFilter) {
        filter.value = selected
    }

    fun onEntitySelected(entityId: Long) {
        focusEntity(entityId)
    }

    fun onSheetDismissed() {
        focusEntity(null)
    }

    fun onQuerySubmitted() {
        val raw = query.text.toString().trim()
        queryJob?.cancel()
        queryJob = launchAction({ userMessage.value = "NOVA could not run that query" }) {
            nova.value = if (raw.isEmpty()) null else NovaResult(queryOntology(raw).matches.mapTo(HashSet()) { it.id })
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
        wholeArea.update { !it }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private class MapChrome(val overlaysVisible: Boolean, val framing: MapFraming, val userMessage: String?)
}
