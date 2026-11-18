package com.wingmanc2.feature.plan.presentation

import com.wingmanc2.core.algorithms.geo.LocalProjection
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.feature.plan.domain.CoaPlan
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val ORBIT_TOLERANCE = 0.05

internal fun CommanderIntent.toUi() = IntentUi(
    text = text,
    quoted = "“$text”",
    constraints = constraints.map { it.display }.toImmutableList(),
)

internal fun CoaPlan.toResultUi(projection: LocalProjection = OperationalArea.projection): PlanResultUi {
    val targetPoint = projection.toLocal(target.position)
    val threat = threats
        .filter { it.threatRadiusM > 0.0 }
        .minByOrNull { val p = projection.toLocal(it.position); hypot(p.x - targetPoint.x, p.y - targetPoint.y) }
    val preview = RoutePreviewUi(
        asset = projection.toLocal(asset.position),
        targetId = target.id,
        targetAffiliation = target.affiliation,
        targetKind = target.kind,
        target = targetPoint,
        threat = threat?.let { projection.toLocal(it.position) },
        threatRadiusM = threat?.threatRadiusM ?: 0.0,
        routes = courses.map { it.toRouteLine(projection, targetPoint) }.toImmutableList(),
    )
    return PlanResultUi(preview, courses.mapIndexed { rank, coa -> coa.toUi(rank, courses.size) }.toImmutableList())
}

internal fun formatEta(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private val CourseOfAction.letter: String get() = code.substringAfterLast('-')

private val CourseOfAction.tone: CoaTone
    get() = when {
        recommended -> CoaTone.Recommended
        rfExposureSeconds > 0 -> CoaTone.Exposed
        else -> CoaTone.Neutral
    }

private fun CourseOfAction.toUi(rank: Int, total: Int): CoaUi {
    val match = "${(matchScore * 100).roundToInt()}%"
    val risk = String.format(Locale.US, "%.2f", riskScore)
    val eta = formatEta(etaSeconds)
    return CoaUi(
        code = code,
        letter = letter,
        title = title,
        tone = tone,
        headline = if (recommended) "Recommended · best intent match" else "Alternative · rank ${rank + 1} of $total",
        match = match,
        risk = risk,
        eta = eta,
        reserve = "$projectedReservePct%",
        summary = listOfNotNull(match, "risk $risk", eta, tradeOff).joinToString(" · "),
        rationale = rationale.toImmutableList(),
    )
}

private fun CourseOfAction.toRouteLine(projection: LocalProjection, target: LocalPoint): RouteLineUi {
    val points = waypoints.map(projection::toLocal)
    if (profile != CoaProfile.ENDURANCE || points.size < 2) {
        return RouteLineUi(code, letter, tone, points.toImmutableList(), orbitRadiusM = null)
    }
    val radius = points.last().distanceTo(target)
    val entry = points.indexOfFirst { abs(it.distanceTo(target) - radius) <= radius * ORBIT_TOLERANCE }.coerceAtLeast(1)
    return RouteLineUi(code, letter, tone, points.subList(0, entry + 1).toImmutableList(), orbitRadiusM = radius)
}

private fun LocalPoint.distanceTo(other: LocalPoint) = hypot(x - other.x, y - other.y)
