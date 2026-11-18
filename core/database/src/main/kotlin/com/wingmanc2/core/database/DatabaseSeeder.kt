package com.wingmanc2.core.database

import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.OntologyEmbeddingRecord
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
    private val hasher: FeatureHasher,
) {
    suspend fun seedIfEmpty(seed: SeedData): Boolean = database.write {
        if (!database.entities.isEmpty) {
            backfillEmbeddings()
            return@write false
        }
        database.entities.put(seed.entities.map { it.toRecord(id = it.id) })
        database.embeddings.put(seed.entities.map { OntologyEmbeddingRecord(it.id, hasher.embed(it)) })
        database.edges.put(seed.edges.map(OntologyEdge::toRecord))
        database.observations.put(seed.observations.map(RawObservation::toRecord))
        true
    }

    private fun backfillEmbeddings() {
        if (database.embeddings.count() >= database.entities.count()) return
        val missing = database.entities.all.filter { !database.embeddings.contains(it.id) }
        database.embeddings.put(missing.map { OntologyEmbeddingRecord(it.id, hasher.embed(it.toModel())) })
    }
}
