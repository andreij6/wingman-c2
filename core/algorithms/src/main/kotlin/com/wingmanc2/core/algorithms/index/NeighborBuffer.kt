package com.wingmanc2.core.algorithms.index

import kotlin.math.sqrt

internal class NeighborBuffer(initialCapacity: Int = 16) {
    private var ids = LongArray(initialCapacity.coerceAtLeast(1))
    private var dxs = DoubleArray(ids.size)
    private var dys = DoubleArray(ids.size)

    var size = 0
        private set

    fun id(i: Int): Long = ids[i]

    fun distanceM(i: Int): Double = sqrt(dxs[i] * dxs[i] + dys[i] * dys[i])

    internal fun dx(i: Int): Double = dxs[i]

    internal fun dy(i: Int): Double = dys[i]

    internal fun clear() {
        size = 0
    }

    internal fun add(id: Long, dx: Double, dy: Double) {
        if (size == ids.size) {
            ids = ids.copyOf(size shl 1)
            dxs = dxs.copyOf(size shl 1)
            dys = dys.copyOf(size shl 1)
        }
        ids[size] = id
        dxs[size] = dx
        dys[size] = dy
        size++
    }
}
