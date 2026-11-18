package com.wingmanc2.core.model

sealed interface IntentConstraint {
    val display: String

    data class StandoffMin(val meters: Double) : IntentConstraint {
        override val display get() = "Standoff ≥ ${meters.toInt()} m"
    }

    data object AvoidRadar : IntentConstraint {
        override val display get() = "Avoid radar"
    }

    data class ReserveMin(val percent: Int) : IntentConstraint {
        override val display get() = "Reserve ≥ $percent%"
    }

    data class MaxAltitude(val metersAgl: Double) : IntentConstraint {
        override val display get() = "Max ${metersAgl.toInt()} m AGL"
    }

    data object ZeroRf : IntentConstraint {
        override val display get() = "Zero RF"
    }
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
)
