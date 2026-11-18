package com.wingmanc2.core.algorithms.planning

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class GridAStar(private val grid: CostGrid) {
    private val gScore = FloatArray(grid.cellCount)
    private val parent = IntArray(grid.cellCount)
    private val mark = IntArray(grid.cellCount)
    private val open = IndexedMinHeap(grid.cellCount)
    private var generation = 0

    fun costTo(cell: Int): Float = gScore[cell]

    fun search(start: Int, goal: Int): IntArray {
        gScore[start] = 0f
        if (start == goal) return intArrayOf(start)
        if (grid.isBlocked(goal)) return IntArray(0)
        val openMark = nextOpenMark()
        val closedMark = openMark + 1
        val cell = grid.cellSizeM.toFloat()
        val unit = cell * grid.minCost() * TIE_BREAK
        val n = grid.resolution
        val goalCol = grid.colOf(goal)
        val goalRow = grid.rowOf(goal)
        open.clear()
        parent[start] = start
        mark[start] = openMark
        open.push(start, unit * octile(grid.colOf(start) - goalCol, grid.rowOf(start) - goalRow))
        while (!open.isEmpty()) {
            val current = open.pop()
            if (current == goal) return reconstruct(start, goal)
            mark[current] = closedMark
            val col = grid.colOf(current)
            val row = grid.rowOf(current)
            val hereCost = grid.cost(current)
            for (k in 0 until NEIGHBOURS) {
                val nc = col + DC[k]
                val nr = row + DR[k]
                if (nc < 0 || nr < 0 || nc >= n || nr >= n) continue
                val next = grid.index(nc, nr)
                if (mark[next] == closedMark) continue
                val stepCost = grid.cost(next)
                if (stepCost == Float.POSITIVE_INFINITY) continue
                val diagonal = k >= ORTHOGONAL
                if (diagonal && (grid.isBlocked(grid.index(nc, row)) || grid.isBlocked(grid.index(col, nr)))) continue
                val meanCost = if (hereCost == Float.POSITIVE_INFINITY) stepCost else 0.5f * (hereCost + stepCost)
                val tentative = gScore[current] + meanCost * cell * (if (diagonal) SQRT2 else 1f)
                val discovered = mark[next] == openMark
                if (discovered && tentative >= gScore[next]) continue
                gScore[next] = tentative
                parent[next] = current
                val f = tentative + unit * octile(nc - goalCol, nr - goalRow)
                if (discovered) open.decreaseKey(next, f) else {
                    mark[next] = openMark
                    open.push(next, f)
                }
            }
        }
        return IntArray(0)
    }

    private fun reconstruct(start: Int, goal: Int): IntArray {
        var length = 1
        var node = goal
        while (node != start) {
            node = parent[node]
            length++
        }
        val path = IntArray(length)
        node = goal
        for (i in length - 1 downTo 0) {
            path[i] = node
            node = parent[node]
        }
        return path
    }

    private fun nextOpenMark(): Int {
        if (generation >= Int.MAX_VALUE / 2 - 1) {
            mark.fill(0)
            generation = 0
        }
        generation++
        return generation * 2
    }

    private fun octile(dc: Int, dr: Int): Float {
        val a = abs(dc)
        val b = abs(dr)
        return max(a, b) + (SQRT2 - 1f) * min(a, b)
    }

    private companion object {
        const val SQRT2 = 1.4142135f
        const val TIE_BREAK = 1.001f
        const val NEIGHBOURS = 8
        const val ORTHOGONAL = 4
        val DC = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val DR = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    }
}
