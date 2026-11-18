package com.wingmanc2.feature.intel.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.FusionSummary
import com.wingmanc2.core.model.QueryResult
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.feature.intel.domain.FocusEntityUseCase
import com.wingmanc2.feature.intel.domain.ObserveEntityGraphUseCase
import com.wingmanc2.feature.intel.domain.ObserveLineageUseCase
import com.wingmanc2.feature.intel.domain.QueryOntologyNlUseCase
import com.wingmanc2.feature.intel.domain.fusionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class IntelViewModel @Inject constructor(
    queryOntology: QueryOntologyNlUseCase,
    observeGraph: ObserveEntityGraphUseCase,
    observeLineage: ObserveLineageUseCase,
    private val focusEntity: FocusEntityUseCase,
    @Dispatcher(WingmanDispatcher.Default) default: CoroutineDispatcher,
) : ViewModel() {

    private val initialRequest = QueryRequest(DEFAULT_QUERY)
    private val request = MutableStateFlow(initialRequest)
    private val pendingAutoFocus = AtomicReference<QueryRequest?>(null)

    var query by mutableStateOf(DEFAULT_QUERY)
        private set

    private val results: Flow<QueryResult> = request
        .debounce { if (it === initialRequest) 0L else QUERY_DEBOUNCE_MS }
        .flatMapLatest { req ->
            queryOntology(req.text).onEach { result ->
                if (pendingAutoFocus.compareAndSet(req, null)) focusEntity(result.matches.firstOrNull()?.id)
            }
        }
        .flowOn(default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val focus: Flow<FocusDetail?> = combine(focusEntity.focusedEntityId, results) { focused, result ->
        focused ?: result.matches.firstOrNull()?.id
    }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else combine(observeGraph(id), observeLineage(id), ::FocusDetail)
        }

    val uiState: StateFlow<IntelUiState> = combine(results, focus, ::toUiState)
        .flowOn(default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IntelUiState())

    fun onQueryChange(text: String) {
        query = text
        val next = QueryRequest(text)
        pendingAutoFocus.set(next)
        request.value = next
    }

    fun onNodeClick(entityId: Long) = focusEntity(entityId)

    fun onPlanRecon(onFocused: () -> Unit) {
        val id = uiState.value.focusId ?: return
        focusEntity(id)
        onFocused()
    }

    private fun toUiState(result: QueryResult, detail: FocusDetail?): IntelUiState {
        val graph = detail?.graph
        val fusion = graph?.fusionSummary()
        return IntelUiState(
            latency = result.latencyMicros.toLatencyLabel(),
            predicates = result.query.predicates.map { it.display }.toImmutableList(),
            graphMeta = graphMeta(result.matches.size, graph),
            graph = graph,
            lineage = if (graph != null && fusion != null) lineageUi(graph, detail.observations, fusion) else null,
            focusId = graph?.focusId,
        )
    }

    private fun lineageUi(graph: EntityGraph, observations: List<RawObservation>, fusion: FusionSummary) = LineageUi(
        entityKey = graph.nodes.first { it.id == graph.focusId }.key,
        rows = observations.map { LineageRow(it.id, it.format.name, it.payload.replace('\n', ' ')) }.toImmutableList(),
        fusedTitle = "Fused → ObjectBox entity #${fusion.entityId}",
        fusedDetail = String.format(
            Locale.US,
            "kind=%s  conf=%.2f  edges=%d  %s",
            fusion.kind.name,
            fusion.confidence,
            fusion.edgeCount,
            fusion.syncState.name.lowercase(Locale.US),
        ),
    )

    private fun graphMeta(matches: Int, graph: EntityGraph?): String {
        val head = if (matches == 1) "1 match" else "$matches matches"
        return if (graph == null) head else "$head · ${graph.totalNodes} nodes · ${graph.totalEdges} edges"
    }

    private fun Long.toLatencyLabel(): String = if (this < MICROS_PER_MS) "$this µs" else "${this / MICROS_PER_MS} ms"

    private class QueryRequest(val text: String)

    private class FocusDetail(val graph: EntityGraph, val observations: List<RawObservation>)

    private companion object {
        const val DEFAULT_QUERY = "unverified emitters near Zone-4B"
        const val QUERY_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
        const val MICROS_PER_MS = 1_000L
    }
}
