package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity

enum class AffiliationFilter(val affiliation: Affiliation?) {
    ALL(null),
    HOSTILE(Affiliation.HOSTILE),
    UNKNOWN(Affiliation.UNKNOWN),
    FRIENDLY(Affiliation.FRIEND),
}

data class TrackCounts(val total: Int, val hostile: Int, val unknown: Int, val friendly: Int) {
    fun of(filter: AffiliationFilter): Int = when (filter) {
        AffiliationFilter.ALL -> total
        AffiliationFilter.HOSTILE -> hostile
        AffiliationFilter.UNKNOWN -> unknown
        AffiliationFilter.FRIENDLY -> friendly
    }
}

data class PlacedEntity(
    val entity: OntologyEntity,
    val position: LocalPoint,
    val footprint: List<LocalPoint>,
    val isAir: Boolean,
)

data class BattlespacePicture(
    val tracks: List<PlacedEntity>,
    val zones: List<PlacedEntity>,
    val counts: TrackCounts,
    val filter: AffiliationFilter,
    val selectedId: Long?,
    val novaActive: Boolean,
)

data class NovaResult(val matchIds: Set<Long>)

data class EntityDetail(
    val id: Long,
    val key: String,
    val kind: EntityKind,
    val affiliation: Affiliation,
    val isAir: Boolean,
    val position: GeoPoint,
    val subtitle: String,
    val confidence: Float,
    val verified: Boolean,
    val frequency: String?,
    val bearingDeg: Int?,
    val lastSeenMinutes: Long,
    val sources: List<String>,
)

data class LiveAsset(
    val entityId: Long,
    val key: String,
    val position: LocalPoint,
    val headingDeg: Double,
    val altitudeAglM: Double,
    val trail: List<LocalPoint>,
)
