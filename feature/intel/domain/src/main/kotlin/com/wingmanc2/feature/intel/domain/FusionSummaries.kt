package com.wingmanc2.feature.intel.domain

import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.FusionSummary

fun EntityGraph.fusionSummary(): FusionSummary? {
    val focus = nodes.firstOrNull { it.id == focusId } ?: return null
    val incident = edges.count { it.sourceId == focusId || it.targetId == focusId }
    return FusionSummary(focus.id, focus.kind, focus.confidence, incident, focus.syncState)
}
