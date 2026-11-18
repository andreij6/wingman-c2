package com.wingmanc2.core.data.mission

import android.util.Log
import com.wingmanc2.core.algorithms.planning.reserveFloorPct
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.MissionStore
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.MissionCommand
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.MissionStatus
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ScenarioProfile
import com.wingmanc2.core.model.TelemetryFrame
import com.wingmanc2.core.simulation.link.LinkSimulator
import com.wingmanc2.core.simulation.uas.FlightCommand
import com.wingmanc2.core.simulation.uas.FlightRoute
import com.wingmanc2.core.simulation.uas.SimEvent
import com.wingmanc2.core.simulation.uas.UasPhysicsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MissionRuntime @Inject constructor(
    private val missions: MissionStore,
    private val entities: EntityStore,
    private val telemetry: TelemetryHub,
    private val journal: MissionJournal,
    private val engine: UasPhysicsEngine,
    private val planner: MissionPlannerService,
    private val assets: AssetRegistry,
    private val link: LinkSimulator,
    private val profile: ScenarioProfile,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) : MissionRepository {

    private sealed interface FlightUpdate {
        class Started(val missionId: Long, val assetKey: String?) : FlightUpdate
        class Frame(val frame: TelemetryFrame) : FlightUpdate
        data object Ended : FlightUpdate
    }

    private val mutex = Mutex()
    private var operatorHold = false

    override val activeMission: StateFlow<MissionPlan?> = missions.observeLatest()
        .map { plan -> plan?.takeIf { it.status in ACTIVE } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun observeDecisions(missionId: Long): Flow<List<AutonomyDecision>> = journal.observe(missionId)

    fun start() {
        scope.launch(default) {
            activeMission
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(FlightUpdate.Ended) else flight(id) }
                .collect { update ->
                    when (update) {
                        is FlightUpdate.Started -> telemetry.begin(update.missionId, update.assetKey)
                        is FlightUpdate.Frame -> telemetry.onFrame(update.frame)
                        FlightUpdate.Ended -> telemetry.end()
                    }
                }
        }
        scope.launch(default) {
            engine.events.collect { event ->
                try {
                    onEvent(event)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Mission event $event failed", failure)
                }
            }
        }
    }

    override suspend fun dispatch(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        coa: CourseOfAction,
    ): MissionPlan = withContext(NonCancellable) {
        mutex.withLock {
            activeMission.value?.let { missions.updateStatus(it.id, MissionStatus.COMPLETE) }
            val now = clock.nowMs()
            val plan = missions.save(MissionPlan(0, intent, target.id, asset.id, coa, MissionStatus.EN_ROUTE, now))
            journal.log(plan, now, DecisionCopy.approval(intent, coa))
            plan
        }
    }

    override suspend fun command(command: MissionCommand) = withContext(NonCancellable) {
        mutex.withLock {
            val plan = current() ?: return@withLock
            val frame = telemetry.latest
            val now = frame?.tMs ?: clock.nowMs()
            when (command) {
                MissionCommand.HOLD -> {
                    operatorHold = true
                    engine.command(FlightCommand.Hold)
                    transition(plan, MissionStatus.LOITER)
                    journal.log(plan, now, DecisionCopy.hold(frame))
                }
                MissionCommand.RESUME -> {
                    operatorHold = false
                    engine.command(FlightCommand.Resume)
                    transition(plan, MissionStatus.EN_ROUTE)
                    journal.log(plan, now, DecisionCopy.resume(plan, frame))
                }
                MissionCommand.REPLAN -> replan(plan, frame)?.let { coa -> journal.log(plan, now, DecisionCopy.replan(coa)) }
                MissionCommand.RTB -> {
                    engine.command(FlightCommand.ReturnToBase)
                    transition(plan, MissionStatus.RTB)
                    journal.log(plan, now, DecisionCopy.returnToBase(frame))
                }
            }
        }
    }

    private fun flight(missionId: Long): Flow<FlightUpdate> = flow {
        val plan = missions.get(missionId) ?: return@flow
        val target = entities.get(plan.targetId)?.position
        emit(FlightUpdate.Started(plan.id, entities.get(plan.assetId)?.key))
        var resumed = false
        engine.fly(routeOf(plan.coa, target, plan.intent)).collect { frame ->
            emit(FlightUpdate.Frame(frame))
            if (!resumed) {
                resumed = true
                when (plan.status) {
                    MissionStatus.LOITER -> engine.command(FlightCommand.Hold)
                    MissionStatus.RTB -> engine.command(FlightCommand.ReturnToBase)
                    else -> Unit
                }
            }
        }
    }.catch { failure -> Log.e(TAG, "Flight for mission $missionId failed", failure) }

    private suspend fun onEvent(event: SimEvent) = mutex.withLock {
        val plan = current() ?: return@withLock
        when (event) {
            is SimEvent.WaypointReached -> if (event.index == loiterIndexOf(plan.coa, event.count) && plan.status == MissionStatus.EN_ROUTE) {
                transition(plan, MissionStatus.LOITER)
            }
            is SimEvent.ThreatDetected -> when {
                plan.status == MissionStatus.EN_ROUTE || plan.status == MissionStatus.REPLANNING ||
                    (plan.status == MissionStatus.LOITER && !operatorHold) -> replan(plan, telemetry.latest)?.let { coa ->
                    journal.log(plan, event.tMs, DecisionCopy.reroute(plan.coa, coa, event.threatKey, event.bearingDeg))
                }
                else -> journal.log(
                    plan,
                    event.tMs,
                    DecisionCopy.threatWhileCommitted(plan.status, event.threatKey, event.distanceM, event.bearingDeg),
                )
            }
            is SimEvent.Captured -> if (event.frameNumber == 1 || event.frameNumber % CAPTURE_LOG_EVERY == 0) {
                journal.log(plan, event.tMs, DecisionCopy.capture(event.frameNumber, link.mode, event.rangeToTargetM))
            }
            is SimEvent.BatteryFloor -> if (plan.status != MissionStatus.RTB) {
                engine.command(FlightCommand.ReturnToBase)
                transition(plan, MissionStatus.RTB)
                journal.log(plan, event.tMs, DecisionCopy.batteryFloor(event.batteryPct))
            }
            is SimEvent.HomeReached -> {
                transition(plan, MissionStatus.COMPLETE)
                journal.log(plan, event.tMs, DecisionCopy.recovered(telemetry.latest))
            }
        }
    }

    private suspend fun current(): MissionPlan? =
        activeMission.value?.let { missions.get(it.id) }?.takeIf { it.status in ACTIVE }

    private suspend fun transition(plan: MissionPlan, status: MissionStatus) {
        missions.updateStatus(plan.id, status)
        telemetry.requestAssetSync()
    }

    private suspend fun replan(plan: MissionPlan, frame: TelemetryFrame?): CourseOfAction? {
        val target = entities.get(plan.targetId) ?: return null
        val asset = assets.primaryAsset().let { if (frame == null) it else it.copy(position = frame.position, headingDeg = frame.headingDeg) }
        missions.updateStatus(plan.id, MissionStatus.REPLANNING)
        val options = planner.plan(plan.intent, target, asset, assets.threats(), assets.noFlyZones(), frame?.batteryPct)
        val coa = options.firstOrNull { it.code == plan.coa.code } ?: options.firstOrNull()
        if (coa == null) {
            missions.updateStatus(plan.id, plan.status)
            return null
        }
        engine.command(FlightCommand.Replan(routeOf(coa, target.position, plan.intent)))
        missions.save(plan.copy(coa = coa, status = MissionStatus.EN_ROUTE))
        telemetry.requestAssetSync()
        return coa
    }

    private fun routeOf(coa: CourseOfAction, target: GeoPoint?, intent: CommanderIntent): FlightRoute {
        val legs = coa.waypoints.drop(1).ifEmpty { coa.waypoints }
        val skipped = coa.waypoints.size - legs.size
        val altitudes = if (coa.altitudesAglM.size == coa.waypoints.size) {
            coa.altitudesAglM.subList(skipped, coa.altitudesAglM.size)
        } else {
            List(legs.size) { coa.maxAltitudeAglM }
        }
        return FlightRoute(
            home = profile.home,
            waypoints = legs,
            altitudesAglM = altitudes,
            target = target,
            loopFrom = coa.orbitEntryIndex?.let { it + 1 - skipped }?.takeIf { it in legs.indices },
            reserveFloorPct = intent.reserveFloorPct(),
        )
    }

    private fun loiterIndexOf(coa: CourseOfAction, count: Int): Int =
        coa.orbitEntryIndex?.let { it - (coa.waypoints.size - count) }?.takeIf { it in 0 until count } ?: (count - 1)

    private companion object {
        const val TAG = "MissionRuntime"
        const val CAPTURE_LOG_EVERY = 5
        val ACTIVE = setOf(MissionStatus.EN_ROUTE, MissionStatus.LOITER, MissionStatus.REPLANNING, MissionStatus.RTB)
    }
}
