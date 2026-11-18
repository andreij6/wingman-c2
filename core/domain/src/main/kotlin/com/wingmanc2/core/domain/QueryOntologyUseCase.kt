package com.wingmanc2.core.domain

import com.wingmanc2.core.algorithms.nlp.QueryParser
import com.wingmanc2.core.model.QueryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class QueryOntologyUseCase @Inject constructor(
    private val parser: QueryParser,
    private val ontology: OntologyRepository,
) {
    suspend operator fun invoke(raw: String): QueryResult = ontology.query(parser.parse(raw.trim()))

    fun live(raw: String): Flow<QueryResult> {
        val parsed = parser.parse(raw.trim())
        return ontology.observeEntities().conflate().map { ontology.query(parsed) }
    }
}
