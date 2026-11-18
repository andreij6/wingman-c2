package com.wingmanc2.core.algorithms.geo

import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LocalPoint
import kotlin.math.PI
import kotlin.math.cos

class LocalProjection(val origin: GeoPoint) {
    private val metersPerDegLat = 111_132.954
    private val metersPerDegLon = 111_319.488 * cos(origin.lat * PI / 180.0)

    fun toLocal(p: GeoPoint): LocalPoint =
        LocalPoint((p.lon - origin.lon) * metersPerDegLon, (p.lat - origin.lat) * metersPerDegLat)

    fun toGeo(p: LocalPoint): GeoPoint = toGeo(p.x, p.y)

    fun toGeo(x: Double, y: Double): GeoPoint =
        GeoPoint(origin.lat + y / metersPerDegLat, origin.lon + x / metersPerDegLon)

    fun xOf(p: GeoPoint): Double = (p.lon - origin.lon) * metersPerDegLon

    fun yOf(p: GeoPoint): Double = (p.lat - origin.lat) * metersPerDegLat
}
