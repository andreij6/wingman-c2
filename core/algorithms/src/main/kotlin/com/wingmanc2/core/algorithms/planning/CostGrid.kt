package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.geo.Terrain
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

class CostGrid(val resolution: Int = DEFAULT_RESOLUTION) {
    val cellCount = resolution * resolution
    val cellSizeM = OperationalArea.EXTENT_M / resolution
    private val terrainMslM = FloatArray(cellCount) { Terrain.elevation(centerX(colOf(it)), centerY(rowOf(it))).toFloat() }
    private val exposure = FloatArray(cellCount)
    private val blocked = BooleanArray(cellCount)
    private var crossings = DoubleArray(8)

    fun reset(): CostGrid = apply {
        exposure.fill(0f)
        blocked.fill(false)
    }

    fun blockDisk(cx: Double, cy: Double, radiusM: Double): CostGrid = apply {
        for (row in rowOf(cy - radiusM)..rowOf(cy + radiusM)) {
            val dy = centerY(row) - cy
            if (dy * dy > radiusM * radiusM) continue
            val half = sqrt(radiusM * radiusM - dy * dy)
            fillSpan(row, cx - half, cx + half)
        }
    }

    fun blockPolygon(xs: DoubleArray, ys: DoubleArray): CostGrid = apply {
        val n = xs.size
        if (n < 3) return@apply
        if (crossings.size < n) crossings = DoubleArray(n)
        for (row in rowOf(ys.min())..rowOf(ys.max())) {
            val cy = centerY(row)
            var count = 0
            var j = n - 1
            for (i in 0 until n) {
                if ((ys[i] > cy) != (ys[j] > cy)) {
                    crossings[count++] = xs[i] + (cy - ys[i]) * (xs[j] - xs[i]) / (ys[j] - ys[i])
                }
                j = i
            }
            crossings.sort(0, count)
            for (k in 0 until count - 1 step 2) fillSpan(row, crossings[k], crossings[k + 1])
        }
    }

    fun addRadarExposure(radar: RadarHorizon, altitudeAglM: Double): CostGrid = apply {
        val r = radar.radiusM
        for (row in rowOf(radar.y - r)..rowOf(radar.y + r)) {
            val cy = centerY(row)
            for (col in colOf(radar.x - r)..colOf(radar.x + r)) {
                val i = index(col, row)
                val e = radar.exposure(centerX(col), cy, terrainMslM[i] + altitudeAglM)
                if (e > exposure[i]) exposure[i] = e
            }
        }
    }

    fun isBlocked(index: Int): Boolean = blocked[index]

    fun cost(index: Int, exposureWeight: Float): Float =
        if (blocked[index]) Float.POSITIVE_INFINITY else 1f + exposureWeight * exposure[index]

    fun minCost(exposureWeight: Float): Float {
        var min = Float.POSITIVE_INFINITY
        for (i in 0 until cellCount) if (!blocked[i] && exposure[i] < min) min = exposure[i]
        return if (min == Float.POSITIVE_INFINITY) 1f else 1f + exposureWeight * min
    }

    fun observationCell(
        targetX: Double,
        targetY: Double,
        minRangeM: Double,
        maxRangeM: Double,
        fromX: Double,
        fromY: Double,
    ): Int {
        var best = -1
        var bestExposure = Float.POSITIVE_INFINITY
        var bestRange = Double.POSITIVE_INFINITY
        var bestApproach = Double.POSITIVE_INFINITY
        val min2 = minRangeM * minRangeM
        val max2 = maxRangeM * maxRangeM
        for (row in rowOf(targetY - maxRangeM)..rowOf(targetY + maxRangeM)) {
            val cy = centerY(row)
            for (col in colOf(targetX - maxRangeM)..colOf(targetX + maxRangeM)) {
                val i = index(col, row)
                if (blocked[i]) continue
                val cx = centerX(col)
                val range2 = sq(cx - targetX) + sq(cy - targetY)
                if (range2 < min2 || range2 > max2) continue
                val approach2 = sq(cx - fromX) + sq(cy - fromY)
                val e = exposure[i]
                val better = e < bestExposure ||
                    e == bestExposure && (range2 < bestRange || range2 == bestRange && approach2 < bestApproach)
                if (better) {
                    best = i
                    bestExposure = e
                    bestRange = range2
                    bestApproach = approach2
                }
            }
        }
        return best
    }

    fun cellAt(x: Double, y: Double): Int = index(colOf(x), rowOf(y))

    fun index(col: Int, row: Int): Int = row * resolution + col

    fun colOf(index: Int): Int = index % resolution

    fun rowOf(index: Int): Int = index / resolution

    fun centerX(col: Int): Double = -OperationalArea.HALF_EXTENT_M + (col + 0.5) * cellSizeM

    fun centerY(row: Int): Double = -OperationalArea.HALF_EXTENT_M + (row + 0.5) * cellSizeM

    private fun colOf(x: Double): Int = clampCell(floor((x + OperationalArea.HALF_EXTENT_M) / cellSizeM).toInt())

    private fun rowOf(y: Double): Int = clampCell(floor((y + OperationalArea.HALF_EXTENT_M) / cellSizeM).toInt())

    private fun clampCell(c: Int): Int = c.coerceIn(0, resolution - 1)

    private fun fillSpan(row: Int, fromX: Double, toX: Double) {
        val first = ceil((fromX + OperationalArea.HALF_EXTENT_M) / cellSizeM - 0.5).toInt().coerceAtLeast(0)
        val last = floor((toX + OperationalArea.HALF_EXTENT_M) / cellSizeM - 0.5).toInt().coerceAtMost(resolution - 1)
        val base = row * resolution
        for (col in first..last) blocked[base + col] = true
    }

    private fun sq(v: Double) = v * v

    companion object {
        const val DEFAULT_RESOLUTION = 128
    }
}
