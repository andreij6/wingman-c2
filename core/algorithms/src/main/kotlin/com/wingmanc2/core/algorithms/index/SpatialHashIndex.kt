package com.wingmanc2.core.algorithms.index

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

class SpatialHashIndex(val cellSizeM: Double, expectedSize: Int = DEFAULT_CAPACITY) {
    private val inverseCell = 1.0 / cellSizeM
    private val slotById = LongIntHashMap(expectedSize)
    private val headByCell = LongIntHashMap(expectedSize)

    private var ids = LongArray(expectedSize.coerceAtLeast(MIN_CAPACITY))
    private var xs = DoubleArray(ids.size)
    private var ys = DoubleArray(ids.size)
    private var cellKeys = LongArray(ids.size)
    private var nextInCell = IntArray(ids.size)
    private var previousInCell = IntArray(ids.size)
    private var highWater = 0
    private var freeHead = NIL

    private var minCx = Int.MAX_VALUE
    private var maxCx = Int.MIN_VALUE
    private var minCy = Int.MAX_VALUE
    private var maxCy = Int.MIN_VALUE

    init {
        require(cellSizeM > 0.0) { "cellSizeM must be positive" }
    }

    val size: Int get() = slotById.size

    operator fun contains(id: Long): Boolean = slotById[id] != NIL

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
        val slot = allocate()
        ids[slot] = id
        xs[slot] = x
        ys[slot] = y
        slotById[id] = slot
        link(slot, key, cx, cy)
    }

    fun remove(id: Long): Boolean {
        val slot = slotById.remove(id)
        if (slot == NIL) return false
        unlink(slot)
        previousInCell[slot] = FREE
        nextInCell[slot] = freeHead
        freeHead = slot
        return true
    }

    fun clear() {
        slotById.clear()
        headByCell.clear()
        highWater = 0
        freeHead = NIL
        minCx = Int.MAX_VALUE
        maxCx = Int.MIN_VALUE
        minCy = Int.MAX_VALUE
        maxCy = Int.MIN_VALUE
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
            forEachLiveSlot { slot -> collect(slot, x, y, radiusSq, out) }
        } else {
            for (cx in cx0..cx1) for (cy in cy0..cy1) forEachInCell(cx, cy) { slot -> collect(slot, x, y, radiusSq, out) }
        }
        return out.size
    }

    fun nearest(x: Double, y: Double, maxDistanceM: Double = Double.POSITIVE_INFINITY): Neighbor? {
        if (size == 0) return null
        val qcx = cellOf(x)
        val qcy = cellOf(y)
        val maxRing = maxOf(
            abs(qcx.toLong() - minCx), abs(maxCx.toLong() - qcx),
            abs(qcy.toLong() - minCy), abs(maxCy.toLong() - qcy),
        )
        var bestSlot = NIL
        var bestSq = maxDistanceM * maxDistanceM
        val ringCells = (2 * maxRing + 1).let { it * it }
        if (ringCells > BRUTE_FORCE_FACTOR.toLong() * size) {
            forEachLiveSlot { slot ->
                val d = distanceSq(slot, x, y)
                if (d <= bestSq) {
                    bestSq = d
                    bestSlot = slot
                }
            }
        } else {
            for (ring in 0..maxRing.toInt()) {
                forEachInRing(qcx, qcy, ring) { slot ->
                    val d = distanceSq(slot, x, y)
                    if (d <= bestSq) {
                        bestSq = d
                        bestSlot = slot
                    }
                }
                val reach = ring * cellSizeM
                if (bestSq <= reach * reach) break
            }
        }
        return if (bestSlot == NIL) null else Neighbor(ids[bestSlot], sqrt(bestSq))
    }

    private inline fun forEachInRing(qcx: Int, qcy: Int, ring: Int, action: (Int) -> Unit) {
        if (ring == 0) {
            forEachInCell(qcx, qcy, action)
            return
        }
        for (cx in qcx - ring..qcx + ring) {
            forEachInCell(cx, qcy - ring, action)
            forEachInCell(cx, qcy + ring, action)
        }
        for (cy in qcy - ring + 1 until qcy + ring) {
            forEachInCell(qcx - ring, cy, action)
            forEachInCell(qcx + ring, cy, action)
        }
    }

    private inline fun forEachInCell(cx: Int, cy: Int, action: (Int) -> Unit) {
        if (cx < minCx || cx > maxCx || cy < minCy || cy > maxCy) return
        var slot = headByCell[packCell(cx, cy)]
        while (slot != NIL) {
            action(slot)
            slot = nextInCell[slot]
        }
    }

    private inline fun forEachLiveSlot(action: (Int) -> Unit) {
        for (slot in 0 until highWater) if (previousInCell[slot] != FREE) action(slot)
    }

    private fun collect(slot: Int, x: Double, y: Double, radiusSq: Double, out: NeighborBuffer) {
        val d = distanceSq(slot, x, y)
        if (d <= radiusSq) out.add(ids[slot], d)
    }

    private fun distanceSq(slot: Int, x: Double, y: Double): Double {
        val dx = xs[slot] - x
        val dy = ys[slot] - y
        return dx * dx + dy * dy
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

    private fun allocate(): Int {
        if (freeHead != NIL) {
            val slot = freeHead
            freeHead = nextInCell[slot]
            return slot
        }
        if (highWater == ids.size) growSlots()
        return highWater++
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

    class Builder(private val cellSizeM: Double, expectedSize: Int = DEFAULT_CAPACITY) {
        private var ids = LongArray(expectedSize.coerceAtLeast(MIN_CAPACITY))
        private var xs = DoubleArray(ids.size)
        private var ys = DoubleArray(ids.size)
        private var count = 0

        fun add(id: Long, x: Double, y: Double): Builder = apply {
            if (count == ids.size) {
                ids = ids.copyOf(count shl 1)
                xs = xs.copyOf(count shl 1)
                ys = ys.copyOf(count shl 1)
            }
            ids[count] = id
            xs[count] = x
            ys[count] = y
            count++
        }

        fun build(): SpatialHashIndex = SpatialHashIndex(cellSizeM, count).also { index ->
            for (i in 0 until count) index.put(ids[i], xs[i], ys[i])
        }
    }

    private companion object {
        const val NIL = LongIntHashMap.ABSENT
        const val FREE = -2
        const val MIN_CAPACITY = 8
        const val DEFAULT_CAPACITY = 64
        const val BRUTE_FORCE_FACTOR = 4

        fun packCell(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFF_FFFFL)
    }
}
