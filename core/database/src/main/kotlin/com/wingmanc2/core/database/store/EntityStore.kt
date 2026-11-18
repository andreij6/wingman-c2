package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.algorithms.embedding.EMBEDDING_DIM
import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.database.embedding.embed
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord_
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.OutboxOp
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class EntityMatch(val entity: OntologyEntity, val distance: Double)

data class EntityStoreStats(val objectCount: Int, val maxVersion: Long)

@Singleton
class EntityStore @Inject constructor(
    private val database: WingmanDatabase,
    private val hasher: FeatureHasher,
) {
    private val box get() = database.entities

    fun observeAll(): Flow<List<OntologyEntity>> =
        database.observe(box.query().order(OntologyEntityRecord_.id).build()) { records ->
            records.map(OntologyEntityRecord::toModel)
        }

    fun observe(id: Long): Flow<OntologyEntity?> =
        database.observe(box.query(OntologyEntityRecord_.id.equal(id)).build()) { records ->
            records.firstOrNull()?.toModel()
        }

    suspend fun get(id: Long): OntologyEntity? = database.read { box.get(id)?.toModel() }

    suspend fun findByKey(key: String): OntologyEntity? = database.read { box.byKey(key)?.toModel() }

    suspend fun findByKeys(keys: Collection<String>): Map<String, OntologyEntity> =
        database.read { box.byKeys(keys).mapValues { (_, record) -> record.toModel() } }

    suspend fun count(): Long = database.read { box.count() }

    fun observeStats(): Flow<EntityStoreStats> = database.observeChanges(box) {
        val maxVersion = box.query().build().use { it.property(OntologyEntityRecord_.version).max() }
        EntityStoreStats(objectCount = box.count().toInt(), maxVersion = maxVersion.coerceAtLeast(0L))
    }

    suspend fun putAll(entities: Collection<OntologyEntity>): List<OntologyEntity> = database.write {
        val latestByKey = entities.associateBy(OntologyEntity::key)
        val existing = box.byKeys(latestByKey.keys)
        val records = latestByKey.values.map { entity ->
            entity.toRecord(id = existing[entity.key]?.id ?: entity.id, embedding = hasher.embed(entity))
        }
        box.put(records)
        records.map(OntologyEntityRecord::toModel)
    }

    suspend fun upsertWithOutbox(
        entity: OntologyEntity,
        op: OutboxOp,
        payloadBytes: Int,
        nowMs: Long,
    ): OntologyEntity = database.write {
        val existing = box.byKey(entity.key)
        val version = maxOf(existing?.version ?: 0L, entity.version) + 1
        val base = existing?.takeIf { it.syncState == ENTITY_SYNCED }?.version
        val state = database.outbox.enqueueCoalesced(ONTOLOGY_ENTITY_TYPE, entity.key, op, payloadBytes, nowMs, version, base)
        val staged = entity.copy(version = version, updatedAtMs = nowMs, syncState = state)
        val record = staged.toRecord(id = existing?.id ?: entity.id, embedding = hasher.embed(staged))
        if (op == OutboxOp.DELETE) existing?.let { box.remove(it.id) } else box.put(record)
        record.toModel()
    }

    suspend fun similar(embedding: FloatArray, k: Int): List<EntityMatch> {
        require(embedding.size == EMBEDDING_DIM) { "Expected $EMBEDDING_DIM dimensions, got ${embedding.size}" }
        require(k > 0) { "k must be positive" }
        return database.read {
            box.query(OntologyEntityRecord_.embedding.nearestNeighbors(embedding, k)).build().use { query ->
                query.findWithScores().map { EntityMatch(it.get().toModel(), it.score) }
            }
        }
    }

    suspend fun similar(text: String, k: Int): List<EntityMatch> = similar(hasher.embed(text), k)
}
