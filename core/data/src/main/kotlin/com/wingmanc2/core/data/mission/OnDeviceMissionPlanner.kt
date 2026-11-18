package com.wingmanc2.core.data.mission

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.algorithms.planning.CoaPlanner
import com.wingmanc2.core.algorithms.planning.planningSignature
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ScenarioProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
internal class OnDeviceMissionPlanner @Inject constructor(
    private val planner: CoaPlanner,
    private val profile: ScenarioProfile,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) : MissionPlannerService {

    private val cache = object : LinkedHashMap<List<Any?>, List<CourseOfAction>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Any?>, List<CourseOfAction>>) = size > CACHE_SIZE
    }

    override suspend fun plan(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        threats: List<OntologyEntity>,
        noFly: List<OntologyEntity>,
        startBatteryPct: Double?,
    ): List<CourseOfAction> = withContext(default) {
        val battery = startBatteryPct ?: FlightModel.START_BATTERY_PCT
        val key = listOf(
            intent,
            target.planningSignature,
            target.attributes,
            asset.planningSignature,
            threats.map { it.planningSignature },
            noFly.map { it.planningSignature to it.footprint },
            battery.roundToInt(),
        )
        synchronized(cache) { cache[key] } ?: planner.plan(intent, target, asset, threats, noFly, battery, profile.home)
            .also { synchronized(cache) { cache[key] = it } }
    }

    private companion object {
        const val CACHE_SIZE = 8
    }
}
