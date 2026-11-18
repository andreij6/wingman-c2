package com.wingmanc2.feature.intel.domain

import com.wingmanc2.core.algorithms.nlp.QueryParser
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.QueryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class QueryOntologyNlUseCase @Inject constructor(
    private val parser: QueryParser,
    private val repository: OntologyRepository,
) {
    operator fun invoke(raw: String): Flow<QueryResult> {
        val parsed = parser.parse(raw)
        return repository.observeEntities()
            .conflate()
            .map { repository.query(parsed) }
    }
}
