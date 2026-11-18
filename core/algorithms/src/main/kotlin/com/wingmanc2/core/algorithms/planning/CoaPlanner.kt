package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal class CoaCandidate(
    val code: String,
    val profile: CoaProfile,
    val title: String,
    val route: Route,
    val metrics: RouteMetrics,
    val observationRangeM: Double,
    val emitsRf: Boolean,
)

@Singleton
class CoaPlanner @Inject constructor() {
    private val grid = CostGrid()
    private val search = GridAStar(grid)
    private val smoother = PathSmoother(grid, search)
    private val horizons = HashMap<HorizonKey, RadarHorizon>()
    private val liveHorizons = HashSet<HorizonKey>()

    @Synchronized
    fun plan(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        threats: List<OntologyEntity>,
        noFly: List<OntologyEntity>,
        startBatteryPct: Double = FlightModel.START_BATTERY_PCT,
        home: GeoPoint = asset.position,
    ): List<CourseOfAction> {
        liveHorizons.clear()
        val context = PlanningContext.of(intent, target, asset, threats, noFly, startBatteryPct, home, ::horizon)
        rasterize(context)
        val candidates = listOf(terrainMasked(context), directTransit(context), passiveLoiter(context))
        horizons.keys.retainAll(liveHorizons)
        return CoaAssessor(context).rank(candidates)
    }

    private fun horizon(x: Double, y: Double, radiusM: Double): RadarHorizon {
        val key = HorizonKey(x, y, radiusM)
        liveHorizons += key
        return horizons.getOrPut(key) { RadarHorizon(x, y, radiusM) }
    }

    private fun rasterize(context: PlanningContext) {
        val target = context.targetPoint
        grid.reset(target.x, target.y)
        context.noGo.forEach { it.stamp(grid) }
        grid.blockDisk(target.x, target.y, context.standoffM)
        for (radar in context.radars) {
            grid.addRadarExposure(radar, context.maskedAltitudeAglM, ExposureLayer.MASKED)
            grid.addRadarExposure(radar, context.transitAltitudeAglM, ExposureLayer.TRANSIT)
        }
    }

    private fun terrainMasked(context: PlanningContext): CoaCandidate {
        grid.weigh(ExposureLayer.MASKED, STEALTH_WEIGHT, MASK_WEIGHT)
        val target = context.targetPoint
        val altitude = context.maskedAltitudeAglM
        val minRange = context.standoffM + grid.cellSizeM
        val maxRange = max(context.standoffM * MASKED_SEARCH_FACTOR, context.coveredRadiusM) + grid.cellSizeM
        val vantage = if (context.popupAltitudeAglM <= altitude) -1 else {
            grid.hiddenVantage(target.x, target.y, minRange, maxRange, horizon(target.x, target.y, maxRange), context.popupAltitudeAglM)
        }
        val goal = if (vantage >= 0) vantage else {
            grid.leastExposedCell(target.x, target.y, minRange, maxRange, context.start.x, context.start.y)
                .takeIf { it >= 0 } ?: standoffPoint(context).let { grid.cellAt(it.x, it.y) }
        }
        val builder = Route.Builder(Waypoint(context.start.x, context.start.y, altitude))
            .follow(plannedCells(context.start, goal), altitude, FlightRegime.CRUISE)
        if (vantage >= 0) builder.to(Waypoint(grid.centerX(grid.colOf(goal)), grid.centerY(grid.rowOf(goal)), context.popupAltitudeAglM), FlightRegime.CRUISE)
        return candidate(context, "COA-A", CoaProfile.STEALTH, "Terrain-masked ridge flank", builder.build(), emitsRf = true)
    }

    private fun directTransit(context: PlanningContext): CoaCandidate {
        grid.weigh(ExposureLayer.TRANSIT, DIRECT_WEIGHT, 0f)
        val aim = standoffPoint(context)
        val route = Route.Builder(Waypoint(context.start.x, context.start.y, context.maskedAltitudeAglM))
            .follow(plannedCells(context.start, grid.cellAt(aim.x, aim.y)), context.transitAltitudeAglM, FlightRegime.DASH)
            .build()
        return candidate(context, "COA-B", CoaProfile.DIRECT, "Direct high-altitude transit", route, emitsRf = true)
    }

    private fun passiveLoiter(context: PlanningContext): CoaCandidate {
        grid.weigh(ExposureLayer.TRANSIT, ENDURANCE_WEIGHT, 0f)
        val target = context.targetPoint
        val altitude = context.transitAltitudeAglM
        val floor = context.standoffM + ORBIT_MARGIN_M
        val swept = clearRadius(context, floor)
        val radius = if (swept <= FlightModel.EO_RANGE_M) swept else min(max(floor, context.coveredRadiusM + ORBIT_MARGIN_M), FlightModel.EO_RANGE_M)
        val loop = loiterLoop(target, radius, atan2(context.start.y - target.y, context.start.x - target.x), context.start)
        val builder = Route.Builder(Waypoint(context.start.x, context.start.y, context.maskedAltitudeAglM))
        if (loop.isEmpty()) {
            val aim = standoffPoint(context)
            builder.follow(plannedCells(context.start, grid.cellAt(aim.x, aim.y)), altitude, FlightRegime.CRUISE)
        } else {
            val entry = loop.first()
            val approach = plannedCells(context.start, grid.cellAt(entry.x, entry.y))
            builder.follow(approach.copyOf((approach.size - 1).coerceAtLeast(0)), altitude, FlightRegime.CRUISE)
                .to(Waypoint(entry.x, entry.y, altitude), FlightRegime.CRUISE)
            for (i in 1 until loop.size) builder.to(Waypoint(loop[i].x, loop[i].y, altitude), FlightRegime.LOITER)
        }
        return candidate(context, "COA-C", CoaProfile.ENDURANCE, "Passive loiter, EO/IR orbit", builder.build(), emitsRf = false)
    }

    private fun clearRadius(context: PlanningContext, floor: Double): Double {
        val target = context.targetPoint
        val bands = context.radars.map { band(target, it.x, it.y, it.radiusM) } +
            context.noGo.map { band(target, it.centerX, it.centerY, it.boundingRadiusM) }
        var radius = floor
        for (band in bands.sortedBy { it.start }) {
            if (band.start >= radius) break
            if (band.endInclusive > radius) radius = band.endInclusive
        }
        return radius
    }

    private fun band(target: LocalPoint, x: Double, y: Double, radiusM: Double): ClosedFloatingPointRange<Double> {
        val d = hypot(x - target.x, y - target.y)
        return (d - radiusM - ORBIT_MARGIN_M)..(d + radiusM + ORBIT_MARGIN_M)
    }

    private fun loiterLoop(target: LocalPoint, radiusM: Double, entryBearingRad: Double, from: LocalPoint): List<LocalPoint> {
        val ring = OrbitGenerator.orbit(target.x, target.y, radiusM, entryBearingRad, LOITER_SAMPLES)
        val clear = BooleanArray(LOITER_SAMPLES) { legClear(ring[it], ring[it + 1]) }
        val firstBlocked = clear.indexOfFirst { !it }
        if (firstBlocked < 0) return ring
        var bestStart = 0
        var bestLength = 0
        var runStart = 0
        var runLength = 0
        for (step in 1..LOITER_SAMPLES) {
            val leg = (firstBlocked + step) % LOITER_SAMPLES
            if (!clear[leg]) {
                runLength = 0
                continue
            }
            if (runLength++ == 0) runStart = leg
            if (runLength > bestLength) {
                bestLength = runLength
                bestStart = runStart
            }
        }
        if (bestLength == 0) return emptyList()
        val arc = List(bestLength + 1) { ring[(bestStart + it) % LOITER_SAMPLES] }
        val outbound = if (distance(arc.first(), from) <= distance(arc.last(), from)) arc else arc.asReversed()
        return outbound + outbound.asReversed().drop(1)
    }

    private fun legClear(a: LocalPoint, b: LocalPoint): Boolean {
        val from = grid.cellAt(a.x, a.y)
        val to = grid.cellAt(b.x, b.y)
        val lengthM = hypot((grid.colOf(to) - grid.colOf(from)).toDouble(), (grid.rowOf(to) - grid.rowOf(from)).toDouble()) * grid.cellSizeM
        val budget = (lengthM * (1.0 + LEG_TOLERANCE)).toFloat()
        return grid.lineCost(from, to, budget) <= budget
    }

    private fun candidate(
        context: PlanningContext,
        code: String,
        profile: CoaProfile,
        title: String,
        route: Route,
        emitsRf: Boolean,
    ) = CoaCandidate(
        code = code,
        profile = profile,
        title = title,
        route = route,
        metrics = RouteMetrics.evaluate(route, context.targetPoint, context.radars, context.home, context.startBatteryPct),
        observationRangeM = hypot(route.end.x - context.targetPoint.x, route.end.y - context.targetPoint.y),
        emitsRf = emitsRf,
    )

    private fun plannedCells(from: LocalPoint, goal: Int): IntArray {
        val path = search.search(grid.cellAt(from.x, from.y), goal)
        if (path.isEmpty()) return intArrayOf(goal)
        val smoothed = smoother.simplify(path)
        return smoothed.copyOfRange(1, smoothed.size)
    }

    private fun Route.Builder.follow(cells: IntArray, altitudeAglM: Double, regime: FlightRegime): Route.Builder = apply {
        for (cell in cells) to(Waypoint(grid.centerX(grid.colOf(cell)), grid.centerY(grid.rowOf(cell)), altitudeAglM), regime)
    }

    private fun standoffPoint(context: PlanningContext): LocalPoint {
        val target = context.targetPoint
        val dx = context.start.x - target.x
        val dy = context.start.y - target.y
        val scale = (context.standoffM + grid.cellSizeM) / max(hypot(dx, dy), 1.0)
        return LocalPoint(target.x + dx * scale, target.y + dy * scale)
    }

    private fun distance(a: LocalPoint, b: LocalPoint) = hypot(a.x - b.x, a.y - b.y)

    private data class HorizonKey(val x: Double, val y: Double, val radiusM: Double)

    private companion object {
        const val STEALTH_WEIGHT = 40f
        const val MASK_WEIGHT = 0.6f
        const val DIRECT_WEIGHT = 0f
        const val ENDURANCE_WEIGHT = 6f
        const val MASKED_SEARCH_FACTOR = 3.0
        const val ORBIT_MARGIN_M = 100.0
        const val LOITER_SAMPLES = 24
        const val LEG_TOLERANCE = 1e-3
    }
}
