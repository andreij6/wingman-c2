package com.wingmanc2.core.ui.map

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class PreviewRoute(
    val label: String,
    val points: ImmutableList<LocalPoint>,
    val color: Color,
    val widthDp: Float = 1.5f,
    val pattern: StrokePattern = StrokePattern.Solid,
    val orbitRadiusM: Double? = null,
)

@Composable
fun MiniRoutePreview(
    routes: ImmutableList<PreviewRoute>,
    asset: LocalPoint,
    modifier: Modifier = Modifier,
    threats: ImmutableList<MapThreat> = persistentListOf(),
    targets: ImmutableList<MapSymbol> = persistentListOf(),
) {
    val layers = remember(routes, asset, threats, targets) { previewLayers(routes, asset, threats, targets) }
    val framing = remember(routes, asset, threats) { MapFraming.Fit(previewBounds(routes, asset, threats), paddingDp = 20f) }
    TacticalMap(
        layers = layers,
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(WingmanShapes.medium)
            .border(1.dp, WingmanTheme.colors.line, WingmanShapes.medium),
        framing = framing,
        showGrid = false,
        showScaleBar = false,
    )
}

private const val ASSET_PREVIEW_ID = Long.MIN_VALUE

private fun previewLayers(
    routes: List<PreviewRoute>,
    asset: LocalPoint,
    threats: ImmutableList<MapThreat>,
    targets: ImmutableList<MapSymbol>,
): MapLayers {
    val orbits = routes.mapNotNull { route ->
        val radius = route.orbitRadiusM ?: return@mapNotNull null
        MapThreat(id = "orbit-${route.label}", center = route.points.last(), radiusM = radius, color = route.color, fillAlpha = 0f, pattern = StrokePattern.ShortDash)
    }
    return MapLayers(
        threats = (threats + orbits).toImmutableList(),
        routes = routes.asReversed().map { MapRoute(it.label, it.points, it.color, it.widthDp, it.pattern) }.toImmutableList(),
        markers = routes.filter { it.points.isNotEmpty() }.map { route ->
            val end = route.points.last()
            val position = route.orbitRadiusM?.let { LocalPoint(end.x, end.y + it) } ?: end
            MapMarker(route.label, position, route.color)
        }.toImmutableList(),
        symbols = (targets + MapSymbol(ASSET_PREVIEW_ID, Affiliation.FRIEND, asset, EntityKind.ASSET, isAir = true)).toImmutableList(),
    )
}

private fun previewBounds(routes: List<PreviewRoute>, asset: LocalPoint, threats: List<MapThreat>): MapBounds {
    var bounds = MapBounds(asset.x, asset.y, asset.x, asset.y)
    for (route in routes) {
        for (p in route.points) bounds = bounds.including(p.x, p.y)
        route.orbitRadiusM?.let { r -> route.points.lastOrNull()?.let { bounds = bounds.including(it.x, it.y, r) } }
    }
    for (threat in threats) bounds = bounds.including(threat.center.x, threat.center.y, threat.radiusM)
    return bounds
}
