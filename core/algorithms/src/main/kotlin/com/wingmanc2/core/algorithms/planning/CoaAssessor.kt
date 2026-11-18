package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.IntentConstraint
import kotlin.math.roundToInt

internal class CoaAssessor(private val context: PlanningContext) {
    private val explainer = CoaExplainer(context)

    fun rank(candidates: List<CoaCandidate>): List<CourseOfAction> {
        val fastestEta = candidates.minOf { it.metrics.etaSeconds }
        return candidates
            .map { assess(it, fastestEta) }
            .sortedWith(compareByDescending<CourseOfAction> { it.matchScore }.thenBy { it.riskScore }.thenBy { it.code })
            .mapIndexed { rank, coa -> if (rank == 0) coa.copy(recommended = true) else coa }
    }

    private fun assess(candidate: CoaCandidate, fastestEta: Double): CourseOfAction {
        val m = candidate.metrics
        return CourseOfAction(
            code = candidate.code,
            profile = candidate.profile,
            title = candidate.title,
            matchScore = match(candidate, fastestEta).toFloat(),
            riskScore = risk(m).toFloat(),
            etaSeconds = m.etaSeconds.roundToInt(),
            projectedReservePct = m.landingBatteryPct.roundToInt().coerceAtLeast(0),
            minStandoffM = m.minStandoffM,
            maxAltitudeAglM = m.maxAltitudeAglM,
            rfExposureSeconds = m.exposureSeconds.roundToInt(),
            waypoints = candidate.route.toGeoPoints(),
            altitudesAglM = candidate.route.waypoints.map { it.altitudeAglM },
            orbitEntryIndex = candidate.route.legs.indexOf(FlightRegime.LOITER).takeIf { it >= 0 },
            rationale = explainer.rationale(candidate),
            tradeOff = explainer.tradeOff(candidate, fastestEta),
            recommended = false,
        )
    }

    private fun match(candidate: CoaCandidate, fastestEta: Double): Double {
        var weighted = OBSERVATION_WEIGHT * observationQuality(candidate.observationRangeM) +
            TIMELINESS_WEIGHT * fastestEta / candidate.metrics.etaSeconds.coerceAtLeast(1.0)
        var total = OBSERVATION_WEIGHT + TIMELINESS_WEIGHT
        for (constraint in context.intent.constraints) {
            val weight = weightOf(constraint)
            weighted += weight * satisfaction(constraint, candidate)
            total += weight
        }
        return weighted / total
    }

    private fun satisfaction(constraint: IntentConstraint, candidate: CoaCandidate): Double {
        val m = candidate.metrics
        return when (constraint) {
            is IntentConstraint.StandoffMin -> unit(m.minStandoffM / constraint.meters)
            IntentConstraint.AvoidRadar -> 1.0 - unit(m.exposureSeconds / RADAR_TOLERANCE_S)
            is IntentConstraint.ReserveMin -> unit(m.landingBatteryPct / constraint.percent)
            is IntentConstraint.MaxAltitude -> unit(constraint.metersAgl / m.maxAltitudeAglM)
            IntentConstraint.ZeroRf -> if (candidate.emitsRf) 0.0 else 1.0
        }
    }

    private fun weightOf(constraint: IntentConstraint): Double = when (constraint) {
        is IntentConstraint.StandoffMin -> 1.0
        IntentConstraint.AvoidRadar -> 1.5
        is IntentConstraint.ReserveMin -> 1.0
        is IntentConstraint.MaxAltitude -> 0.75
        IntentConstraint.ZeroRf -> 1.0
    }

    private fun observationQuality(rangeM: Double): Double =
        1.0 - unit((rangeM - context.standoffM) / (FlightModel.EO_RANGE_M - context.standoffM).coerceAtLeast(1.0))

    private fun risk(m: RouteMetrics): Double {
        val exposure = unit(m.exposureSeconds / RADAR_TOLERANCE_S)
        val proximity = 1.0 - unit((m.minStandoffM - context.standoffM) / context.standoffM)
        val reserve = 1.0 - unit((m.landingBatteryPct - context.reserveFloorPct) / (context.startBatteryPct - context.reserveFloorPct).coerceAtLeast(1.0))
        return EXPOSURE_RISK * exposure + PROXIMITY_RISK * proximity + RESERVE_RISK * reserve
    }

    private fun unit(value: Double) = value.coerceIn(0.0, 1.0)

    private companion object {
        const val RADAR_TOLERANCE_S = 60.0
        const val OBSERVATION_WEIGHT = 1.0
        const val TIMELINESS_WEIGHT = 0.5
        const val EXPOSURE_RISK = 0.5
        const val PROXIMITY_RISK = 0.25
        const val RESERVE_RISK = 0.25
    }
}
