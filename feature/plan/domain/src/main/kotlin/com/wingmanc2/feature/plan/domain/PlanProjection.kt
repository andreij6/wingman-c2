package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import kotlin.math.hypot

internal fun planPicture(target: OntologyEntity, asset: OntologyEntity, threats: List<OntologyEntity>, courses: List<CourseOfAction>): PlanPicture {
    val projection = OperationalArea.projection
    val targetPoint = projection.toLocal(target.position)
    var primary: PlannedThreat? = null
    var nearest = Double.POSITIVE_INFINITY
    for (threat in threats) {
        if (threat.threatRadiusM <= 0.0) continue
        val point = projection.toLocal(threat.position)
        val distance = point.distanceTo(targetPoint)
        if (distance < nearest) {
            nearest = distance
            primary = PlannedThreat(point, threat.threatRadiusM)
        }
    }
    return PlanPicture(
        asset = projection.toLocal(asset.position),
        target = targetPoint,
        primaryThreat = primary,
        routes = courses.map { it.plannedRoute() },
    )
}

private fun CourseOfAction.plannedRoute(): PlannedRoute {
    val points = waypoints.map(OperationalArea.projection::toLocal)
    val entry = orbitEntryIndex?.takeIf { it in points.indices } ?: return PlannedRoute(code, points, orbit = emptyList())
    return PlannedRoute(code, points.subList(0, entry + 1), orbit = points.subList(entry, points.size))
}

private fun LocalPoint.distanceTo(other: LocalPoint) = hypot(x - other.x, y - other.y)
