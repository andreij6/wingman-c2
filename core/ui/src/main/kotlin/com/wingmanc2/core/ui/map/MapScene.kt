package com.wingmanc2.core.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class MapScene(
    layers: MapLayers,
    private val projector: MapProjector,
    private val size: Size,
    private val basemap: ImageBitmap?,
    showGrid: Boolean,
    private val paint: MapPaint,
) {
    private val colors = paint.colors
    private val imageOffset: IntOffset
    private val imageSize: IntSize
    private val grid: Path? = if (showGrid) gridPath() else null

    private val zones = layers.zones
    private val zonePaths = Array(zones.size) { polyline(zones[it].footprint, closed = true) }
    private val zoneStroke = paint.stroke(1.5f, StrokePattern.Dashed)
    private val zoneTags = Array(zones.size) { i -> zones[i].tag?.let { paint.tag(it, zones[i].color) } }
    private val zoneTagOrigins = FloatArray(zones.size * 2)

    private val threats = layers.threats
    private val threatCenters = FloatArray(threats.size * 2)
    private val threatRadii = FloatArray(threats.size) { (threats[it].radiusM * projector.pxPerMeter).toFloat() }
    private val threatStrokes = Array(threats.size) { paint.stroke(1.2f, threats[it].pattern) }

    private val trails = layers.trails
    private val trailPaths = Array(trails.size) { polyline(trails[it].points, closed = false) }
    private val trailStroke = paint.stroke(2f, StrokePattern.Dotted)

    private val routes = layers.routes
    private val routePaths = Array(routes.size) { polyline(routes[it].points, closed = false) }
    private val routeStrokes = Array(routes.size) { paint.stroke(routes[it].widthDp, routes[it].pattern) }

    private val waypoints = layers.waypoints
    private val waypointCenters = FloatArray(waypoints.size * 2)
    private val waypointLabels = Array(waypoints.size) { paint.waypointLabel(waypoints[it].number, if (waypoints[it].done) colors.background else colors.text) }
    private val waypointRing = paint.selectionRing
    private val targetRing = paint.stroke(1f, StrokePattern.ShortDash)

    private val markers = layers.markers
    private val markerCenters = FloatArray(markers.size * 2)
    private val markerLabels = Array(markers.size) { paint.markerLabel(markers[it].label) }

    private val symbols = layers.symbols
    private val symbolCenters = FloatArray(symbols.size * 2)
    private val symbolTags = Array(symbols.size) { i ->
        val symbol = symbols[i]
        symbol.tag?.let { paint.tag(it, symbol.tagColor.takeOrElse { colors.affiliation(symbol.affiliation) }) }
    }
    private val symbolTagOrigins = FloatArray(symbols.size * 2)

    private val callouts = layers.callouts
    private val calloutTitles = Array(callouts.size) { paint.calloutTitle(callouts[it].title, callouts[it].color) }
    private val calloutDetails = Array(callouts.size) { i -> callouts[i].detail?.let(paint::calloutDetail) }
    private val calloutBoxes = FloatArray(callouts.size * 4)
    private val calloutCorner = CornerRadius(paint.dp(8f))

    init {
        val left = projector.x(-OperationalArea.HALF_EXTENT_M)
        val top = projector.y(OperationalArea.HALF_EXTENT_M)
        val right = projector.x(OperationalArea.HALF_EXTENT_M)
        val bottom = projector.y(-OperationalArea.HALF_EXTENT_M)
        imageOffset = IntOffset(left.roundToInt(), top.roundToInt())
        imageSize = IntSize((right - left).roundToInt(), (bottom - top).roundToInt())
        for (i in 0 until zones.size) placeZoneTag(i)
        for (i in 0 until threats.size) project(threats[i].center, threatCenters, i)
        for (i in 0 until waypoints.size) project(waypoints[i].position, waypointCenters, i)
        for (i in 0 until markers.size) project(markers[i].position, markerCenters, i)
        for (i in 0 until symbols.size) {
            project(symbols[i].position, symbolCenters, i)
            placeSymbolTag(i)
        }
        for (i in 0 until callouts.size) placeCallout(i)
    }

    fun DrawScope.draw() {
        drawRect(colors.mapBackground)
        if (basemap != null) drawImage(basemap, dstOffset = imageOffset, dstSize = imageSize)
        drawRect(colors.background, alpha = DIM_ALPHA)
        if (grid != null) drawPath(grid, colors.text, alpha = GRID_ALPHA, style = paint.hairline)
        drawZones()
        drawThreats()
        for (i in 0 until trails.size) drawPath(trailPaths[i], trails[i].color, alpha = TRAIL_ALPHA, style = trailStroke)
        for (i in 0 until routes.size) drawPath(routePaths[i], routes[i].color, alpha = routes[i].alpha, style = routeStrokes[i])
        drawWaypoints()
        drawMarkers()
        drawSymbols()
        drawTags()
        drawCallouts()
    }

    private fun DrawScope.drawZones() {
        for (i in 0 until zones.size) {
            drawPath(zonePaths[i], zones[i].color, alpha = ZONE_FILL_ALPHA)
            drawPath(zonePaths[i], zones[i].color, style = zoneStroke)
        }
    }

    private fun DrawScope.drawThreats() {
        for (i in 0 until threats.size) {
            val threat = threats[i]
            val center = Offset(threatCenters[2 * i], threatCenters[2 * i + 1])
            if (threat.fillAlpha > 0f) drawCircle(threat.color, threatRadii[i], center, alpha = threat.fillAlpha)
            drawCircle(threat.color, threatRadii[i], center, alpha = RING_ALPHA, style = threatStrokes[i])
        }
    }

    private fun DrawScope.drawWaypoints() {
        val radius = paint.dp(8f)
        for (i in 0 until waypoints.size) {
            val waypoint = waypoints[i]
            val center = Offset(waypointCenters[2 * i], waypointCenters[2 * i + 1])
            if (waypoint.target) drawCircle(colors.text2, paint.dp(22f), center, style = targetRing)
            if (waypoint.done) {
                drawCircle(colors.friend, radius, center)
            } else {
                drawCircle(colors.surface, radius, center)
                drawCircle(colors.text2, radius, center, style = waypointRing)
            }
            with(paint) { drawCentered(waypointLabels[i], center) }
        }
    }

    private fun DrawScope.drawMarkers() {
        val radius = paint.dp(9f)
        for (i in 0 until markers.size) {
            val center = Offset(markerCenters[2 * i], markerCenters[2 * i + 1])
            drawCircle(markers[i].color, radius, center)
            with(paint) { drawCentered(markerLabels[i], center) }
        }
    }

    private fun DrawScope.drawSymbols() {
        for (i in 0 until symbols.size) {
            val symbol = symbols[i]
            val center = Offset(symbolCenters[2 * i], symbolCenters[2 * i + 1])
            if (symbol.selected) drawCircle(colors.text, paint.dp(SELECTION_RADIUS_DP), center, style = paint.selectionRing)
            val heading = symbol.headingDeg
            if (heading != null) drawHeading(paint, center, heading, colors.affiliation(symbol.affiliation))
            paint.glyphs.draw(this, center, symbol.affiliation, symbol.kind, symbol.isAir)
        }
    }

    private fun DrawScope.drawTags() {
        with(paint) {
            for (i in 0 until zones.size) zoneTags[i]?.let { drawTag(it, zoneTagOrigins[2 * i], zoneTagOrigins[2 * i + 1]) }
            for (i in 0 until symbols.size) symbolTags[i]?.let { drawTag(it, symbolTagOrigins[2 * i], symbolTagOrigins[2 * i + 1]) }
        }
    }

    private fun DrawScope.drawCallouts() {
        val padX = paint.dp(9f)
        val padY = paint.dp(5f)
        for (i in 0 until callouts.size) {
            val left = calloutBoxes[4 * i]
            val top = calloutBoxes[4 * i + 1]
            val boxSize = Size(calloutBoxes[4 * i + 2], calloutBoxes[4 * i + 3])
            drawRoundRect(colors.scrim, Offset(left, top), boxSize, calloutCorner, alpha = CALLOUT_ALPHA)
            drawRoundRect(callouts[i].color, Offset(left, top), boxSize, calloutCorner, style = paint.hairline)
            val title = calloutTitles[i]
            drawText(title, topLeft = Offset(left + padX, top + padY))
            calloutDetails[i]?.let { drawText(it, topLeft = Offset(left + padX, top + padY + title.size.height)) }
        }
    }

    private fun project(point: LocalPoint, into: FloatArray, index: Int) {
        into[2 * index] = projector.x(point.x)
        into[2 * index + 1] = projector.y(point.y)
    }

    private fun polyline(points: List<LocalPoint>, closed: Boolean) = Path().apply {
        for (i in points.indices) {
            val x = projector.x(points[i].x)
            val y = projector.y(points[i].y)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        if (closed && points.size > 2) close()
    }

    private fun gridPath() = Path().apply {
        val half = OperationalArea.HALF_EXTENT_M
        val top = projector.y(half)
        val bottom = projector.y(-half)
        val left = projector.x(-half)
        val right = projector.x(half)
        var m = -half + GRID_STEP_M
        while (m < half) {
            moveTo(projector.x(m), top)
            lineTo(projector.x(m), bottom)
            moveTo(left, projector.y(m))
            lineTo(right, projector.y(m))
            m += GRID_STEP_M
        }
    }

    private fun placeZoneTag(i: Int) {
        val tag = zoneTags[i] ?: return
        val footprint = zones[i].footprint
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (k in footprint.indices) {
            val x = projector.x(footprint[k].x)
            val y = projector.y(footprint[k].y)
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        zoneTagOrigins[2 * i] = (minX + maxX) / 2f - paint.tagWidth(tag) / 2f
        zoneTagOrigins[2 * i + 1] = min(minY + paint.dp(ZONE_TAG_INSET_DP), (minY + maxY - paint.tagHeight) / 2f)
    }

    private fun placeSymbolTag(i: Int) {
        val tag = symbolTags[i] ?: return
        val x = symbolCenters[2 * i]
        val y = symbolCenters[2 * i + 1]
        val gap = paint.glyphs.extentPx + paint.dp(TAG_GAP_DP)
        when (symbols[i].tagPlacement) {
            TagPlacement.End -> {
                symbolTagOrigins[2 * i] = x + gap
                symbolTagOrigins[2 * i + 1] = y - paint.tagHeight / 2f
            }
            TagPlacement.Below -> {
                symbolTagOrigins[2 * i] = x - paint.tagWidth(tag) / 2f
                symbolTagOrigins[2 * i + 1] = y + gap
            }
        }
    }

    private fun placeCallout(i: Int) {
        val title = calloutTitles[i]
        val detail = calloutDetails[i]
        val width = max(title.size.width, detail?.size?.width ?: 0) + paint.dp(18f)
        val height = title.size.height + (detail?.size?.height ?: 0) + paint.dp(10f)
        val ax = projector.x(callouts[i].anchor.x)
        val ay = projector.y(callouts[i].anchor.y)
        val margin = paint.dp(8f)
        val gap = paint.dp(12f)
        val below = ay + gap
        calloutBoxes[4 * i] = (ax - width / 2f).coerceIn(margin, max(margin, size.width - width - margin))
        calloutBoxes[4 * i + 1] = if (below + height <= size.height - margin) below else ay - gap - height
        calloutBoxes[4 * i + 2] = width
        calloutBoxes[4 * i + 3] = height
    }

    private companion object {
        const val DIM_ALPHA = 0.28f
        const val GRID_ALPHA = 0.05f
        const val GRID_STEP_M = 1_000.0
        const val ZONE_FILL_ALPHA = 0.10f
        const val RING_ALPHA = 0.8f
        const val TRAIL_ALPHA = 0.8f
        const val CALLOUT_ALPHA = 0.85f
        const val ZONE_TAG_INSET_DP = 18f
        const val TAG_GAP_DP = 6f
    }
}

internal const val SELECTION_RADIUS_DP = 16f
private const val HEADING_LENGTH_DP = 29f

internal fun DrawScope.drawHeading(paint: MapPaint, center: Offset, headingDeg: Double, color: Color) {
    val radians = Math.toRadians(headingDeg)
    val length = paint.dp(HEADING_LENGTH_DP)
    val end = Offset(center.x + (sin(radians) * length).toFloat(), center.y - (cos(radians) * length).toFloat())
    drawLine(color, center, end, strokeWidth = paint.dp(2f), cap = StrokeCap.Round)
}

internal class LiveAssetLayer(private val projector: MapProjector, private val paint: MapPaint) {
    private val trailPath = Path()
    private val trailStroke: Stroke = paint.stroke(2f, StrokePattern.Dotted)
    private var trailSource: List<LocalPoint>? = null
    private var tagText: String? = null
    private var tagLayout: TextLayoutResult? = null

    fun DrawScope.draw(asset: MapLiveAsset) {
        val color = paint.colors.affiliation(asset.affiliation)
        if (asset.trail !== trailSource) rebuildTrail(asset.trail)
        if (asset.trail.size > 1) drawPath(trailPath, color, alpha = 0.8f, style = trailStroke)
        val center = Offset(projector.x(asset.position.x), projector.y(asset.position.y))
        drawHeading(paint, center, asset.headingDeg, color)
        paint.assetGlyphs.draw(this, center, asset.affiliation, EntityKind.ASSET, asset.isAir)
        val tag = asset.tag ?: return
        if (tag != tagText) {
            tagText = tag
            tagLayout = paint.tag(tag, color)
        }
        val layout = tagLayout ?: return
        with(paint) { drawTag(layout, center.x + paint.assetGlyphs.extentPx + paint.dp(6f), center.y - paint.tagHeight / 2f) }
    }

    private fun rebuildTrail(trail: List<LocalPoint>) {
        trailSource = trail
        trailPath.rewind()
        for (i in trail.indices) {
            val x = projector.x(trail[i].x)
            val y = projector.y(trail[i].y)
            if (i == 0) trailPath.moveTo(x, y) else trailPath.lineTo(x, y)
        }
    }
}
