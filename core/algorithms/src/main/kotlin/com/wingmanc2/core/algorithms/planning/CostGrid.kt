package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.geo.Terrain
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

internal enum class ExposureLayer { MASKED, TRANSIT }

internal class CostGrid(val resolution: Int = DEFAULT_RESOLUTION) {
    val cellCount = resolution * resolution
    val cellSizeM = OperationalArea.EXTENT_M / resolution
    private val terrainMslM = FloatArray(cellCount) { Terrain.elevation(centerX(colOf(it)), centerY(rowOf(it))).toFloat() }
    private val leePenaltyBySide = Array(2) { side ->
        val leeCrestOffsetM = if (side == 0) -LEE_OFFSET_M else LEE_OFFSET_M
        FloatArray(cellCount) {
            val d = Terrain.signedDistanceToCrest(centerX(colOf(it)), centerY(rowOf(it))) - leeCrestOffsetM
            (1.0 - exp(-d * d * INV_2_LEE_WIDTH2)).toFloat()
        }
    }
    private val exposureLayers = Array(ExposureLayer.entries.size) { FloatArray(cellCount) }
    private val blocked = BooleanArray(cellCount)
    private var crossings = DoubleArray(8)
    private var exposure = exposureLayers[0]
    private var leePenalty = leePenaltyBySide[0]
    private var exposureWeight = 0f
    private var maskWeight = 0f

    fun reset(targetX: Double, targetY: Double): CostGrid = apply {
        exposureLayers.forEach { it.fill(0f) }
        blocked.fill(false)
        leePenalty = leePenaltyBySide[if (Terrain.signedDistanceToCrest(targetX, targetY) >= 0.0) 0 else 1]
    }

    fun weigh(layer: ExposureLayer, exposureWeight: Float, maskWeight: Float): CostGrid = apply {
        exposure = exposureLayers[layer.ordinal]
        this.exposureWeight = exposureWeight
        this.maskWeight = maskWeight
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

    fun addRadarExposure(radar: RadarHorizon, altitudeAglM: Double, layer: ExposureLayer): CostGrid = apply {
        val target = exposureLayers[layer.ordinal]
        val r = radar.radiusM
        for (row in rowOf(radar.y - r)..rowOf(radar.y + r)) {
            val cy = centerY(row)
            for (col in colOf(radar.x - r)..colOf(radar.x + r)) {
                val i = index(col, row)
                val e = radar.exposure(centerX(col), cy, terrainMslM[i] + altitudeAglM)
                if (e > target[i]) target[i] = e
            }
        }
    }

    fun isBlocked(index: Int): Boolean = blocked[index]

    fun cost(index: Int): Float =
        if (blocked[index]) Float.POSITIVE_INFINITY else 1f + exposureWeight * exposure[index] + maskWeight * leePenalty[index]

    fun minCost(): Float {
        var min = Float.POSITIVE_INFINITY
        for (i in 0 until cellCount) {
            val c = cost(i)
            if (c < min) min = c
        }
        return if (min == Float.POSITIVE_INFINITY) 1f else min
    }

    fun lineCost(from: Int, to: Int, budget: Float): Float {
        var col = colOf(from)
        var row = rowOf(from)
        val endCol = colOf(to)
        val endRow = rowOf(to)
        val dc = endCol - col
        val dr = endRow - row
        val lengthM = hypot(dc.toDouble(), dr.toDouble()) * cellSizeM
        val stepCol = if (dc > 0) 1 else -1
        val stepRow = if (dr > 0) 1 else -1
        val tDeltaCol = if (dc == 0) Double.POSITIVE_INFINITY else 1.0 / abs(dc)
        val tDeltaRow = if (dr == 0) Double.POSITIVE_INFINITY else 1.0 / abs(dr)
        var tMaxCol = 0.5 * tDeltaCol
        var tMaxRow = 0.5 * tDeltaRow
        var t = 0.0
        var total = 0.0
        while (true) {
            val c = cost(index(col, row))
            if (c == Float.POSITIVE_INFINITY) return c
            val tNext = min(1.0, min(tMaxCol, tMaxRow))
            total += c * (tNext - t) * lengthM
            if (total > budget) return Float.POSITIVE_INFINITY
            if (tNext >= 1.0) return total.toFloat()
            t = tNext
            when {
                tMaxCol < tMaxRow -> {
                    col += stepCol
                    tMaxCol += tDeltaCol
                }
                tMaxRow < tMaxCol -> {
                    row += stepRow
                    tMaxRow += tDeltaRow
                }
                else -> {
                    if (blocked[index(col + stepCol, row)] || blocked[index(col, row + stepRow)]) return Float.POSITIVE_INFINITY
                    col += stepCol
                    row += stepRow
                    tMaxCol += tDeltaCol
                    tMaxRow += tDeltaRow
                }
            }
        }
    }

    fun hiddenVantage(
        targetX: Double,
        targetY: Double,
        minRangeM: Double,
        maxRangeM: Double,
        viewshed: RadarHorizon,
        popupAglM: Double,
    ): Int {
        var best = -1
        var bestPenalty = Float.POSITIVE_INFINITY
        var bestRange = Double.POSITIVE_INFINITY
        forEachInBand(targetX, targetY, minRangeM, maxRangeM) { i, cx, cy, range2 ->
            val penalty = leePenalty[i]
            val better = penalty < bestPenalty || penalty == bestPenalty && range2 < bestRange
            if (better && exposure[i] == 0f && viewshed.exposure(cx, cy, terrainMslM[i] + popupAglM) > 0f) {
                best = i
                bestPenalty = penalty
                bestRange = range2
            }
        }
        return best
    }

    fun leastExposedCell(
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
        forEachInBand(targetX, targetY, minRangeM, maxRangeM) { i, cx, cy, range2 ->
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
        return best
    }

    private inline fun forEachInBand(
        targetX: Double,
        targetY: Double,
        minRangeM: Double,
        maxRangeM: Double,
        visit: (index: Int, cx: Double, cy: Double, range2: Double) -> Unit,
    ) {
        val min2 = minRangeM * minRangeM
        val max2 = maxRangeM * maxRangeM
        for (row in rowOf(targetY - maxRangeM)..rowOf(targetY + maxRangeM)) {
            val cy = centerY(row)
            for (col in colOf(targetX - maxRangeM)..colOf(targetX + maxRangeM)) {
                val i = index(col, row)
                if (blocked[i]) continue
                val cx = centerX(col)
                val range2 = sq(cx - targetX) + sq(cy - targetY)
                if (range2 in min2..max2) visit(i, cx, cy, range2)
            }
        }
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
        private const val LEE_OFFSET_M = 150.0
        private const val LEE_WIDTH_M = 120.0
        private const val INV_2_LEE_WIDTH2 = 1.0 / (2 * LEE_WIDTH_M * LEE_WIDTH_M)
    }
}
