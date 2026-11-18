package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.IntentConstraint
import kotlin.math.roundToInt

internal class CoaExplainer(private val context: PlanningContext) {
    private val threat = context.primaryThreat
    private val threatName = threat?.entity?.key ?: context.target.key
    private val lobe = "${threat?.bandLabel ?: "radar"} lobe"

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
            CoaProfile.DIRECT -> add("On station in ${clock(m.etaSeconds)} at ${candidate.route.end.altitudeAglM.roundToInt()} m AGL")
            CoaProfile.ENDURANCE -> add("Passive EO/IR orbit at ${candidate.observationRangeM.roundToInt()} m, zero RF emissions")
        }
    }

    fun tradeOff(candidate: CoaCandidate, fastestEta: Double): String? {
        val m = candidate.metrics
        val exposed = m.exposureSeconds.roundToInt()
        val slower = (m.etaSeconds - fastestEta).roundToInt()
        return when {
            exposed > 0 -> "$exposed s in $lobe"
            !candidate.emitsRf -> "zero RF emissions"
            slower > 0 -> "+${clock(slower.toDouble())} vs fastest COA"
            else -> null
        }
    }

    private fun radarLine(m: RouteMetrics): String {
        val exposed = m.exposureSeconds.roundToInt()
        return when {
            threat == null -> "No radar threats along route"
            exposed > 0 -> "Crosses $threatName $lobe for $exposed s"
            m.maskedSeconds > 0.0 -> "Ridge shadow keeps UAS below $threatName $lobe"
            else -> "Stays outside $threatName radar envelope"
        }
    }

    private fun standoffLine(minStandoffM: Double): String {
        val actual = minStandoffM.roundToInt()
        val limit = context.standoffM.roundToInt()
        return if (actual >= limit) "$actual m minimum standoff (limit $limit m)" else "$actual m minimum standoff breaches $limit m limit"
    }

    private fun reserveLine(landingPct: Int): String {
        val floor = context.reserveFloorPct.roundToInt()
        return if (landingPct >= floor) "Lands with ${landingPct - floor}% margin over $floor% reserve" else "Lands ${floor - landingPct}% below $floor% reserve"
    }

    private fun clock(seconds: Double): String {
        val total = seconds.roundToInt()
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
