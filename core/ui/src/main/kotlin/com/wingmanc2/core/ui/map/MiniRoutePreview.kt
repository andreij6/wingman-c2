package com.wingmanc2.core.ui.map

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.ui.StatusTone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class PreviewRoute(
    val label: String,
    val points: ImmutableList<LocalPoint>,
    val tone: StatusTone,
    val widthDp: Float = 1.5f,
    val pattern: StrokePattern = StrokePattern.Solid,
    val orbit: ImmutableList<LocalPoint> = persistentListOf(),
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
    val framing = remember(routes, asset, threats, targets) { MapFraming.Fit(previewBounds(routes, asset, threats, targets), paddingDp = 28f) }
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
private const val ORBIT_WIDTH_SCALE = 0.75f

private fun previewLayers(
    routes: List<PreviewRoute>,
    asset: LocalPoint,
    threats: ImmutableList<MapThreat>,
    targets: ImmutableList<MapSymbol>,
): MapLayers = MapLayers(
    threats = threats,
    routes = routes.asReversed().flatMap { route ->
        listOfNotNull(
            route.orbit.takeIf { it.size > 1 }?.let { MapRoute("${route.label}-orbit", it, route.tone, route.widthDp * ORBIT_WIDTH_SCALE, StrokePattern.ShortDash) },
            MapRoute(route.label, route.points, route.tone, route.widthDp, route.pattern),
        )
    }.toImmutableList(),
    markers = routes.mapNotNull { route -> route.points.lastOrNull()?.let { MapMarker(route.label, it, route.tone) } }.toImmutableList(),
    symbols = (targets + MapSymbol(ASSET_PREVIEW_ID, Affiliation.FRIEND, asset, EntityKind.ASSET, isAir = true)).toImmutableList(),
)

private fun previewBounds(routes: List<PreviewRoute>, asset: LocalPoint, threats: List<MapThreat>, targets: List<MapSymbol>): MapBounds {
    var bounds = MapBounds(asset.x, asset.y, asset.x, asset.y)
    for (target in targets) bounds = bounds.including(target.position.x, target.position.y)
    for (threat in threats) bounds = bounds.including(threat.center.x, threat.center.y, threat.radiusM)
    for (route in routes) {
        for (p in route.points) bounds = bounds.including(p.x, p.y)
        for (p in route.orbit) bounds = bounds.including(p.x, p.y)
    }
    return bounds
}
