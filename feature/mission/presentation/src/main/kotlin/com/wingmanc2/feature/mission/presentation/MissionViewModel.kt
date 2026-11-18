package com.wingmanc2.feature.mission.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.ObserveLinkStateUseCase
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.MissionCommand
import com.wingmanc2.core.ui.WhileUiSubscribed
import com.wingmanc2.core.ui.launchAction
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
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

    private val userMessage = MutableStateFlow<String?>(null)

    private val mission = observeMission()
        .distinctUntilChanged()
        .flowOn(default)
        .shareIn(viewModelScope, WhileUiSubscribed, replay = 1)

    private val frames = observeTelemetry()
        .map<LiveTelemetry, LiveTelemetry?> { it }
        .onStart { emit(null) }
        .flowOn(default)
        .shareIn(viewModelScope, WhileUiSubscribed, replay = 1)

    private val progress = frames
        .map { live -> live?.frame?.let { RouteProgress(it.waypointIndex, it.waypointCount) } }
        .distinctUntilChanged()

    private val flownTrack = observeFlownTrack()
        .map<List<LocalPoint>, ImmutableList<LocalPoint>> { it.toImmutableList() }
        .onStart { emit(persistentListOf()) }

    val uiState: StateFlow<MissionUiState> = combine(mission, progress, userMessage) { picture, route, message ->
        picture.toUiState(route, message)
    }
        .flowOn(default)
        .stateIn(viewModelScope, WhileUiSubscribed, MissionUiState.Loading)

    val live: StateFlow<MissionLiveUi?> = combine(frames, mission.filterNotNull(), observeLinkState(), flownTrack) { frame, picture, link, trail ->
        frame.toLiveUi(picture, link, trail)
    }
        .flowOn(default)
        .stateIn(viewModelScope, WhileUiSubscribed, null)

    fun toggleHold() {
        val active = uiState.value as? MissionUiState.Active ?: return
        launchAction({ userMessage.value = "Hold command was not delivered" }) { issueCommand(if (active.holding) MissionCommand.RESUME else MissionCommand.HOLD) }
    }

    fun returnToBase() {
        launchAction({ userMessage.value = "Return to base was not delivered" }) { issueCommand(MissionCommand.RTB) }
    }

    fun replan() {
        launchAction({ userMessage.value = "Replan request failed" }) { replanMission() }
    }

    fun onMessageShown() {
        userMessage.value = null
    }
}
