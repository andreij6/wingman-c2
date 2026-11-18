package com.wingmanc2.core.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.designsystem.component.MapTag
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

private val NoLiveAsset: () -> MapLiveAsset? = { null }
private val NoViewportPadding = PaddingValues()
private val ControlsReserveDp = Size(60f, 112f)
private val ScaleReserveDp = Size(100f, 44f)

private fun Double.keepInside(min: Double, max: Double, halfSpan: Double): Double =
    if (max - min <= 2 * halfSpan) (min + max) / 2 else coerceIn(min + halfSpan, max - halfSpan)
private const val HIT_RADIUS_DP = 24f

@Composable
fun TacticalMap(
    layers: MapLayers,
    modifier: Modifier = Modifier,
    framing: MapFraming = MapFraming.AreaOfOperations,
    liveAsset: () -> MapLiveAsset? = NoLiveAsset,
    onSymbolTap: ((Long) -> Unit)? = null,
    coordinateLabel: String? = null,
    showGrid: Boolean = true,
    showScaleBar: Boolean = true,
    onLayersClick: (() -> Unit)? = null,
    onRecenterClick: (() -> Unit)? = null,
    viewportPadding: PaddingValues = NoViewportPadding,
    reservedTop: Dp = 0.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.clipToBounds().background(WingmanTheme.colors.mapBackground)) {
        val direction = LocalLayoutDirection.current
        val left = viewportPadding.calculateLeftPadding(direction).value
        val right = viewportPadding.calculateRightPadding(direction).value
        val top = viewportPadding.calculateTopPadding().value
        val bottom = viewportPadding.calculateBottomPadding().value
        val camera = remember(framing, maxWidth, maxHeight, left, right, top, bottom) {
            val visibleWidth = maxWidth.value - left - right
            val visibleHeight = maxHeight.value - top - bottom
            val area = MapBounds.AreaOfOperations
            val fitted = framing.resolve(visibleWidth, visibleHeight)
            val base = fitted.copy(metersPerDp = minOf(fitted.metersPerDp, area.width / maxWidth.value, area.height / visibleHeight.coerceAtLeast(1f)))
            base.copy(
                centerX = base.centerX.keepInside(area.minX, area.maxX, visibleWidth / 2.0 * base.metersPerDp) + (right - left) / 2.0 * base.metersPerDp,
                centerY = base.centerY.keepInside(area.minY, area.maxY, visibleHeight / 2.0 * base.metersPerDp) - (bottom - top) / 2.0 * base.metersPerDp,
            )
        }
        val density = LocalDensity.current.density
        val hasControls = onLayersClick != null || onRecenterClick != null
        val reserved = remember(maxWidth, maxHeight, bottom, right, hasControls, showScaleBar, reservedTop, density) {
            val w = maxWidth.value * density
            val h = maxHeight.value * density
            listOfNotNull(
                Rect(0f, 0f, w, reservedTop.value * density).takeIf { reservedTop > 0.dp },
                Rect(w - ControlsReserveDp.width * density, 0f, w, ControlsReserveDp.height * density).takeIf { hasControls },
                Rect(
                    w - (right + ScaleReserveDp.width) * density,
                    h - (bottom + ScaleReserveDp.height) * density,
                    w - right * density,
                    h - bottom * density,
                ).takeIf { showScaleBar },
            )
        }
        MapCanvas(layers, camera, showGrid, liveAsset, onSymbolTap, Modifier.matchParentSize(), reserved)
        if (coordinateLabel != null) {
            MapTag(coordinateLabel, WingmanTheme.colors.text2, Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        if (onLayersClick != null || onRecenterClick != null) {
            Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onLayersClick != null) MapControlButton(WingmanIcons.Layers, "Map layers", onLayersClick)
                if (onRecenterClick != null) MapControlButton(WingmanIcons.MyLocation, "Recenter", onRecenterClick)
            }
        }
        if (showScaleBar) ScaleBar(camera.metersPerDp, Modifier.align(Alignment.BottomEnd).padding(viewportPadding).padding(12.dp))
        overlay()
    }
}

@Composable
internal fun MapCanvas(
    layers: MapLayers,
    camera: MapCamera,
    showGrid: Boolean,
    liveAsset: () -> MapLiveAsset?,
    onSymbolTap: ((Long) -> Unit)?,
    modifier: Modifier,
    reserved: List<Rect> = emptyList(),
) {
    val paint = rememberMapPaint()
    val basemap = rememberBasemap(camera)
    val index = remember(layers.symbols) { SymbolIndex(layers.symbols) }
    val currentLive by rememberUpdatedState(liveAsset)
    val currentTap by rememberUpdatedState(onSymbolTap)
    Spacer(
        modifier
            .semantics { contentDescription = "Tactical map, ${layers.symbols.size} contacts" }
            .then(
                if (onSymbolTap == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(layers.symbols, camera) {
                        detectTapGestures { tap ->
                            val projector = MapProjector(camera, size.width.toFloat(), size.height.toFloat(), density)
                            val x = projector.metersX(tap.x)
                            val y = projector.metersY(tap.y)
                            val radius = HIT_RADIUS_DP * density / projector.pxPerMeter
                            val live = currentLive()
                            val hit = when {
                                live != null && hypot(live.position.x - x, live.position.y - y) <= radius -> live.entityId
                                else -> index.nearest(x, y, radius).takeIf { it >= 0 }?.let { layers.symbols[it].entityId }
                            }
                            if (hit != null) currentTap?.invoke(hit)
                        }
                    }
                },
            )
            .drawWithCache {
                val projector = MapProjector(camera, size.width, size.height, density)
                val scene = MapScene(layers, projector, size, basemap, showGrid, paint, reserved)
                val live = LiveAssetLayer(projector, paint)
                onDrawBehind {
                    with(scene) { draw() }
                    currentLive()?.let { with(live) { draw(it) } }
                }
            },
    )
}

@Composable
private fun rememberBasemap(camera: MapCamera): ImageBitmap? {
    val resources = LocalResources.current
    val density = LocalDensity.current.density
    val shift = Basemap.sampleShiftFor((OperationalArea.EXTENT_M / camera.metersPerDp).toFloat() * density)
    val bitmap by produceState(Basemap.cached(shift), shift) {
        value = Basemap.cached(shift) ?: withContext(Dispatchers.IO) { Basemap.load(resources, shift) }
    }
    return bitmap
}

@Composable
private fun MapControlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = WingmanTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(WingmanShapes.small)
            .background(colors.surface)
            .border(1.dp, colors.line, WingmanShapes.small)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = colors.text, modifier = Modifier.size(24.dp))
    }
}

private val ScaleSteps = intArrayOf(50, 100, 200, 250, 500, 1_000, 2_000)
private const val SCALE_MAX_DP = 80.0

@Composable
private fun ScaleBar(metersPerDp: Double, modifier: Modifier) {
    val meters = remember(metersPerDp) { ScaleSteps.lastOrNull { it / metersPerDp <= SCALE_MAX_DP } ?: ScaleSteps.first() }
    val color = WingmanTheme.colors.text2
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (meters >= 1_000) "${meters / 1_000} km" else "$meters m",
            style = WingmanTheme.typography.caption,
            color = color,
        )
        Spacer(Modifier.height(4.dp))
        Spacer(
            Modifier
                .width((meters / metersPerDp).dp)
                .height(6.dp)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    val bottom = size.height - stroke / 2f
                    drawLine(color, Offset(0f, 0f), Offset(0f, bottom), stroke, StrokeCap.Square)
                    drawLine(color, Offset(0f, bottom), Offset(size.width, bottom), stroke, StrokeCap.Square)
                    drawLine(color, Offset(size.width, 0f), Offset(size.width, bottom), stroke, StrokeCap.Square)
                },
        )
    }
}
