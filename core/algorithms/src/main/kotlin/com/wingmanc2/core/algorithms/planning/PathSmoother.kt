package com.wingmanc2.core.algorithms.planning

import kotlin.math.abs
import kotlin.math.max

internal class PathSmoother(private val grid: CostGrid) {

    fun simplify(path: IntArray, exposureWeight: Float): IntArray {
        if (path.size <= 2) return path
        val kept = IntArray(path.size)
        var count = 0
        kept[count++] = path[0]
        var anchor = 0
        var bound = grid.cost(path[1], exposureWeight)
        for (k in 1 until path.size - 1) {
            val next = k + 1
            val widened = max(bound, grid.cost(path[next], exposureWeight))
            if (lineWithin(path[anchor], path[next], widened, exposureWeight)) {
                bound = widened
            } else {
                kept[count++] = path[k]
                anchor = k
                bound = grid.cost(path[next], exposureWeight)
            }
        }
        kept[count++] = path[path.size - 1]
        return kept.copyOf(count)
    }

    private fun lineWithin(from: Int, to: Int, bound: Float, exposureWeight: Float): Boolean {
        var col = grid.colOf(from)
        var row = grid.rowOf(from)
        val endCol = grid.colOf(to)
        val endRow = grid.rowOf(to)
        val dc = abs(endCol - col)
        val dr = -abs(endRow - row)
        val sc = if (col < endCol) 1 else -1
        val sr = if (row < endRow) 1 else -1
        var err = dc + dr
        val limit = bound + TOLERANCE
        while (col != endCol || row != endRow) {
            val e2 = 2 * err
            val stepCol = e2 >= dr
            val stepRow = e2 <= dc
            if (stepCol && stepRow && (grid.isBlocked(grid.index(col + sc, row)) || grid.isBlocked(grid.index(col, row + sr)))) return false
            if (stepCol) {
                err += dr
                col += sc
            }
            if (stepRow) {
                err += dc
                row += sr
            }
            if (grid.cost(grid.index(col, row), exposureWeight) > limit) return false
        }
        return true
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
