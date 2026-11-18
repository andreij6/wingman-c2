package com.wingmanc2.core.data.mission

import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.TelemetryFrame

internal class TelemetryRing(private val capacity: Int) {
    private val tMs = LongArray(capacity)
    private val lat = DoubleArray(capacity)
    private val lon = DoubleArray(capacity)
    private val heading = DoubleArray(capacity)
    private val altitude = DoubleArray(capacity)
    private val airspeed = DoubleArray(capacity)
    private val climb = DoubleArray(capacity)
    private val battery = DoubleArray(capacity)
    private val snr = DoubleArray(capacity)
    private val waypointIndex = IntArray(capacity)
    private val waypointCount = IntArray(capacity)
    private var head = 0
    private var size = 0

    fun append(frame: TelemetryFrame) {
        tMs[head] = frame.tMs
        lat[head] = frame.position.lat
        lon[head] = frame.position.lon
        heading[head] = frame.headingDeg
        altitude[head] = frame.altitudeAglM
        airspeed[head] = frame.airspeedMps
        climb[head] = frame.climbMps
        battery[head] = frame.batteryPct
        snr[head] = frame.linkSnrDb
        waypointIndex[head] = frame.waypointIndex
        waypointCount[head] = frame.waypointCount
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun snapshot(): List<TelemetryFrame> {
        val oldest = (head - size + capacity) % capacity
        return List(size) { offset ->
            val i = (oldest + offset) % capacity
            TelemetryFrame(
                tMs = tMs[i],
                position = GeoPoint(lat[i], lon[i]),
                headingDeg = heading[i],
                altitudeAglM = altitude[i],
                airspeedMps = airspeed[i],
                climbMps = climb[i],
                batteryPct = battery[i],
                linkSnrDb = snr[i],
                waypointIndex = waypointIndex[i],
                waypointCount = waypointCount[i],
            )
        }
    }
}
