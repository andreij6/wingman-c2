package com.wingmanc2.core.model

sealed interface QueryPredicate {
    data class KindIs(val kind: EntityKind) : QueryPredicate

    data class AffiliationIs(val affiliation: Affiliation) : QueryPredicate

    data class VerifiedIs(val verified: Boolean) : QueryPredicate

    data class Near(val anchorKey: String, val radiusM: Double) : QueryPredicate

    data class SeenWithin(val minutes: Int) : QueryPredicate

    data class Text(val term: String) : QueryPredicate
}

data class ParsedQuery(val raw: String, val predicates: List<QueryPredicate>)

data class QueryResult(val query: ParsedQuery, val matches: List<OntologyEntity>, val latencyMicros: Long)
