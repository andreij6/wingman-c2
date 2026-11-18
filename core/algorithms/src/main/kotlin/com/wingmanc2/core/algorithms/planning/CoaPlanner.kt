package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.model.CoaProfile
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

internal class CoaCandidate(
    val code: String,
    val profile: CoaProfile,
    val title: String,
    val route: Route,
    val metrics: RouteMetrics,
    val observationRangeM: Double,
    val emitsRf: Boolean,
)

class CoaPlanner @Inject constructor() {
    private val grid = CostGrid()
    private val search = GridAStar(grid)
    private val smoother = PathSmoother(grid)

    @Synchronized
    fun plan(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        threats: List<OntologyEntity>,
        noFly: List<OntologyEntity>,
    ): List<CourseOfAction> {
        val context = PlanningContext.of(intent, target, asset, threats, noFly)
        val candidates = listOf(terrainMasked(context), directTransit(context), passiveLoiter(context))
        return CoaAssessor(context).rank(candidates)
    }

    private fun terrainMasked(context: PlanningContext): CoaCandidate {
        rasterize(context, context.maskedAltitudeAglM)
        val target = context.targetPoint
        val minRange = context.standoffM + grid.cellSizeM
        val maxRange = max(context.standoffM * MASKED_SEARCH_FACTOR, context.coveredRadiusM) + grid.cellSizeM
        val goal = grid.observationCell(target.x, target.y, minRange, maxRange, context.start.x, context.start.y)
            .takeIf { it >= 0 } ?: standoffPoint(context).let { grid.cellAt(it.x, it.y) }
        val route = Route.Builder(Waypoint(context.start.x, context.start.y, context.maskedAltitudeAglM))
            .follow(plannedCells(context.start, goal, STEALTH_WEIGHT), context.maskedAltitudeAglM, FlightRegime.CRUISE)
            .build()
        return candidate(context, "COA-A", CoaProfile.STEALTH, "Terrain-masked ridge flank", route, emitsRf = true)
    }

    private fun directTransit(context: PlanningContext): CoaCandidate {
        rasterize(context, context.transitAltitudeAglM)
        val aim = standoffPoint(context)
        val route = Route.Builder(Waypoint(context.start.x, context.start.y, context.maskedAltitudeAglM))
            .follow(plannedCells(context.start, grid.cellAt(aim.x, aim.y), DIRECT_WEIGHT), context.transitAltitudeAglM, FlightRegime.DASH)
            .build()
        return candidate(context, "COA-B", CoaProfile.DIRECT, "Direct high-altitude transit", route, emitsRf = true)
    }

    private fun passiveLoiter(context: PlanningContext): CoaCandidate {
        rasterize(context, context.transitAltitudeAglM)
        val target = context.targetPoint
        val radius = max(context.standoffM, context.coveredRadiusM) + ORBIT_MARGIN_M
        val bearing = atan2(context.start.y - target.y, context.start.x - target.x)
        val orbit = OrbitGenerator.orbit(target.x, target.y, radius, bearing)
        val entry = orbit.first()
        val approach = plannedCells(context.start, grid.cellAt(entry.x, entry.y), ENDURANCE_WEIGHT)
        val builder = Route.Builder(Waypoint(context.start.x, context.start.y, context.maskedAltitudeAglM))
            .follow(approach.copyOf((approach.size - 1).coerceAtLeast(0)), context.transitAltitudeAglM, FlightRegime.CRUISE)
            .to(Waypoint(entry.x, entry.y, context.transitAltitudeAglM), FlightRegime.CRUISE)
        for (i in 1 until orbit.size) builder.to(Waypoint(orbit[i].x, orbit[i].y, context.transitAltitudeAglM), FlightRegime.LOITER)
        return candidate(context, "COA-C", CoaProfile.ENDURANCE, "Passive loiter, EO/IR orbit", builder.build(), emitsRf = false, observationRangeM = radius)
    }

    private fun candidate(
        context: PlanningContext,
        code: String,
        profile: CoaProfile,
        title: String,
        route: Route,
        emitsRf: Boolean,
        observationRangeM: Double = hypot(route.end.x - context.targetPoint.x, route.end.y - context.targetPoint.y),
    ) = CoaCandidate(
        code = code,
        profile = profile,
        title = title,
        route = route,
        metrics = RouteMetrics.evaluate(route, context.targetPoint, context.radars, context.start),
        observationRangeM = observationRangeM,
        emitsRf = emitsRf,
    )

    private fun rasterize(context: PlanningContext, altitudeAglM: Double) {
        grid.reset()
        context.noGo.forEach { it.stamp(grid) }
        grid.blockDisk(context.targetPoint.x, context.targetPoint.y, context.standoffM)
        context.radars.forEach { grid.addRadarExposure(it, altitudeAglM) }
    }

    private fun plannedCells(from: LocalPoint, goal: Int, exposureWeight: Float): IntArray {
        val start = grid.cellAt(from.x, from.y)
        val path = search.search(start, goal, exposureWeight)
        val cells = if (path.isEmpty()) intArrayOf(start, goal) else path
        return smoother.simplify(cells, exposureWeight).let { it.copyOfRange(1, it.size) }
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

    private companion object {
        const val STEALTH_WEIGHT = 40f
        const val DIRECT_WEIGHT = 0f
        const val ENDURANCE_WEIGHT = 6f
        const val MASKED_SEARCH_FACTOR = 3.0
        const val ORBIT_MARGIN_M = 100.0
    }
}
