package com.wingmanc2.core.ui.map

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.LocalPoint
import kotlin.math.max
import kotlin.math.min

@Immutable
data class MapBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val centerX: Double get() = (minX + maxX) / 2
    val centerY: Double get() = (minY + maxY) / 2

    fun expandedBy(meters: Double) = MapBounds(minX - meters, minY - meters, maxX + meters, maxY + meters)

    fun including(x: Double, y: Double, radius: Double = 0.0) =
        MapBounds(min(minX, x - radius), min(minY, y - radius), max(maxX, x + radius), max(maxY, y + radius))

    companion object {
        val AreaOfOperations = MapBounds(
            -OperationalArea.HALF_EXTENT_M,
            -OperationalArea.HALF_EXTENT_M,
            OperationalArea.HALF_EXTENT_M,
            OperationalArea.HALF_EXTENT_M,
        )

        fun around(points: Iterable<LocalPoint>): MapBounds? {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (p in points) {
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
            return if (minX <= maxX) MapBounds(minX, minY, maxX, maxY) else null
        }
    }
}

@Immutable
data class MapCamera(val centerX: Double, val centerY: Double, val metersPerDp: Double) {
    companion object {
        fun fit(bounds: MapBounds, widthDp: Float, heightDp: Float, paddingDp: Float = 24f, cover: Boolean = false): MapCamera {
            val usableW = max(widthDp - 2 * paddingDp, 1f)
            val usableH = max(heightDp - 2 * paddingDp, 1f)
            val sx = bounds.width / usableW
            val sy = bounds.height / usableH
            val scale = if (cover) min(sx, sy) else max(sx, sy)
            return MapCamera(bounds.centerX, bounds.centerY, max(scale, MIN_METERS_PER_DP))
        }

        private const val MIN_METERS_PER_DP = 0.5
    }
}

@Immutable
sealed interface MapFraming {
    fun resolve(widthDp: Float, heightDp: Float): MapCamera

    @Immutable
    data class Fit(val bounds: MapBounds, val paddingDp: Float = 24f, val cover: Boolean = false) : MapFraming {
        override fun resolve(widthDp: Float, heightDp: Float) = MapCamera.fit(bounds, widthDp, heightDp, paddingDp, cover)
    }

    companion object {
        val AreaOfOperations: MapFraming = Fit(MapBounds.AreaOfOperations, paddingDp = 0f, cover = true)
    }
}

internal class MapProjector(camera: MapCamera, widthPx: Float, heightPx: Float, density: Float) {
    val pxPerMeter: Double = density / camera.metersPerDp
    private val tx = widthPx / 2.0 - camera.centerX * pxPerMeter
    private val ty = heightPx / 2.0 + camera.centerY * pxPerMeter

    fun x(meters: Double): Float = (tx + meters * pxPerMeter).toFloat()

    fun y(meters: Double): Float = (ty - meters * pxPerMeter).toFloat()

    fun metersX(px: Float): Double = (px - tx) / pxPerMeter

    fun metersY(px: Float): Double = (ty - px) / pxPerMeter
}
