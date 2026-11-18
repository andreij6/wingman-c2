package com.wingmanc2.feature.intel.domain

import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.RawObservation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLineageUseCase @Inject constructor(private val repository: OntologyRepository) {
    operator fun invoke(entityId: Long): Flow<List<RawObservation>> = repository.observeLineage(entityId)
}
