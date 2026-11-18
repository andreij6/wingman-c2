package com.wingmanc2.feature.battlespace.presentation

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.ui.map.MapLayers
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.core.ui.map.MapSymbol
import com.wingmanc2.core.ui.map.MapThreat
import com.wingmanc2.core.ui.map.MapZone
import com.wingmanc2.feature.battlespace.domain.AffiliationFilter
import com.wingmanc2.feature.battlespace.domain.BattlespacePicture
import com.wingmanc2.feature.battlespace.domain.EntityDetail
import com.wingmanc2.feature.battlespace.domain.LiveAsset
import com.wingmanc2.feature.battlespace.domain.PlacedEntity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun BattlespacePicture.toLayers(liveAssetId: Long?, overlaysVisible: Boolean): MapLayers = MapLayers(
    zones = if (overlaysVisible) zones.map { it.toZone() }.toImmutableList() else persistentListOf(),
    threats = if (overlaysVisible) {
        tracks.filter { it.entity.affiliation == Affiliation.HOSTILE && it.entity.threatRadiusM > 0.0 }
            .map { MapThreat(it.entity.key, it.position, it.entity.threatRadiusM) }
            .toImmutableList()
    } else {
        persistentListOf()
    },
    symbols = tracks.filter { it.entity.id != liveAssetId }.map { it.toSymbol(selectedId, novaActive) }.toImmutableList(),
)

internal fun BattlespacePicture.toFilterChips() = AffiliationFilter.entries.map { option ->
    FilterChipUi(option, "${option.title} ${counts.of(option)}", option == filter)
}.toImmutableList()

private val AffiliationFilter.title: String
    get() = when (this) {
        AffiliationFilter.ALL -> "All"
        AffiliationFilter.HOSTILE -> "Hostile"
        AffiliationFilter.UNKNOWN -> "Unknown"
        AffiliationFilter.FRIENDLY -> "Friendly"
    }

private fun PlacedEntity.toZone(): MapZone {
    val restriction = entity.attributes[ATTR_RESTRICTION]?.substringBefore(SEPARATOR)
    return MapZone(
        id = entity.key,
        footprint = footprint.toImmutableList(),
        tag = if (restriction == null) entity.key else "${entity.key}$SEPARATOR$restriction",
    )
}

private fun PlacedEntity.toSymbol(selectedId: Long?, novaActive: Boolean): MapSymbol {
    val selected = entity.id == selectedId
    val tagged = selected || novaActive || entity.kind == EntityKind.ASSET || entity.affiliation == Affiliation.HOSTILE || entity.affiliation == Affiliation.UNKNOWN
    return MapSymbol(
        entityId = entity.id,
        affiliation = entity.affiliation,
        position = position,
        kind = entity.kind,
        isAir = isAir,
        tag = if (tagged) entity.key else null,
        selected = selected,
        headingDeg = entity.headingDeg,
    )
}

internal fun LiveAsset.toMapLiveAsset() = MapLiveAsset(
    entityId = entityId,
    position = position,
    headingDeg = headingDeg,
    tag = "$key$SEPARATOR${altitudeAglM.roundToInt()} m",
    trail = trail.toImmutableList(),
)

internal fun EntityDetail.toSheet() = EntitySheetUi(
    id = id,
    key = key,
    subtitle = subtitle,
    affiliation = affiliation,
    kind = kind,
    isAir = isAir,
    confidence = String.format(Locale.US, "Conf %.2f", confidence),
    verified = verified,
    frequency = frequency ?: NO_VALUE,
    bearing = bearingDeg?.let { String.format(Locale.US, "%03d°", it) } ?: NO_VALUE,
    lastSeen = lastSeenMinutes.toAge(),
    fusedFrom = sources.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Fused from ", separator = SEPARATOR),
    coordinate = position.toLabel(),
)

private fun Long.toAge(): String = when {
    this < 1 -> "<1 min"
    this < MINUTES_PER_HOUR -> "$this min"
    else -> "${this / MINUTES_PER_HOUR} h"
}

private fun GeoPoint.toLabel(): String = String.format(
    Locale.US,
    "%.4f%s %.4f%s",
    abs(lat),
    if (lat >= 0) "N" else "S",
    abs(lon),
    if (lon >= 0) "E" else "W",
)

private const val ATTR_RESTRICTION = "Restriction"
private const val SEPARATOR = " · "
private const val NO_VALUE = "—"
private const val MINUTES_PER_HOUR = 60L
