package com.wingmanc2.core.algorithms.index

import kotlin.math.floor

internal class SpatialHashIndex(cellSizeM: Double, expectedSize: Int = DEFAULT_CAPACITY) {
    private val inverseCell = 1.0 / cellSizeM
    private val slotById = LongIntHashMap(expectedSize)
    private val headByCell = LongIntHashMap(expectedSize)

    private var ids = LongArray(expectedSize.coerceAtLeast(MIN_CAPACITY))
    private var xs = DoubleArray(ids.size)
    private var ys = DoubleArray(ids.size)
    private var cellKeys = LongArray(ids.size)
    private var nextInCell = IntArray(ids.size)
    private var previousInCell = IntArray(ids.size)

    private var minCx = Int.MAX_VALUE
    private var maxCx = Int.MIN_VALUE
    private var minCy = Int.MAX_VALUE
    private var maxCy = Int.MIN_VALUE

    init {
        require(cellSizeM > 0.0) { "cellSizeM must be positive" }
    }

    private val size: Int get() = slotById.size

    fun put(id: Long, x: Double, y: Double) {
        val cx = cellOf(x)
        val cy = cellOf(y)
        val key = packCell(cx, cy)
        val existing = slotById[id]
        if (existing != NIL) {
            xs[existing] = x
            ys[existing] = y
            if (cellKeys[existing] != key) {
                unlink(existing)
                link(existing, key, cx, cy)
            }
            return
        }
        val slot = size
        if (slot == ids.size) growSlots()
        ids[slot] = id
        xs[slot] = x
        ys[slot] = y
        slotById[id] = slot
        link(slot, key, cx, cy)
    }

    fun queryRadius(x: Double, y: Double, radiusM: Double, out: NeighborBuffer): Int {
        out.clear()
        if (size == 0 || radiusM < 0.0) return 0
        val radiusSq = radiusM * radiusM
        val cx0 = clampedCell(x - radiusM, minCx, maxCx)
        val cx1 = clampedCell(x + radiusM, minCx, maxCx)
        val cy0 = clampedCell(y - radiusM, minCy, maxCy)
        val cy1 = clampedCell(y + radiusM, minCy, maxCy)
        if (cx0 > cx1 || cy0 > cy1) return 0
        val cells = (cx1 - cx0 + 1).toLong() * (cy1 - cy0 + 1)
        if (cells > size) {
            for (slot in 0 until size) collect(slot, x, y, radiusSq, out)
        } else {
            for (cx in cx0..cx1) for (cy in cy0..cy1) forEachInCell(cx, cy) { slot -> collect(slot, x, y, radiusSq, out) }
        }
        return out.size
    }

    private inline fun forEachInCell(cx: Int, cy: Int, action: (Int) -> Unit) {
        if (cx < minCx || cx > maxCx || cy < minCy || cy > maxCy) return
        var slot = headByCell[packCell(cx, cy)]
        while (slot != NIL) {
            action(slot)
            slot = nextInCell[slot]
        }
    }

    private fun collect(slot: Int, x: Double, y: Double, radiusSq: Double, out: NeighborBuffer) {
        val dx = xs[slot] - x
        val dy = ys[slot] - y
        if (dx * dx + dy * dy <= radiusSq) out.add(ids[slot], dx, dy)
    }

    private fun link(slot: Int, key: Long, cx: Int, cy: Int) {
        val head = headByCell[key]
        cellKeys[slot] = key
        previousInCell[slot] = NIL
        nextInCell[slot] = head
        if (head != NIL) previousInCell[head] = slot
        headByCell[key] = slot
        if (cx < minCx) minCx = cx
        if (cx > maxCx) maxCx = cx
        if (cy < minCy) minCy = cy
        if (cy > maxCy) maxCy = cy
    }

    private fun unlink(slot: Int) {
        val previous = previousInCell[slot]
        val next = nextInCell[slot]
        when {
            previous != NIL -> nextInCell[previous] = next
            next != NIL -> headByCell[cellKeys[slot]] = next
            else -> headByCell.remove(cellKeys[slot])
        }
        if (next != NIL) previousInCell[next] = previous
    }

    private fun growSlots() {
        val capacity = ids.size shl 1
        ids = ids.copyOf(capacity)
        xs = xs.copyOf(capacity)
        ys = ys.copyOf(capacity)
        cellKeys = cellKeys.copyOf(capacity)
        nextInCell = nextInCell.copyOf(capacity)
        previousInCell = previousInCell.copyOf(capacity)
    }

    private fun cellOf(v: Double): Int = floor(v * inverseCell).toInt()

    private fun clampedCell(v: Double, lo: Int, hi: Int): Int =
        floor(v * inverseCell).coerceIn(lo.toDouble() - 1.0, hi.toDouble() + 1.0).toInt()

    private companion object {
        const val NIL = LongIntHashMap.ABSENT
        const val MIN_CAPACITY = 8
        const val DEFAULT_CAPACITY = 64

        fun packCell(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)
    }
}
