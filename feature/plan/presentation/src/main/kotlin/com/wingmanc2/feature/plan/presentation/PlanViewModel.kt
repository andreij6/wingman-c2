package com.wingmanc2.feature.plan.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.domain.FocusEntityUseCase
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.ui.WhileUiSubscribed
import com.wingmanc2.core.ui.launchAction
import com.wingmanc2.feature.plan.domain.CoaPlan
import com.wingmanc2.feature.plan.domain.DispatchWingmanMissionUseCase
import com.wingmanc2.feature.plan.domain.GenerateExplainableCoasUseCase
import com.wingmanc2.feature.plan.domain.ObservePlanningTargetUseCase
import com.wingmanc2.feature.plan.domain.ParseIntentUseCase
import com.wingmanc2.feature.plan.domain.ResolveIntentTargetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
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

@HiltViewModel
internal class PlanViewModel @Inject constructor(
    observePlanningTarget: ObservePlanningTargetUseCase,
    private val parseIntent: ParseIntentUseCase,
    private val generateCoas: GenerateExplainableCoasUseCase,
    private val resolveIntentTarget: ResolveIntentTargetUseCase,
    private val focusEntity: FocusEntityUseCase,
    private val dispatchMission: DispatchWingmanMissionUseCase,
    @Dispatcher(WingmanDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val intentDraft = TextFieldState()

    private val customIntents = MutableStateFlow(persistentMapOf<String, String>())
    private val selection = MutableStateFlow<String?>(null)
    private val action = MutableStateFlow(ActionState())

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
        .stateIn(viewModelScope, WhileUiSubscribed, PlanSnapshot.Empty)

    val uiState: StateFlow<PlanUiState> = combine(snapshot, selection, action) { snapshot, selected, action ->
        val intent = snapshot.intent ?: return@combine PlanUiState.Loading
        val courses = snapshot.result?.courses ?: persistentListOf()
        PlanUiState.Ready(
            targetKey = snapshot.targetKey,
            intent = intent,
            planning = snapshot.busy,
            preview = snapshot.result?.preview,
            courses = courses,
            selectedCode = selected?.takeIf { code -> courses.any { it.code == code } } ?: courses.firstOrNull()?.code,
            dispatching = action.dispatching,
            dispatched = action.dispatched,
            userMessage = action.userMessage,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, PlanUiState.Loading)

    fun select(code: String) {
        selection.value = code
    }

    fun editIntent(text: String) {
        intentDraft.setTextAndPlaceCursorAtEnd(text)
    }

    fun applyIntent() {
        val trimmed = intentDraft.text.toString().trim()
        val current = snapshot.value
        if (trimmed.isEmpty() || current.intent == null) return
        val currentKey = current.targetKey
        launchAction({ action.update { it.copy(userMessage = "Could not apply that intent") } }) {
            val retarget = resolveIntentTarget(parseIntent(trimmed), currentKey)
            customIntents.update { it.putting(retarget?.key ?: currentKey, trimmed) }
            retarget?.let { focusEntity(it.id) }
        }
    }

    fun approve() {
        val state = uiState.value as? PlanUiState.Ready ?: return
        val plan = snapshot.value.plan ?: return
        val code = state.selectedCode ?: return
        if (!state.canDispatch || action.value.dispatching) return
        action.update { it.copy(dispatching = true, userMessage = null) }
        viewModelScope.launch {
            val dispatched = try {
                dispatchMission(plan, code) != null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                false
            }
            action.value = if (dispatched) ActionState(dispatched = true) else ActionState(userMessage = "Dispatch of $code failed")
        }
    }

    fun onDispatchHandled() {
        action.update { it.copy(dispatched = false) }
    }

    fun onMessageShown() {
        action.update { it.copy(userMessage = null) }
    }

    private data class PlanRequest(val target: OntologyEntity, val intent: CommanderIntent)

    private data class ActionState(val dispatching: Boolean = false, val dispatched: Boolean = false, val userMessage: String? = null)

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
            val Empty = PlanSnapshot(targetKey = "", intent = null, plan = null, result = null, busy = true)
        }
    }
}
