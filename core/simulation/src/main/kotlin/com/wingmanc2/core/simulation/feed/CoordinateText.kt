package com.wingmanc2.core.simulation.feed

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

internal object CoordinateText {
    fun decimal(value: Double): String = String.format(Locale.ROOT, "%.5f", value)

    fun one(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    fun latitudeDm(lat: Double): String = degreesMinutes(lat, if (lat >= 0) 'N' else 'S', 2)

    fun longitudeDm(lon: Double): String = degreesMinutes(lon, if (lon >= 0) 'E' else 'W', 3)

    private fun degreesMinutes(value: Double, hemisphere: Char, width: Int): String {
        val magnitude = abs(value)
        val degrees = floor(magnitude).toInt()
        val minutes = (magnitude - degrees) * 60.0
        return String.format(Locale.ROOT, "%0${width}d°%06.3f'%c", degrees, minutes, hemisphere)
    }
}
