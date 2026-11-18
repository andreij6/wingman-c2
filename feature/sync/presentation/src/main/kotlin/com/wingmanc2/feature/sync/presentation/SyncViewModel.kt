package com.wingmanc2.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.ScenarioInject
import com.wingmanc2.feature.sync.domain.InjectScenarioUseCase
import com.wingmanc2.feature.sync.domain.ObserveSyncDashboardUseCase
import com.wingmanc2.feature.sync.domain.ResolveConflictUseCase
import com.wingmanc2.feature.sync.domain.SetLinkModeUseCase
import com.wingmanc2.feature.sync.domain.SyncDashboard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SyncViewModel @Inject constructor(
    observeSyncDashboard: ObserveSyncDashboardUseCase,
    private val setLinkMode: SetLinkModeUseCase,
    private val resolveConflict: ResolveConflictUseCase,
    private val injectScenario: InjectScenarioUseCase,
    clock: Clock,
    @Dispatcher(WingmanDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val uiState: StateFlow<SyncUiState> = combine(observeSyncDashboard(), clock.ticks(), SyncDashboard::toUiState)
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState.Loading)

    fun onLinkModeSelected(mode: LinkMode) = setLinkMode(mode)

    fun onResolve(eventId: Long, resolution: ConflictResolution) {
        viewModelScope.launch { resolveConflict(eventId, resolution) }
    }

    fun onInject(inject: ScenarioInject) {
        viewModelScope.launch { injectScenario(inject) }
    }
}

private fun Clock.ticks(periodMs: Long = 1_000): Flow<Long> = flow {
    while (true) {
        emit(nowMs())
        delay(periodMs)
    }
}
