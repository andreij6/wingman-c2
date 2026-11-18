package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEntity
import kotlin.math.floor

const val PLANNER_CELL_M = OperationalArea.EXTENT_M / CostGrid.DEFAULT_RESOLUTION

data class PlanningSignature(
    val id: Long,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val threatRadiusM: Double,
    val cellX: Int,
    val cellY: Int,
)

val OntologyEntity.planningSignature: PlanningSignature
    get() {
        val point = OperationalArea.projection.toLocal(position)
        return PlanningSignature(id, kind, affiliation, threatRadiusM, floor(point.x / PLANNER_CELL_M).toInt(), floor(point.y / PLANNER_CELL_M).toInt())
    }
