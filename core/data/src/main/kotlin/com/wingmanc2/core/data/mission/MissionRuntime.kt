package com.wingmanc2.core.data.mission

import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.database.store.DecisionStore
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.MissionStore
import com.wingmanc2.core.database.store.TelemetryStore
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.domain.TelemetryStreamSource
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.DecisionKind
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.MissionCommand
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.MissionStatus
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.OutboxOp
import com.wingmanc2.core.model.TelemetryFrame
import com.wingmanc2.core.simulation.link.LinkSimulator
import com.wingmanc2.core.simulation.scenario.ScenarioKeys
import com.wingmanc2.core.simulation.scenario.ScenarioSeed
import com.wingmanc2.core.simulation.uas.FlightCommand
import com.wingmanc2.core.simulation.uas.FlightRoute
import com.wingmanc2.core.simulation.uas.SimEvent
import com.wingmanc2.core.simulation.uas.UasPhysicsEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
internal class MissionRuntime @Inject constructor(
    private val missions: MissionStore,
    private val decisions: DecisionStore,
    private val telemetryStore: TelemetryStore,
    private val entities: EntityStore,
    private val index: EntityIndex,
    private val engine: UasPhysicsEngine,
    private val planner: MissionPlannerService,
    private val assets: AssetRegistry,
    private val link: LinkSimulator,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) : MissionRepository, TelemetryStreamSource {

    private class MissionFrame(val missionId: Long, val assetId: Long, val frame: TelemetryFrame)

    private val mutex = Mutex()
    private val ring = TelemetryRing(TRAIL_CAPACITY)
    private val frames = MutableSharedFlow<TelemetryFrame>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val trailState = MutableStateFlow<List<TelemetryFrame>>(emptyList())
    private var trackedMissionId = NO_MISSION
    private var framesSinceTrail = 0
    private var lastPersistedMs = Long.MIN_VALUE

    override val activeMission: StateFlow<MissionPlan?> = missions.observeLatest()
        .map { plan -> plan?.takeIf { it.status in ACTIVE } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun telemetry(): Flow<TelemetryFrame> = frames.asSharedFlow()

    override fun trail(): Flow<List<TelemetryFrame>> = trailState.asStateFlow()

    override fun observeDecisions(missionId: Long): Flow<List<AutonomyDecision>> = decisions.observe(missionId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch(default) {
            activeMission
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) emptyFlow() else flight(id) }
                .collect(::onFrame)
        }
        scope.launch(default) { engine.events.collect(::onEvent) }
    }

    override suspend fun dispatch(
        intent: CommanderIntent,
        target: OntologyEntity,
        asset: OntologyEntity,
        coa: CourseOfAction,
    ): MissionPlan = mutex.withLock {
        activeMission.value?.let { missions.updateStatus(it.id, MissionStatus.COMPLETE) }
        val now = clock.nowMs()
        val plan = missions.save(MissionPlan(0, intent, target.id, asset.id, coa, MissionStatus.EN_ROUTE, now))
        log(plan, now, DecisionKind.APPROVAL, "${coa.code} approved by operator", DecisionCopy.approvalRationale(intent), "Operator · ${coa.title}")
        plan
    }

    override suspend fun command(command: MissionCommand) = mutex.withLock {
        val plan = activeMission.value ?: return@withLock
        val now = clock.nowMs()
        val frame = frames.replayCache.lastOrNull()
        when (command) {
            MissionCommand.HOLD -> {
                engine.command(FlightCommand.Hold)
                missions.updateStatus(plan.id, MissionStatus.LOITER)
                log(plan, now, DecisionKind.HOLD, "Holding · orbit at current position", "Operator override · ${batteryText(frame)} remaining", PROVENANCE_OPERATOR)
            }
            MissionCommand.RESUME -> {
                engine.command(FlightCommand.Resume)
                missions.updateStatus(plan.id, MissionStatus.EN_ROUTE)
                log(plan, now, DecisionKind.RESUME, "Operator resumed ${plan.coa.code}", "Route reacquired · ${waypointText(frame)}", PROVENANCE_OPERATOR)
            }
            MissionCommand.REPLAN -> replan(plan, frame)?.let { coa ->
                log(
                    plan, now, DecisionKind.REPLAN, "Re-planned ${coa.code} from current position",
                    "${coa.minStandoffM.roundToInt()} m standoff · ${coa.projectedReservePct}% reserve · ETA ${etaText(coa.etaSeconds)}",
                    "Operator · CoaPlanner A*",
                )
            }
            MissionCommand.RTB -> {
                engine.command(FlightCommand.ReturnToBase)
                missions.updateStatus(plan.id, MissionStatus.RTB)
                log(plan, now, DecisionKind.RTB, "Return to base", "Operator override · ${batteryText(frame)} remaining", PROVENANCE_OPERATOR)
            }
        }
    }

    suspend fun reportThreat(threat: OntologyEntity) = mutex.withLock {
        val plan = activeMission.value ?: return@withLock
        val from = frames.replayCache.lastOrNull()?.position ?: ScenarioSeed.home
        log(
            plan, clock.nowMs(), DecisionKind.THREAT,
            "Pop-up emitter ${threat.key} · ${threat.attributes[ScenarioKeys.ATTR_BAND] ?: "RF"}",
            DecisionCopy.threatRationale(threat.threatRadiusM, DecisionCopy.distanceM(from, threat.position), DecisionCopy.bearingDeg(from, threat.position)),
            "${ScenarioKeys.RF_RX2} · scenario inject",
        )
    }

    private fun flight(missionId: Long): Flow<MissionFrame> = flow {
        val plan = missions.get(missionId) ?: return@flow
        if (plan.status == MissionStatus.LOITER) missions.updateStatus(plan.id, MissionStatus.EN_ROUTE)
        val target = entities.get(plan.targetId)?.position
        engine.fly(routeOf(plan.coa, target)).collect { emit(MissionFrame(plan.id, plan.assetId, it)) }
    }

    private suspend fun onFrame(missionFrame: MissionFrame) {
        val frame = missionFrame.frame
        if (missionFrame.missionId != trackedMissionId) {
            trackedMissionId = missionFrame.missionId
            ring.clear()
            framesSinceTrail = TRAIL_EVERY_FRAMES
            lastPersistedMs = Long.MIN_VALUE
        }
        ring.append(frame)
        frames.emit(frame)
        if (++framesSinceTrail >= TRAIL_EVERY_FRAMES) {
            framesSinceTrail = 0
            trailState.value = ring.snapshot()
        }
        if (frame.tMs - lastPersistedMs >= PERSIST_INTERVAL_MS) {
            lastPersistedMs = frame.tMs
            persist(missionFrame)
        }
    }

    private suspend fun persist(missionFrame: MissionFrame) {
        val frame = missionFrame.frame
        telemetryStore.append(missionFrame.missionId, frame)
        val asset = entities.get(missionFrame.assetId) ?: return
        val moved = entities.upsertWithOutbox(
            entity = asset.copy(position = frame.position, headingDeg = frame.headingDeg),
            op = OutboxOp.UPSERT,
            payloadBytes = TELEMETRY_PAYLOAD_BYTES,
            nowMs = frame.tMs,
        )
        index.register(moved)
    }

    private suspend fun onEvent(event: SimEvent) = mutex.withLock {
        val plan = activeMission.value ?: return@withLock
        when (event) {
            is SimEvent.WaypointReached -> if (event.index == event.count - 1 && plan.status == MissionStatus.EN_ROUTE) {
                missions.updateStatus(plan.id, MissionStatus.LOITER)
            }
            is SimEvent.ThreatDetected -> replan(plan, frames.replayCache.lastOrNull())?.let { coa ->
                log(
                    plan, event.tMs, DecisionKind.REROUTE,
                    DecisionCopy.rerouteTitle(plan.coa.waypoints, coa.waypoints),
                    DecisionCopy.rerouteRationale(event.bearingDeg, coa.profile, coa.minStandoffM),
                    "UAS physics · ${event.threatKey} detected · CoaPlanner A*",
                )
            }
            is SimEvent.Captured -> if (event.frameNumber == 1 || event.frameNumber % CAPTURE_LOG_EVERY == 0) {
                log(
                    plan, event.tMs, DecisionKind.CAPTURE, "Captured EO frame #${event.frameNumber}",
                    DecisionCopy.captureRationale(link.mode), "EO/IR gimbal · range ${event.rangeToTargetM.roundToInt()} m",
                )
            }
            is SimEvent.BatteryFloor -> {
                engine.command(FlightCommand.ReturnToBase)
                missions.updateStatus(plan.id, MissionStatus.RTB)
                log(
                    plan, event.tMs, DecisionKind.RTB, "Auto-RTB · battery ${event.batteryPct.roundToInt()}%",
                    "Reserve floor reached · returning to base", "UAS physics · battery monitor",
                )
            }
            is SimEvent.HomeReached -> {
                missions.updateStatus(plan.id, MissionStatus.COMPLETE)
                log(
                    plan, event.tMs, DecisionKind.RTB, "Recovered at home",
                    "Mission complete · ${batteryText(frames.replayCache.lastOrNull())} remaining", "UAS physics · home reached",
                )
            }
        }
    }

    private suspend fun replan(plan: MissionPlan, frame: TelemetryFrame?): CourseOfAction? {
        val target = entities.get(plan.targetId) ?: return null
        val asset = assets.primaryAsset().let { if (frame == null) it else it.copy(position = frame.position, headingDeg = frame.headingDeg) }
        missions.updateStatus(plan.id, MissionStatus.REPLANNING)
        val options = planner.plan(plan.intent, target, asset, assets.threats(), assets.noFlyZones())
        val coa = options.firstOrNull { it.code == plan.coa.code } ?: options.firstOrNull() ?: return null
        engine.command(FlightCommand.Replan(routeOf(coa, target.position)))
        missions.save(plan.copy(coa = coa, status = MissionStatus.EN_ROUTE))
        return coa
    }

    private suspend fun log(plan: MissionPlan, atMs: Long, kind: DecisionKind, title: String, rationale: String, provenance: String) {
        decisions.append(AutonomyDecision(0, plan.id, (atMs - plan.startedAtMs).coerceAtLeast(0L), kind, title, rationale, provenance))
    }

    private fun routeOf(coa: CourseOfAction, target: GeoPoint?): FlightRoute = FlightRoute(
        home = ScenarioSeed.home,
        waypoints = coa.waypoints,
        altitudesAglM = List(coa.waypoints.size) { coa.maxAltitudeAglM },
        target = target,
    )

    private fun batteryText(frame: TelemetryFrame?): String = frame?.let { "battery ${it.batteryPct.roundToInt()}%" } ?: "battery nominal"

    private fun waypointText(frame: TelemetryFrame?): String =
        frame?.let { "waypoint ${(it.waypointIndex + 1).coerceAtMost(it.waypointCount)} of ${it.waypointCount}" } ?: "route resumed"

    private fun etaText(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private companion object {
        const val NO_MISSION = Long.MIN_VALUE
        const val TRAIL_CAPACITY = 240
        const val TRAIL_EVERY_FRAMES = 5
        const val PERSIST_INTERVAL_MS = 1_000L
        const val TELEMETRY_PAYLOAD_BYTES = 184
        const val CAPTURE_LOG_EVERY = 5
        const val PROVENANCE_OPERATOR = "Operator · human-on-the-loop"
        val ACTIVE = setOf(MissionStatus.EN_ROUTE, MissionStatus.LOITER, MissionStatus.REPLANNING, MissionStatus.RTB)
    }
}
