package com.wingmanc2.core.data.mission

import com.wingmanc2.core.algorithms.planning.CoaPlanner
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.OntologyEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class OnDeviceMissionPlanner @Inject constructor(
    private val planner: CoaPlanner,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) : MissionPlannerService {

    override suspend fun plan(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        threats: List<OntologyEntity>,
        noFly: List<OntologyEntity>,
    ): List<CourseOfAction> = withContext(default) { planner.plan(intent, target, asset, threats, noFly) }
}
