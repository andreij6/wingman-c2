package com.wingmanc2.core.simulation.scenario

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.RelationType
import com.wingmanc2.core.model.SourceFormat

data class SeedEntity(
    val key: String,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val label: String,
    val subtitle: String,
    val local: LocalPoint,
    val confidence: Float,
    val verified: Boolean,
    val attributes: Map<String, String>,
    val footprintLocal: List<LocalPoint>,
    val threatRadiusM: Double,
    val headingDeg: Double?,
    val version: Long = ScenarioSeed.INITIAL_VERSION,
) {
    val position: GeoPoint = OperationalArea.projection.toGeo(local)
    val footprint: List<GeoPoint> = footprintLocal.map(OperationalArea.projection::toGeo)
    val isTrack: Boolean get() = kind != EntityKind.ZONE && kind != EntityKind.CLASS
}

data class SeedEdge(
    val sourceKey: String,
    val targetKey: String,
    val relation: RelationType,
    val weight: Float,
    val label: String,
    val provenance: String,
)

data class SeedObservation(
    val entityKey: String,
    val format: SourceFormat,
    val payload: String,
    val note: String,
    val minutesAgo: Int,
)
