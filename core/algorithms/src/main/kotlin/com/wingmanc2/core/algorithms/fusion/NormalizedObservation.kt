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
)
