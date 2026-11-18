package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import kotlin.math.hypot

internal class ThreatRadar(val entity: OntologyEntity, val horizon: RadarHorizon) {
    val bandLabel: String = entity.attributes[EntityAttributes.BAND]?.let { if (it.endsWith("band", ignoreCase = true)) it else "$it-band" } ?: "radar"
}

internal sealed interface NoGoArea {
    val centerX: Double
    val centerY: Double
    val boundingRadiusM: Double

    fun stamp(grid: CostGrid)

    class Polygon(private val xs: DoubleArray, private val ys: DoubleArray) : NoGoArea {
        override val centerX = xs.average()
        override val centerY = ys.average()
        override val boundingRadiusM = xs.indices.maxOf { hypot(xs[it] - centerX, ys[it] - centerY) }

        override fun stamp(grid: CostGrid) {
            grid.blockPolygon(xs, ys)
        }
    }

    class Disk(override val centerX: Double, override val centerY: Double, override val boundingRadiusM: Double) : NoGoArea {
        override fun stamp(grid: CostGrid) {
            grid.blockDisk(centerX, centerY, boundingRadiusM)
        }
    }
}

internal class PlanningContext(
    val intent: CommanderIntent,
    val target: OntologyEntity,
    val start: LocalPoint,
    val home: LocalPoint,
    val targetPoint: LocalPoint,
    val startBatteryPct: Double,
    val threats: List<ThreatRadar>,
    val noGo: List<NoGoArea>,
) {
    val radars: List<RadarHorizon> = threats.map { it.horizon }

    val standoffM: Double = intent.standoffM()

    val reserveFloorPct: Double = intent.reserveFloorPct()

    private val ceilingAglM: Double = intent.ceilingAglM()

    val maskedAltitudeAglM: Double = minOf(FlightModel.MASKED_ALT_AGL_M, ceilingAglM)

    val popupAltitudeAglM: Double = minOf(FlightModel.MASKED_ALT_AGL_M + POPUP_CLIMB_M, ceilingAglM)

    val transitAltitudeAglM: Double = minOf(FlightModel.TRANSIT_ALT_AGL_M, ceilingAglM)

    val primaryThreat: ThreatRadar? = threats.minByOrNull { hypot(it.horizon.x - targetPoint.x, it.horizon.y - targetPoint.y) }

    val coveredRadiusM: Double = threats.maxOfOrNull {
        val reach = it.horizon.radiusM + hypot(it.horizon.x - targetPoint.x, it.horizon.y - targetPoint.y)
        if (it.horizon.covers(targetPoint.x, targetPoint.y)) reach else 0.0
    } ?: 0.0

    companion object {
        private const val POPUP_CLIMB_M = 80.0

        fun of(
            intent: CommanderIntent,
            target: OntologyEntity,
            asset: OntologyEntity,
            threats: List<OntologyEntity>,
            noFly: List<OntologyEntity>,
            startBatteryPct: Double,
            home: GeoPoint,
            horizon: (x: Double, y: Double, radiusM: Double) -> RadarHorizon,
        ): PlanningContext {
            val projection = OperationalArea.projection
            val radars = (listOf(target) + threats)
                .filter { it.threatRadiusM > 0.0 }
                .distinctBy { it.id }
                .map { threat ->
                    val at = projection.toLocal(threat.position)
                    ThreatRadar(threat, horizon(at.x, at.y, threat.threatRadiusM))
                }
            val zones = noFly.mapNotNull { zone ->
                when {
                    zone.footprint.size >= 3 -> {
                        val corners = zone.footprint.map(projection::toLocal)
                        NoGoArea.Polygon(DoubleArray(corners.size) { corners[it].x }, DoubleArray(corners.size) { corners[it].y })
                    }
                    zone.threatRadiusM > 0.0 -> projection.toLocal(zone.position).let { NoGoArea.Disk(it.x, it.y, zone.threatRadiusM) }
                    else -> null
                }
            }
            return PlanningContext(
                intent = intent,
                target = target,
                start = projection.toLocal(asset.position),
                home = projection.toLocal(home),
                targetPoint = projection.toLocal(target.position),
                startBatteryPct = startBatteryPct,
                threats = radars,
                noGo = zones,
            )
        }
    }
}
