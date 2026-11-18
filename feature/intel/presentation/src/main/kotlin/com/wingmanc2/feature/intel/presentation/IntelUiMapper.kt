package com.wingmanc2.feature.intel.presentation

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.QueryResult
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.SyncState
import com.wingmanc2.core.ui.format.label
import com.wingmanc2.feature.intel.domain.FusionSummary
import com.wingmanc2.feature.intel.domain.fusionSummary
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

private const val MAX_LINEAGE_ROWS = 3
private const val MICROS_PER_MS = 1_000L

internal class FocusDetail(val graph: EntityGraph, val observations: List<RawObservation>)

internal fun QueryResult.toUiState(detail: FocusDetail?): IntelUiState.Ready {
    val graph = detail?.graph
    val fusion = graph?.fusionSummary()
    return IntelUiState.Ready(
        latency = latencyMicros.toLatencyLabel(),
        predicates = query.predicates.map { it.label }.toImmutableList(),
        graphMeta = graphMeta(matches.size, graph),
        graph = graph,
        lineage = if (graph != null && fusion != null) lineageUi(graph, detail.observations, fusion) else null,
        focusId = graph?.focusId,
    )
}

internal fun EntityGraph.renderKey(): GraphRenderKey = GraphRenderKey(
    focusId = focusId,
    nodes = nodes.map { GraphNodeKey(it.id, it.key, it.kind, it.affiliation, it.subtitle, it.confidence, it.syncState) },
    edges = edges,
    totalNodes = totalNodes,
    totalEdges = totalEdges,
)

internal data class GraphNodeKey(
    val id: Long,
    val key: String,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val subtitle: String,
    val confidence: Float,
    val syncState: SyncState,
)

internal data class GraphRenderKey(
    val focusId: Long,
    val nodes: List<GraphNodeKey>,
    val edges: List<OntologyEdge>,
    val totalNodes: Int,
    val totalEdges: Int,
)

private fun lineageUi(graph: EntityGraph, observations: List<RawObservation>, fusion: FusionSummary) = LineageUi(
    entityKey = graph.nodes.first { it.id == graph.focusId }.key,
    rows = observations.take(MAX_LINEAGE_ROWS).map { LineageRow(it.id, it.format.name, it.payload.replace('\n', ' ')) }.toImmutableList(),
    fusedTitle = "Fused → ObjectBox entity #${fusion.entityId}",
    fusedDetail = String.format(
        Locale.ROOT,
        "kind=%s  conf=%.2f  edges=%d  %s",
        fusion.kind.name,
        fusion.confidence,
        fusion.edgeCount,
        fusion.syncState.name.lowercase(Locale.ROOT),
    ),
)

private fun graphMeta(matches: Int, graph: EntityGraph?): String {
    val head = if (matches == 1) "1 match" else "$matches matches"
    return if (graph == null) head else "$head · ${graph.totalNodes} nodes · ${graph.totalEdges} edges"
}

private fun Long.toLatencyLabel(): String = if (this < MICROS_PER_MS) "$this µs" else "${this / MICROS_PER_MS} ms"
