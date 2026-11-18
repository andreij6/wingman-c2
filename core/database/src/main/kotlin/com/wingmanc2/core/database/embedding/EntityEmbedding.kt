package com.wingmanc2.core.database.embedding

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.model.OntologyEntity

internal const val EMBEDDING_DIMENSIONS_LONG: Long = EMBEDDING_DIM.toLong()

fun OntologyEntity.semanticText(): String = buildString(INITIAL_CAPACITY) {
    append(kind.name).append(' ').append(affiliation.name).append(' ')
    append(key).append(' ').append(label).append(' ').append(subtitle)
    for ((name, value) in attributes) append(' ').append(name).append(' ').append(value)
}

internal fun FeatureHasher.embed(entity: OntologyEntity): FloatArray = embed(entity.semanticText())

private const val INITIAL_CAPACITY = 160
