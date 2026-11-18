package com.wingmanc2.feature.plan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.feature.plan.domain.CoaPlan
import com.wingmanc2.feature.plan.domain.DEFAULT_TARGET_KEY
import com.wingmanc2.feature.plan.domain.DispatchWingmanMissionUseCase
import com.wingmanc2.feature.plan.domain.FocusPlanningTargetUseCase
import com.wingmanc2.feature.plan.domain.GenerateExplainableCoasUseCase
import com.wingmanc2.feature.plan.domain.ObservePlanningTargetUseCase
import com.wingmanc2.feature.plan.domain.ParseIntentUseCase
import com.wingmanc2.feature.plan.domain.ResolveIntentTargetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class PlanViewModel @Inject constructor(
    observePlanningTarget: ObservePlanningTargetUseCase,
    private val parseIntent: ParseIntentUseCase,
    private val generateCoas: GenerateExplainableCoasUseCase,
    private val resolveIntentTarget: ResolveIntentTargetUseCase,
    private val focusPlanningTarget: FocusPlanningTargetUseCase,
    private val dispatchMission: DispatchWingmanMissionUseCase,
    @Dispatcher(WingmanDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val customIntents = MutableStateFlow(persistentMapOf<String, String>())
    private val selection = MutableStateFlow<String?>(null)
    private val dispatching = MutableStateFlow(false)

    private val snapshot: StateFlow<PlanSnapshot> = combine(observePlanningTarget(), customIntents) { target, custom ->
        PlanRequest(target, custom[target.key]?.let(parseIntent::invoke) ?: parseIntent.defaultFor(target.key))
    }
        .distinctUntilChanged()
        .transformLatest { request ->
            emit(PlanStep.Started(request, request.intent.toUi()))
            val plan = generateCoas(request.intent, request.target)
            emit(PlanStep.Finished(plan, plan.toResultUi()))
        }
        .scan(PlanSnapshot.Empty) { acc, step ->
            when (step) {
                is PlanStep.Started -> acc.copy(targetKey = step.request.target.key, intent = step.intent, busy = true)
                is PlanStep.Finished -> acc.copy(plan = step.plan, result = step.result, busy = false)
            }
        }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanSnapshot.Empty)

    val uiState: StateFlow<PlanUiState> = combine(snapshot, selection, dispatching) { snapshot, selected, sending ->
        val courses = snapshot.result?.courses ?: persistentListOf()
        PlanUiState(
            targetKey = snapshot.targetKey,
            intent = snapshot.intent,
            planning = snapshot.busy,
            preview = snapshot.result?.preview,
            courses = courses,
            selectedCode = selected?.takeIf { code -> courses.any { it.code == code } } ?: courses.firstOrNull()?.code,
            dispatching = sending,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState.Initial)

    fun select(code: String) {
        selection.value = code
    }

    fun applyIntent(text: String) {
        val trimmed = text.trim()
        val current = snapshot.value
        if (trimmed.isEmpty() || current.intent == null) return
        val currentKey = current.targetKey
        viewModelScope.launch {
            val retarget = resolveIntentTarget(parseIntent(trimmed), currentKey)
            customIntents.update { it.putting(retarget?.key ?: currentKey, trimmed) }
            retarget?.let(focusPlanningTarget::invoke)
        }
    }

    fun approve(onDispatched: () -> Unit) {
        val state = uiState.value
        val plan = snapshot.value.plan ?: return
        val code = state.selectedCode ?: return
        if (!state.canDispatch || !dispatching.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                dispatchMission(plan, code)
                onDispatched()
            } finally {
                dispatching.value = false
            }
        }
    }

    private data class PlanRequest(val target: OntologyEntity, val intent: CommanderIntent)

    private sealed interface PlanStep {
        data class Started(val request: PlanRequest, val intent: IntentUi) : PlanStep
        data class Finished(val plan: CoaPlan, val result: PlanResultUi) : PlanStep
    }

    private data class PlanSnapshot(
        val targetKey: String,
        val intent: IntentUi?,
        val plan: CoaPlan?,
        val result: PlanResultUi?,
        val busy: Boolean,
    ) {
        companion object {
            val Empty = PlanSnapshot(DEFAULT_TARGET_KEY, intent = null, plan = null, result = null, busy = true)
        }
    }
}
