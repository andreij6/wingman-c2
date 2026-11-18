package com.wingmanc2.core.ui.map

import com.wingmanc2.core.algorithms.geo.OperationalArea

internal class SymbolIndex(symbols: List<MapSymbol>) {
    private val xs = DoubleArray(symbols.size) { symbols[it].position.x }
    private val ys = DoubleArray(symbols.size) { symbols[it].position.y }
    private val cellStart = IntArray(CELLS * CELLS + 1)
    private val entries = IntArray(symbols.size)

    init {
        for (i in xs.indices) cellStart[cellIndex(xs[i], ys[i]) + 1]++
        for (c in 1..CELLS * CELLS) cellStart[c] += cellStart[c - 1]
        val cursor = cellStart.copyOf(CELLS * CELLS)
        for (i in xs.indices) entries[cursor[cellIndex(xs[i], ys[i])]++] = i
    }

    fun nearest(x: Double, y: Double, radius: Double): Int {
        val c0 = cellOf(x - radius)
        val c1 = cellOf(x + radius)
        val r0 = cellOf(y - radius)
        val r1 = cellOf(y + radius)
        var best = -1
        var bestDistSq = radius * radius
        for (row in r0..r1) {
            for (col in c0..c1) {
                val cell = row * CELLS + col
                for (e in cellStart[cell] until cellStart[cell + 1]) {
                    val i = entries[e]
                    val dx = xs[i] - x
                    val dy = ys[i] - y
                    val d = dx * dx + dy * dy
                    if (d <= bestDistSq) {
                        bestDistSq = d
                        best = i
                    }
                }
            }
        }
        return best
    }

    private fun cellIndex(x: Double, y: Double) = cellOf(y) * CELLS + cellOf(x)

    private fun cellOf(v: Double) = ((v + OperationalArea.HALF_EXTENT_M) / CELL_M).toInt().coerceIn(0, CELLS - 1)

    private companion object {
        const val CELLS = 16
        const val CELL_M = OperationalArea.EXTENT_M / CELLS
    }
}
