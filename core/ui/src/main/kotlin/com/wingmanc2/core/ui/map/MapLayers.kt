package com.wingmanc2.core.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.ui.StatusTone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class StrokePattern(val onDp: Float, val offDp: Float) {
    Solid(0f, 0f),
    Dashed(6f, 4f),
    ShortDash(3f, 3f),
    Dotted(2f, 4f),
}

enum class TagPlacement { End, Below }

@Immutable
data class MapZone(
    val id: String,
    val footprint: ImmutableList<LocalPoint>,
    val tag: String? = null,
    val color: Color = Color.Unspecified,
)

@Immutable
data class MapThreat(
    val id: String,
    val center: LocalPoint,
    val radiusM: Double,
    val color: Color = Color.Unspecified,
    val fillAlpha: Float = 0.07f,
    val pattern: StrokePattern = StrokePattern.Dashed,
)

@Immutable
data class MapRoute(
    val id: String,
    val points: ImmutableList<LocalPoint>,
    val tone: StatusTone,
    val widthDp: Float = 2f,
    val pattern: StrokePattern = StrokePattern.Solid,
    val alpha: Float = 1f,
)

@Immutable
data class MapWaypoint(
    val number: Int,
    val position: LocalPoint,
    val done: Boolean,
    val target: Boolean = false,
)

@Immutable
data class MapSymbol(
    val entityId: Long,
    val affiliation: Affiliation,
    val position: LocalPoint,
    val kind: EntityKind = EntityKind.TRACK,
    val isAir: Boolean = false,
    val tag: String? = null,
    val tagColor: Color = Color.Unspecified,
    val tagPlacement: TagPlacement = TagPlacement.End,
    val selected: Boolean = false,
    val headingDeg: Double? = null,
)

@Immutable
data class MapMarker(
    val label: String,
    val position: LocalPoint,
    val tone: StatusTone,
)

@Immutable
data class MapCallout(
    val anchor: LocalPoint,
    val title: String,
    val detail: String? = null,
    val color: Color = Color.Unspecified,
)

@Immutable
data class MapLiveAsset(
    val entityId: Long,
    val position: LocalPoint,
    val headingDeg: Double,
    val tag: String? = null,
    val trail: ImmutableList<LocalPoint> = persistentListOf(),
    val affiliation: Affiliation = Affiliation.FRIEND,
    val isAir: Boolean = true,
)

@Immutable
data class MapLayers(
    val zones: ImmutableList<MapZone> = persistentListOf(),
    val threats: ImmutableList<MapThreat> = persistentListOf(),
    val routes: ImmutableList<MapRoute> = persistentListOf(),
    val waypoints: ImmutableList<MapWaypoint> = persistentListOf(),
    val markers: ImmutableList<MapMarker> = persistentListOf(),
    val symbols: ImmutableList<MapSymbol> = persistentListOf(),
    val callouts: ImmutableList<MapCallout> = persistentListOf(),
) {
    companion object {
        val Empty = MapLayers()
    }
}
