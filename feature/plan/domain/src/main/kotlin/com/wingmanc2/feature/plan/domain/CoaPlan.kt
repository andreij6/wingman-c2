package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity

data class CoaPlan(
    val intent: CommanderIntent,
    val target: OntologyEntity,
    val asset: OntologyEntity,
    val courses: List<CourseOfAction>,
    val picture: PlanPicture,
)

data class PlanPicture(
    val asset: LocalPoint,
    val target: LocalPoint,
    val primaryThreat: PlannedThreat?,
    val routes: List<PlannedRoute>,
)

data class PlannedThreat(val position: LocalPoint, val radiusM: Double)

data class PlannedRoute(val code: String, val approach: List<LocalPoint>, val orbit: List<LocalPoint>)
