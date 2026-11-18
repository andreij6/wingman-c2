package com.wingmanc2.feature.intel.domain

import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.EntityGraph
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveEntityGraphUseCase @Inject constructor(private val repository: OntologyRepository) {
    operator fun invoke(focusId: Long): Flow<EntityGraph> = repository.observeGraph(focusId)
}
