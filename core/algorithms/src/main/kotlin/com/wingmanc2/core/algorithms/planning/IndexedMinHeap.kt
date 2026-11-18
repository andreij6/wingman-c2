package com.wingmanc2.core.algorithms.planning

internal class IndexedMinHeap(capacity: Int) {
    private val nodes = IntArray(capacity)
    private val slotOf = IntArray(capacity)
    private val keys = FloatArray(capacity)
    var size = 0
        private set

    fun isEmpty() = size == 0

    fun clear() {
        size = 0
    }

    fun push(node: Int, key: Float) {
        keys[node] = key
        nodes[size] = node
        slotOf[node] = size
        siftUp(size++)
    }

    fun decreaseKey(node: Int, key: Float) {
        keys[node] = key
        siftUp(slotOf[node])
    }

    fun pop(): Int {
        val top = nodes[0]
        val last = nodes[--size]
        if (size > 0) {
            nodes[0] = last
            slotOf[last] = 0
            siftDown(0)
        }
        return top
    }

    private fun siftUp(start: Int) {
        var slot = start
        val node = nodes[slot]
        val key = keys[node]
        while (slot > 0) {
            val parentSlot = (slot - 1) ushr 1
            val parent = nodes[parentSlot]
            if (keys[parent] <= key) break
            nodes[slot] = parent
            slotOf[parent] = slot
            slot = parentSlot
        }
        nodes[slot] = node
        slotOf[node] = slot
    }

    private fun siftDown(start: Int) {
        var slot = start
        val node = nodes[slot]
        val key = keys[node]
        while (true) {
            var child = 2 * slot + 1
            if (child >= size) break
            if (child + 1 < size && keys[nodes[child + 1]] < keys[nodes[child]]) child++
            val childNode = nodes[child]
            if (keys[childNode] >= key) break
            nodes[slot] = childNode
            slotOf[childNode] = slot
            slot = child
        }
        nodes[slot] = node
        slotOf[node] = slot
    }
}
