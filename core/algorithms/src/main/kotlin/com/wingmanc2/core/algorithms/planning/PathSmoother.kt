package com.wingmanc2.core.algorithms.planning

internal class PathSmoother(private val grid: CostGrid, private val search: GridAStar) {

    fun simplify(path: IntArray): IntArray {
        if (path.size <= 2) return path
        val kept = IntArray(path.size)
        var count = 0
        kept[count++] = path[0]
        var anchor = 0
        for (k in 1 until path.size - 1) {
            if (!shortcutHolds(path[anchor], path[k + 1])) {
                kept[count++] = path[k]
                anchor = k
            }
        }
        kept[count++] = path[path.size - 1]
        return kept.copyOf(count)
    }

    private fun shortcutHolds(from: Int, to: Int): Boolean {
        val budget = (search.costTo(to) - search.costTo(from)) * (1f + RELATIVE_TOLERANCE) + ABSOLUTE_TOLERANCE
        return grid.lineCost(from, to, budget) <= budget
    }

    private companion object {
        const val RELATIVE_TOLERANCE = 1e-4f
        const val ABSOLUTE_TOLERANCE = 0.05f
    }
}
