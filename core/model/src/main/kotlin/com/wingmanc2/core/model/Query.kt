package com.wingmanc2.core.model

sealed interface QueryPredicate {
    val display: String

    data class KindIs(val kind: EntityKind) : QueryPredicate {
        override val display get() = "kind:${kind.name}"
    }

    data class AffiliationIs(val affiliation: Affiliation) : QueryPredicate {
        override val display get() = "affil:${affiliation.name.lowercase()}"
    }

    data class VerifiedIs(val verified: Boolean) : QueryPredicate {
        override val display get() = if (verified) "status:verified" else "status:unverified"
    }

    data class Near(val anchorKey: String, val radiusM: Double) : QueryPredicate {
        override val display get() = "near:$anchorKey ≤${radiusM.toInt()}m"
    }

    data class SeenWithin(val minutes: Int) : QueryPredicate {
        override val display get() = "seen:≤${minutes}m"
    }

    data class Text(val term: String) : QueryPredicate {
        override val display get() = "text:$term"
    }
}

data class ParsedQuery(val raw: String, val predicates: List<QueryPredicate>)

data class QueryResult(val query: ParsedQuery, val matches: List<OntologyEntity>, val latencyMicros: Long)
