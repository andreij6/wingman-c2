package com.wingmanc2.feature.mission.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.ObserveLinkStateUseCase
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.MissionCommand
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.feature.mission.domain.IssueMissionCommandUseCase
import com.wingmanc2.feature.mission.domain.LiveTelemetry
import com.wingmanc2.feature.mission.domain.ObserveFlownTrackUseCase
import com.wingmanc2.feature.mission.domain.ObserveMissionUseCase
import com.wingmanc2.feature.mission.domain.ObserveTelemetryUseCase
import com.wingmanc2.feature.mission.domain.ReplanMissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class MissionViewModel @Inject constructor(
    observeMission: ObserveMissionUseCase,
    observeTelemetry: ObserveTelemetryUseCase,
    observeFlownTrack: ObserveFlownTrackUseCase,
    observeLinkState: ObserveLinkStateUseCase,
    private val issueCommand: IssueMissionCommandUseCase,
    private val replanMission: ReplanMissionUseCase,
    @Dispatcher(WingmanDispatcher.Default) default: CoroutineDispatcher,
) : ViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)

    private val mission = observeMission().flowOn(default).shareIn(viewModelScope, sharing, replay = 1)

    private val activeMission = mission.filterNotNull()

    private val frames = observeTelemetry()
        .map<LiveTelemetry, LiveTelemetry?> { it }
        .onStart { emit(null) }
        .flowOn(default)
        .shareIn(viewModelScope, sharing, replay = 1)

    private val progress = frames
        .map { live -> live?.frame?.let { RouteProgress(it.waypointIndex, it.waypointCount) } }
        .distinctUntilChanged()

    val uiState: StateFlow<MissionUiState> = combine(mission, progress) { picture, route -> picture.toUiState(route) }
        .flowOn(default)
        .stateIn(viewModelScope, sharing, MissionUiState.Loading)

    val telemetry: StateFlow<TelemetryUi?> = combine(frames, activeMission, observeLinkState()) { live, picture, link ->
        live?.frame.toTelemetryUi(picture.reserveFloorPct, link)
    }
        .flowOn(default)
        .stateIn(viewModelScope, sharing, null)

    val clock: StateFlow<String?> = combine(frames.filterNotNull(), activeMission.map { it.startedAtMs }.distinctUntilChanged()) { live, start ->
        formatTPlus(live.frame.tMs - start)
    }
        .distinctUntilChanged()
        .flowOn(default)
        .stateIn(viewModelScope, sharing, null)

    private val flownTrack = observeFlownTrack()
        .map<List<LocalPoint>, ImmutableList<LocalPoint>> { it.toImmutableList() }
        .onStart { emit(persistentListOf()) }

    val liveAsset: StateFlow<MapLiveAsset?> = combine(
        frames.filterNotNull(),
        flownTrack,
        activeMission.map { it.assetId }.distinctUntilChanged(),
    ) { live, trail, assetId -> MapLiveAsset(assetId, live.position, live.frame.headingDeg, trail = trail) }
        .flowOn(default)
        .stateIn(viewModelScope, sharing, null)

    fun toggleHold() {
        val active = uiState.value as? MissionUiState.Active ?: return
        viewModelScope.launch { issueCommand(if (active.holding) MissionCommand.RESUME else MissionCommand.HOLD) }
    }

    fun returnToBase() {
        viewModelScope.launch { issueCommand(MissionCommand.RTB) }
    }

    fun replan(onReplanned: () -> Unit) {
        viewModelScope.launch {
            replanMission()
            onReplanned()
        }
    }
}
