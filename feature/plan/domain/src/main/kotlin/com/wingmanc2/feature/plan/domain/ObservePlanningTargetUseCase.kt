package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.algorithms.planning.planningSignature
import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ScenarioProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class ObservePlanningTargetUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val ontology: OntologyRepository,
    private val profile: ScenarioProfile,
) {
    operator fun invoke(): Flow<OntologyEntity> = focus.focusedEntityId
        .flatMapLatest { id -> if (id == null) defaultTarget() else focused(id) }
        .distinctUntilChangedBy { it.planningSignature }

    private fun focused(id: Long): Flow<OntologyEntity> = ontology.observeEntity(id).flatMapLatest { entity ->
        if (entity != null && entity.isPlanningTarget) flowOf(entity) else defaultTarget()
    }

    private fun defaultTarget(): Flow<OntologyEntity> =
        ontology.observeEntities().mapNotNull { entities -> entities.firstOrNull { it.key == profile.defaultFocusKey } }
}
