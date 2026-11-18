package com.wingmanc2.feature.intel.domain

import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.SyncState

data class FusionSummary(val entityId: Long, val kind: EntityKind, val confidence: Float, val edgeCount: Int, val syncState: SyncState)

fun EntityGraph.fusionSummary(): FusionSummary? {
    val focus = nodes.firstOrNull { it.id == focusId } ?: return null
    val incident = edges.count { it.sourceId == focusId || it.targetId == focusId }
    return FusionSummary(focus.id, focus.kind, focus.confidence, incident, focus.syncState)
}
