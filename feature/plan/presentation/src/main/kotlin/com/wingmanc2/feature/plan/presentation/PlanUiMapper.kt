package com.wingmanc2.feature.plan.presentation

import com.wingmanc2.core.common.formatClock
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.ui.StatusTone
import com.wingmanc2.core.ui.format.label
import com.wingmanc2.feature.plan.domain.CoaPlan
import com.wingmanc2.feature.plan.domain.PlannedRoute
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale
import kotlin.math.roundToInt

internal fun CommanderIntent.toUi() = IntentUi(
    text = text,
    quoted = "“$text”",
    constraints = constraints.map { it.label }.toImmutableList(),
)

internal fun CoaPlan.toResultUi(): PlanResultUi {
    val tones = courses.associate { it.code to it.tone }
    val preview = RoutePreviewUi(
        asset = picture.asset,
        targetId = target.id,
        targetAffiliation = target.affiliation,
        targetKind = target.kind,
        target = picture.target,
        threat = picture.primaryThreat?.position,
        threatRadiusM = picture.primaryThreat?.radiusM ?: 0.0,
        routes = picture.routes.map { it.toRouteLine(tones.getValue(it.code)) }.toImmutableList(),
    )
    return PlanResultUi(preview, courses.mapIndexed { rank, coa -> coa.toUi(rank, courses.size) }.toImmutableList())
}

private fun letterOf(code: String): String = code.substringAfterLast('-')

private val CourseOfAction.tone: StatusTone
    get() = when {
        recommended -> StatusTone.Ok
        rfExposureSeconds > 0 -> StatusTone.Warn
        else -> StatusTone.Muted
    }

private fun CourseOfAction.toUi(rank: Int, total: Int): CoaUi {
    val match = "${(matchScore * 100).roundToInt()}%"
    val risk = String.format(Locale.ROOT, "%.2f", riskScore)
    val eta = formatClock(etaSeconds)
    return CoaUi(
        code = code,
        letter = letterOf(code),
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

private fun PlannedRoute.toRouteLine(tone: StatusTone) =
    RouteLineUi(code, letterOf(code), tone, approach.toImmutableList(), orbit.toImmutableList())
