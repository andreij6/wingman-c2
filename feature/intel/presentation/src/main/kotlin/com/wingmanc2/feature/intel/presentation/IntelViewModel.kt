package com.wingmanc2.feature.intel.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.FocusEntityUseCase
import com.wingmanc2.core.domain.ObserveFocusUseCase
import com.wingmanc2.core.domain.QueryOntologyUseCase
import com.wingmanc2.core.model.QueryResult
import com.wingmanc2.core.ui.WhileUiSubscribed
import com.wingmanc2.feature.intel.domain.ObserveEntityGraphUseCase
import com.wingmanc2.feature.intel.domain.ObserveLineageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
internal class IntelViewModel @Inject constructor(
    queryOntology: QueryOntologyUseCase,
    observeFocus: ObserveFocusUseCase,
    observeGraph: ObserveEntityGraphUseCase,
    observeLineage: ObserveLineageUseCase,
    private val focusEntity: FocusEntityUseCase,
    @Dispatcher(WingmanDispatcher.Default) default: CoroutineDispatcher,
) : ViewModel() {

    val query = TextFieldState(DEFAULT_QUERY)

    private val edits = snapshotFlow { query.text.toString() }

    private val results: Flow<QueryResult> = edits
        .withIndex()
        .debounce { if (it.index == 0) 0L else QUERY_DEBOUNCE_MS }
        .flatMapLatest { queryOntology.live(it.value) }
        .flowOn(default)
        .shareIn(viewModelScope, WhileUiSubscribed, replay = 1)

    private val focus: Flow<FocusDetail?> = combine(observeFocus(), results) { focused, result ->
        focused ?: result.matches.firstOrNull()?.id
    }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(observeGraph(id).distinctUntilChangedBy { it.renderKey() }, observeLineage(id), ::FocusDetail)
            }
        }

    val uiState: StateFlow<IntelUiState> = combine(results, focus) { result, detail -> result.toUiState(detail) }
        .flowOn(default)
        .stateIn(viewModelScope, WhileUiSubscribed, IntelUiState.Loading)

    init {
        edits.drop(1).debounce(QUERY_DEBOUNCE_MS).onEach { focusEntity(null) }.launchIn(viewModelScope)
    }

    fun onFocus(entityId: Long) = focusEntity(entityId)

    private companion object {
        const val DEFAULT_QUERY = "unverified emitters ≤2km from Zone-4B"
        const val QUERY_DEBOUNCE_MS = 250L
    }
}
