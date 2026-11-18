package com.wingmanc2.core.algorithms.index

import kotlin.math.sqrt

class NeighborBuffer(initialCapacity: Int = 16) {
    private var ids = LongArray(initialCapacity.coerceAtLeast(1))
    private var distancesSq = DoubleArray(ids.size)

    var size = 0
        private set

    fun id(i: Int): Long = ids[i]

    fun distanceM(i: Int): Double = sqrt(distancesSq[i])

    internal fun clear() {
        size = 0
    }

    internal fun add(id: Long, distanceSq: Double) {
        if (size == ids.size) {
            ids = ids.copyOf(size shl 1)
            distancesSq = distancesSq.copyOf(size shl 1)
        }
        ids[size] = id
        distancesSq[size] = distanceSq
        size++
    }
}

data class Neighbor(val id: Long, val distanceM: Double)
