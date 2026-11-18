package com.wingmanc2.core.algorithms.index

import com.wingmanc2.core.algorithms.random.Mix64

internal class LongIntHashMap(expectedSize: Int) {
    private var keys = LongArray(0)
    private var values = IntArray(0)
    private var mask = 0

    var size = 0
        private set

    init {
        allocate(capacityFor(expectedSize))
    }

    operator fun get(key: Long): Int {
        var i = home(key)
        while (true) {
            val value = values[i]
            if (value == ABSENT || keys[i] == key) return value
            i = (i + 1) and mask
        }
    }

    operator fun set(key: Long, value: Int) {
        var i = home(key)
        while (values[i] != ABSENT) {
            if (keys[i] == key) {
                values[i] = value
                return
            }
            i = (i + 1) and mask
        }
        keys[i] = key
        values[i] = value
        if (++size > values.size ushr 1) grow()
    }

    fun remove(key: Long): Int {
        var hole = home(key)
        while (true) {
            val value = values[hole]
            if (value == ABSENT) return ABSENT
            if (keys[hole] == key) break
            hole = (hole + 1) and mask
        }
        val removed = values[hole]
        var j = (hole + 1) and mask
        while (values[j] != ABSENT) {
            val distanceFromHome = (j - home(keys[j])) and mask
            if (distanceFromHome >= ((j - hole) and mask)) {
                keys[hole] = keys[j]
                values[hole] = values[j]
                hole = j
            }
            j = (j + 1) and mask
        }
        values[hole] = ABSENT
        size--
        return removed
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        allocate(oldValues.size shl 1)
        for (i in oldValues.indices) {
            if (oldValues[i] == ABSENT) continue
            var j = home(oldKeys[i])
            while (values[j] != ABSENT) j = (j + 1) and mask
            keys[j] = oldKeys[i]
            values[j] = oldValues[i]
        }
    }

    private fun allocate(capacity: Int) {
        keys = LongArray(capacity)
        values = IntArray(capacity).apply { fill(ABSENT) }
        mask = capacity - 1
    }

    private fun home(key: Long): Int = Mix64.fmix(key).toInt() and mask

    companion object {
        const val ABSENT = -1

        private fun capacityFor(expectedSize: Int): Int {
            var capacity = 8
            while (capacity < expectedSize * 2) capacity = capacity shl 1
            return capacity
        }
    }
}
