package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.algorithms.nlp.QueryParser
import com.wingmanc2.core.domain.OntologyRepository
import javax.inject.Inject

class RunNovaQueryUseCase @Inject constructor(
    private val parser: QueryParser,
    private val ontology: OntologyRepository,
) {
    suspend operator fun invoke(raw: String): NovaResult? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val result = ontology.query(parser.parse(text))
        return NovaResult(result.matches.mapTo(HashSet(result.matches.size)) { it.id })
    }
}
