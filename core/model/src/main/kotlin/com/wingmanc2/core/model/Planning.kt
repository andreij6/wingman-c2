package com.wingmanc2.core.model

sealed interface IntentConstraint {
    data class StandoffMin(val meters: Double) : IntentConstraint

    data object AvoidRadar : IntentConstraint

    data class ReserveMin(val percent: Int) : IntentConstraint

    data class MaxAltitude(val metersAgl: Double) : IntentConstraint

    data object ZeroRf : IntentConstraint
}

data class CommanderIntent(val text: String, val targetKey: String?, val constraints: List<IntentConstraint>)

enum class CoaProfile { STEALTH, DIRECT, ENDURANCE }

data class CourseOfAction(
    val code: String,
    val profile: CoaProfile,
    val title: String,
    val matchScore: Float,
    val riskScore: Float,
    val etaSeconds: Int,
    val projectedReservePct: Int,
    val minStandoffM: Double,
    val maxAltitudeAglM: Double,
    val rfExposureSeconds: Int,
    val waypoints: List<GeoPoint>,
    val rationale: List<String>,
    val tradeOff: String?,
    val recommended: Boolean,
    val altitudesAglM: List<Double> = emptyList(),
    val orbitEntryIndex: Int? = null,
)
