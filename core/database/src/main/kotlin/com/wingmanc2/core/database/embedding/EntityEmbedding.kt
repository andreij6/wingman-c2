package com.wingmanc2.core.database.embedding

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.model.OntologyEntity

internal const val EMBEDDING_DIMENSIONS_LONG: Long = EMBEDDING_DIM.toLong()

internal fun OntologyEntity.semanticallyEquals(other: OntologyEntity): Boolean =
    kind == other.kind &&
        affiliation == other.affiliation &&
        key == other.key &&
        label == other.label &&
        subtitle == other.subtitle &&
        attributes == other.attributes
