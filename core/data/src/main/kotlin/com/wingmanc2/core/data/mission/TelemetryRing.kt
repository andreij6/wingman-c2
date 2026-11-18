package com.wingmanc2.core.data.mission

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.FlownTrack
import com.wingmanc2.core.model.TelemetryFrame

internal class TelemetryRing(private val capacity: Int) {
    private val xs = DoubleArray(capacity)
    private val ys = DoubleArray(capacity)
    private var head = 0
    private var size = 0

    fun append(frame: TelemetryFrame) {
        val point = OperationalArea.projection.toLocal(frame.position)
        xs[head] = point.x
        ys[head] = point.y
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun track(): FlownTrack {
        val oldest = (head - size + capacity) % capacity
        val xy = DoubleArray(size * 2)
        for (offset in 0 until size) {
            val i = (oldest + offset) % capacity
            xy[offset * 2] = xs[i]
            xy[offset * 2 + 1] = ys[i]
        }
        return FlownTrack(xy)
    }
}
