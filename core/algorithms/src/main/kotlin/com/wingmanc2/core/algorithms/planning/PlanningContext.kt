package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.IntentConstraint
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import kotlin.math.hypot

internal class ThreatRadar(val entity: OntologyEntity, val horizon: RadarHorizon) {
    val bandLabel: String = entity.attributes["band"]?.let { if (it.endsWith("band", ignoreCase = true)) it else "$it-band" } ?: "radar"
}

internal sealed interface NoGoArea {
    fun stamp(grid: CostGrid)

    class Polygon(private val xs: DoubleArray, private val ys: DoubleArray) : NoGoArea {
        override fun stamp(grid: CostGrid) {
            grid.blockPolygon(xs, ys)
        }
    }

    class Disk(private val x: Double, private val y: Double, private val radiusM: Double) : NoGoArea {
        override fun stamp(grid: CostGrid) {
            grid.blockDisk(x, y, radiusM)
        }
    }
}

internal class PlanningContext(
    val intent: CommanderIntent,
    val target: OntologyEntity,
    val start: LocalPoint,
    val targetPoint: LocalPoint,
    val standoffM: Double,
    val reserveFloorPct: Double,
    val threats: List<ThreatRadar>,
    val noGo: List<NoGoArea>,
) {
    val radars: List<RadarHorizon> = threats.map { it.horizon }

    private val ceilingAglM: Double = intent.constraints.filterIsInstance<IntentConstraint.MaxAltitude>().minOfOrNull { it.metersAgl }
        ?: Double.POSITIVE_INFINITY

    val maskedAltitudeAglM: Double = minOf(FlightModel.MASKED_ALT_AGL_M, ceilingAglM)

    val transitAltitudeAglM: Double = minOf(FlightModel.TRANSIT_ALT_AGL_M, ceilingAglM)

    val primaryThreat: ThreatRadar? = threats.minByOrNull { hypot(it.horizon.x - targetPoint.x, it.horizon.y - targetPoint.y) }

    val coveredRadiusM: Double = threats.maxOfOrNull {
        val reach = it.horizon.radiusM + hypot(it.horizon.x - targetPoint.x, it.horizon.y - targetPoint.y)
        if (it.horizon.covers(targetPoint.x, targetPoint.y)) reach else 0.0
    } ?: 0.0

    companion object {
        private const val DEFAULT_STANDOFF_M = 200.0

        fun of(
            intent: CommanderIntent,
            target: OntologyEntity,
            asset: OntologyEntity,
            threats: List<OntologyEntity>,
            noFly: List<OntologyEntity>,
        ): PlanningContext {
            val projection = OperationalArea.projection
            val radars = (listOf(target) + threats)
                .filter { it.threatRadiusM > 0.0 }
                .distinctBy { it.id }
                .map { ThreatRadar(it, RadarHorizon(projection.xOf(it.position), projection.yOf(it.position), it.threatRadiusM)) }
            val zones = noFly.mapNotNull { zone ->
                when {
                    zone.footprint.size >= 3 -> NoGoArea.Polygon(
                        DoubleArray(zone.footprint.size) { projection.xOf(zone.footprint[it]) },
                        DoubleArray(zone.footprint.size) { projection.yOf(zone.footprint[it]) },
                    )
                    zone.threatRadiusM > 0.0 -> NoGoArea.Disk(projection.xOf(zone.position), projection.yOf(zone.position), zone.threatRadiusM)
                    else -> null
                }
            }
            return PlanningContext(
                intent = intent,
                target = target,
                start = projection.toLocal(asset.position),
                targetPoint = projection.toLocal(target.position),
                standoffM = intent.constraints.filterIsInstance<IntentConstraint.StandoffMin>().maxOfOrNull { it.meters } ?: DEFAULT_STANDOFF_M,
                reserveFloorPct = intent.constraints.filterIsInstance<IntentConstraint.ReserveMin>().maxOfOrNull { it.percent.toDouble() }
                    ?: FlightModel.RTB_FLOOR_PCT,
                threats = radars,
                noGo = zones,
            )
        }
    }
}
