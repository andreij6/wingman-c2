package com.wingmanc2.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.common.ticks
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.ScenarioInject
import com.wingmanc2.core.ui.WhileUiSubscribed
import com.wingmanc2.core.ui.launchAction
import com.wingmanc2.feature.sync.domain.InjectScenarioUseCase
import com.wingmanc2.feature.sync.domain.ObserveSyncDashboardUseCase
import com.wingmanc2.feature.sync.domain.ResolveConflictUseCase
import com.wingmanc2.feature.sync.domain.SetLinkModeUseCase
import com.wingmanc2.feature.sync.domain.SyncDashboard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
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

    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SyncUiState> = combine(observeSyncDashboard(), clock.ticks(TICK_MS), userMessage, SyncDashboard::toUiState)
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, SyncUiState.Loading)

    fun onLinkModeSelected(mode: LinkMode) = setLinkMode(mode)

    fun onResolve(eventId: Long, resolution: ConflictResolution) {
        launchAction({ userMessage.value = "Conflict #$eventId could not be resolved" }) { resolveConflict(eventId, resolution) }
    }

    fun onInject(inject: ScenarioInject) {
        launchAction({ userMessage.value = "Scenario inject failed" }) { injectScenario(inject) }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
