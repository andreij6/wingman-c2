package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.SourceFormat

data class NormalizedObservation(
    val format: SourceFormat,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val position: LocalPoint,
    val uncertaintyM: Double,
    val confidence: Float,
    val ageSeconds: Int?,
    val attributes: Map<String, String>,
    val missingFields: List<String>,
    val descriptor: String,
    val bearing: BearingRay? = null,
    val frequencyGhz: Double = Double.NaN,
    val identity: String? = null,
) {
    val located: Boolean get() = bearing != null || uncertaintyM < Localizer.UNLOCATED_THRESHOLD_M
}

data class BearingRay(val origin: LocalPoint, val azimuthDeg: Double, val maxRangeM: Double)
