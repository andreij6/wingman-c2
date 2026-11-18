package com.wingmanc2.core.database.store

import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.embedding.semanticallyEquals
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.OntologyEmbeddingRecord
import com.wingmanc2.core.database.record.OntologyEmbeddingRecord_
import com.wingmanc2.core.database.record.OntologyEntityRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord_
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.OutboxOp
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class EntityMatch(val entityId: Long, val distance: Double)

data class EntityStoreStats(val objectCount: Int, val maxVersion: Long)

class StoredEntity(val entity: OntologyEntity, val embedding: FloatArray)

@Singleton
class EntityStore @Inject constructor(
    private val database: WingmanDatabase,
    private val hasher: FeatureHasher,
) {
    private val box get() = database.entities
    private val embeddings get() = database.embeddings

    fun observeAll(): Flow<List<OntologyEntity>> =
        database.observe({ box.query().order(OntologyEntityRecord_.id).build() }) { records ->
            records.map(OntologyEntityRecord::toModel)
        }

    fun observe(id: Long): Flow<OntologyEntity?> =
        database.observe({ box.query(OntologyEntityRecord_.id.equal(id)).build() }) { records ->
            records.firstOrNull()?.toModel()
        }

    suspend fun get(id: Long): OntologyEntity? = database.read { box.get(id)?.toModel() }

    suspend fun getAll(ids: Collection<Long>): List<OntologyEntity> =
        if (ids.isEmpty()) emptyList() else database.read { box.get(ids.toLongArray()).mapNotNull { it?.toModel() } }

    suspend fun findByKey(key: String): OntologyEntity? = database.read { box.byKey(key)?.toModel() }

    suspend fun count(): Long = database.read { box.count() }

    suspend fun loadIndexed(): List<StoredEntity> = database.write {
        box.all.map { record -> StoredEntity(record.toModel(), embeddingOf(record)) }
    }

    fun observeStats(): Flow<EntityStoreStats> = database.observeChanges(OntologyEntityRecord::class.java) {
        val maxVersion = box.query().build().use { it.property(OntologyEntityRecord_.version).max() }
        EntityStoreStats(objectCount = box.count().toInt(), maxVersion = maxVersion.coerceAtLeast(0L))
    }

    suspend fun upsertWithOutbox(
        key: String,
        payloadBytes: Int,
        nowMs: Long,
        transform: (OntologyEntity?) -> OntologyEntity?,
    ): StoredEntity? = database.write {
        val existing = box.byKey(key)
        val current = existing?.toModel()
        val next = transform(current) ?: return@write null
        stage(existing, current, next, if (existing == null) OutboxOp.INSERT else OutboxOp.UPSERT, payloadBytes, nowMs)
    }

    suspend fun similar(embedding: FloatArray, k: Int): List<EntityMatch> {
        require(embedding.size == EMBEDDING_DIM) { "Expected $EMBEDDING_DIM dimensions, got ${embedding.size}" }
        require(k > 0) { "k must be positive" }
        return database.read {
            embeddings.query(OntologyEmbeddingRecord_.embedding.nearestNeighbors(embedding, k)).build().use { query ->
                query.findIdsWithScores().map { EntityMatch(it.id, it.score) }
            }
        }
    }

    private fun stage(
        existing: OntologyEntityRecord?,
        current: OntologyEntity?,
        next: OntologyEntity,
        op: OutboxOp,
        payloadBytes: Int,
        nowMs: Long,
    ): StoredEntity {
        val version = maxOf(existing?.version ?: 0L, next.version) + 1
        val base = existing?.takeIf { it.syncState == ENTITY_SYNCED }?.version
        val state = database.outbox.enqueueCoalesced(ONTOLOGY_ENTITY_TYPE, next.key, op, payloadBytes, nowMs, version, base)
        val staged = next.copy(version = version, updatedAtMs = nowMs, syncState = state)
        val record = staged.toRecord(id = existing?.id ?: next.id)
        box.put(record)
        val stored = staged.copy(id = record.id)
        val changed = current == null || !current.semanticallyEquals(stored)
        return StoredEntity(stored, if (changed) embed(stored) else embeddingOf(record))
    }

    private fun embeddingOf(record: OntologyEntityRecord): FloatArray =
        embeddings.get(record.id)?.embedding ?: embed(record.toModel())

    private fun embed(entity: OntologyEntity): FloatArray =
        hasher.embed(entity).also { embeddings.put(OntologyEmbeddingRecord(entity.id, it)) }
}
