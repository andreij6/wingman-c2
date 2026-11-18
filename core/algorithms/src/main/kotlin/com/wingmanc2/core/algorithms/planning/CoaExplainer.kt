package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.common.formatClock
import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.IntentConstraint
import kotlin.math.roundToInt

internal class CoaExplainer(private val context: PlanningContext) {
    private val primary = context.primaryThreat

    fun rationale(candidate: CoaCandidate): List<String> = buildList {
        val m = candidate.metrics
        add(radarLine(m))
        add(standoffLine(m.minStandoffM))
        add(reserveLine(m.landingBatteryPct.roundToInt()))
        context.intent.constraints.filterIsInstance<IntentConstraint.MaxAltitude>().firstOrNull()?.let {
            add("Peaks at ${m.maxAltitudeAglM.roundToInt()} m AGL (limit ${it.metersAgl.roundToInt()} m)")
        }
        when (candidate.profile) {
            CoaProfile.STEALTH -> Unit
            CoaProfile.DIRECT -> add("On station in ${formatClock(m.etaSeconds.roundToInt())} at ${candidate.route.end.altitudeAglM.roundToInt()} m AGL")
            CoaProfile.ENDURANCE -> add("Passive EO/IR orbit at ${candidate.observationRangeM.roundToInt()} m, zero RF emissions")
        }
    }

    fun tradeOff(candidate: CoaCandidate, fastestEta: Double): String? {
        val m = candidate.metrics
        val exposed = m.exposureSeconds.roundToInt()
        val slower = (m.etaSeconds - fastestEta).roundToInt()
        return when {
            exposed > 0 -> "$exposed s in ${dominantThreat(m)?.let(::lobeOf) ?: "radar lobe"}"
            !candidate.emitsRf -> "zero RF emissions"
            slower > 0 -> "+${formatClock(slower)} vs fastest COA"
            else -> null
        }
    }

    private fun radarLine(m: RouteMetrics): String {
        val exposed = m.exposureSeconds.roundToInt()
        val threat = dominantThreat(m)
        return when {
            threat == null -> "No radar threats along route"
            exposed > 0 -> "Crosses ${lobeOf(threat)} for $exposed s"
            m.maskedSeconds > 0.0 -> "Ridge shadow keeps UAS below ${lobeOf(threat)}"
            else -> "Stays outside ${threat.entity.key} radar envelope"
        }
    }

    private fun dominantThreat(m: RouteMetrics): ThreatRadar? = context.threats.getOrNull(m.dominantRadar) ?: primary

    private fun lobeOf(threat: ThreatRadar): String = "${threat.entity.key} ${threat.bandLabel} lobe"

    private fun standoffLine(minStandoffM: Double): String {
        val actual = minStandoffM.roundToInt()
        val limit = context.standoffM.roundToInt()
        return if (actual >= limit) "$actual m minimum standoff (limit $limit m)" else "$actual m minimum standoff breaches $limit m limit"
    }

    private fun reserveLine(landingPct: Int): String {
        val floor = context.reserveFloorPct.roundToInt()
        return if (landingPct >= floor) "Lands with ${landingPct - floor}% margin over $floor% reserve" else "Lands ${floor - landingPct}% below $floor% reserve"
    }
}
