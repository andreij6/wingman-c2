package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LocalPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Fix(val position: LocalPoint, val uncertaintyM: Double, val bearing: BearingRay? = null)

internal object Localizer {
    private const val GNSS_UNCERTAINTY_M = 50.0
    private const val RANGED_UNCERTAINTY_RATIO = 0.35
    private const val FALLBACK_RANGE_M = 1_200.0

    val unlocated = Fix(LocalPoint(0.0, 0.0), OperationalArea.HALF_EXTENT_M * sqrt(2.0))

    val UNLOCATED_THRESHOLD_M = unlocated.uncertaintyM / 2

    fun explicit(fields: Map<String, String>): Fix? {
        val x = fields.field("x", "x_m", "east_m")?.let(::leadingNumber)
        val y = fields.field("y", "y_m", "north_m")?.let(::leadingNumber)
        if (x != null && y != null) return Fix(LocalPoint(x, y), GNSS_UNCERTAINTY_M)
        val lat = fields.field("lat", "latitude", "lat_deg")?.let(::coordinate) ?: return null
        val lon = fields.field("lon", "lng", "long", "longitude", "lon_deg")?.let(::coordinate) ?: return null
        return geo(lat, lon)
    }

    fun geo(lat: Double, lon: Double): Fix = Fix(OperationalArea.projection.toLocal(GeoPoint(lat, lon)), GNSS_UNCERTAINTY_M)

    fun alongBearing(origin: LocalPoint, bearingDeg: Double, rangeM: Double?): Fix {
        val radians = bearingDeg * PI / 180.0
        val ux = sin(radians)
        val uy = cos(radians)
        if (rangeM != null) return Fix(offset(origin, ux, uy, rangeM), rangeM * RANGED_UNCERTAINTY_RATIO)
        val exit = min(exitDistance(origin.x, ux), exitDistance(origin.y, uy))
        val length = if (exit > 0.0 && exit.isFinite()) exit else FALLBACK_RANGE_M * 2
        return Fix(offset(origin, ux, uy, length / 2), length / 2, BearingRay(origin, bearingDeg, length))
    }

    fun offset(origin: LocalPoint, ux: Double, uy: Double, distanceM: Double) =
        LocalPoint(origin.x + ux * distanceM, origin.y + uy * distanceM)

    private fun exitDistance(p: Double, u: Double): Double = when {
        u > 0.0 -> (OperationalArea.HALF_EXTENT_M - p) / u
        u < 0.0 -> (-OperationalArea.HALF_EXTENT_M - p) / u
        else -> Double.POSITIVE_INFINITY
    }
}
