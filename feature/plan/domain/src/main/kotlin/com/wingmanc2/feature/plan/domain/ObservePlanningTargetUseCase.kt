package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.OntologyEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class ObservePlanningTargetUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val ontology: OntologyRepository,
) {
    operator fun invoke(): Flow<OntologyEntity> = focus.focusedEntityId
        .flatMapLatest { id -> if (id == null) defaultTarget() else focused(id) }
        .distinctUntilChanged()

    private fun focused(id: Long): Flow<OntologyEntity> = ontology.observeEntity(id).flatMapLatest { entity ->
        if (entity != null && entity.isPlanningTarget) flowOf(entity) else defaultTarget()
    }

    private fun defaultTarget(): Flow<OntologyEntity> =
        ontology.observeEntities().mapNotNull { entities -> entities.firstOrNull { it.key == DEFAULT_TARGET_KEY } }
}
