package com.wingmanc2.core.database

import com.wingmanc2.core.database.embedding.SemanticEmbedder
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawObservation
import javax.inject.Inject
import javax.inject.Singleton

data class SeedData(
    val entities: List<OntologyEntity>,
    val edges: List<OntologyEdge>,
    val observations: List<RawObservation>,
)

@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: WingmanDatabase,
    private val embedder: SemanticEmbedder,
) {
    suspend fun isEmpty(): Boolean = database.read { database.entities.isEmpty }

    suspend fun seedIfEmpty(seed: SeedData): Boolean = database.write {
        if (!database.entities.isEmpty) return@write false
        database.entities.put(seed.entities.map { it.toRecord(id = it.id, embedding = embedder.embed(it)) })
        database.edges.put(seed.edges.map(OntologyEdge::toRecord))
        database.observations.put(seed.observations.map(RawObservation::toRecord))
        true
    }
}
