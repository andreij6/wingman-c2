package com.wingmanc2.feature.plan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.StatusTone
import com.wingmanc2.core.ui.map.MapSymbol
import com.wingmanc2.core.ui.map.MapThreat
import com.wingmanc2.core.ui.map.MiniRoutePreview
import com.wingmanc2.core.ui.map.PreviewRoute
import com.wingmanc2.core.ui.map.StrokePattern
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun RoutePreviewPanel(preview: RoutePreviewUi?, selectedCode: String?, modifier: Modifier = Modifier) {
    if (preview == null) {
        PreviewLoading(modifier)
    } else {
        CoaRoutePreview(preview, selectedCode, modifier)
    }
}

@Composable
private fun CoaRoutePreview(preview: RoutePreviewUi, selectedCode: String?, modifier: Modifier) {
    val colors = WingmanTheme.colors
    val routes = remember(preview.routes, selectedCode) { previewRoutes(preview.routes, selectedCode) }
    val threats = remember(preview, colors) { previewThreats(preview, colors) }
    val targets = remember(preview) {
        persistentListOf(MapSymbol(preview.targetId, preview.targetAffiliation, preview.target, preview.targetKind))
    }
    MiniRoutePreview(routes = routes, asset = preview.asset, modifier = modifier, threats = threats, targets = targets)
}

@Composable
private fun PreviewLoading(modifier: Modifier) {
    val colors = WingmanTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(WingmanShapes.medium)
            .background(colors.mapBackground)
            .border(1.dp, colors.line, WingmanShapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
    }
}

private fun previewRoutes(lines: List<RouteLineUi>, selectedCode: String?): ImmutableList<PreviewRoute> =
    lines.sortedByDescending { it.code == selectedCode }.map { line ->
        PreviewRoute(
            label = line.letter,
            points = line.points,
            tone = line.tone,
            widthDp = if (line.code == selectedCode) 3f else 1.5f,
            pattern = if (line.tone == StatusTone.Warn) StrokePattern.Dashed else StrokePattern.Solid,
            orbit = line.orbit,
        )
    }.toImmutableList()

private fun previewThreats(preview: RoutePreviewUi, colors: WingmanColors): ImmutableList<MapThreat> =
    if (preview.threat != null && preview.threatRadiusM > 0.0) {
        persistentListOf(MapThreat(id = "threat", center = preview.threat, radiusM = preview.threatRadiusM, color = colors.hostile))
    } else {
        persistentListOf()
    }
