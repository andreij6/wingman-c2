package com.wingmanc2.core.database.store

import com.wingmanc2.core.algorithms.embedding.FeatureHasher
import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.embedding.embed
import com.wingmanc2.core.database.mapping.Codes
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord_
import com.wingmanc2.core.database.record.OutboxEventRecord
import com.wingmanc2.core.database.record.OutboxEventRecord_
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class OutboxCounts(val pending: Int, val inFlight: Int, val synced: Int, val conflict: Int) {
    val unsynced: Int get() = pending + inFlight + conflict
}

data class OutboxEntry(val event: OutboxEvent, val baseVersion: Long?)

@Singleton
class OutboxStore @Inject constructor(
    private val database: WingmanDatabase,
    private val hasher: FeatureHasher,
) {
    private val box get() = database.outbox
    private val syncedRetention = BoundedRetention(capacity = SYNCED_RETAINED, checkEvery = SYNCED_RETENTION_CHECK)

    fun observeRecent(syncedLimit: Int): Flow<List<OutboxEvent>> = database.observeChanges(box) {
        val open = box.query(OutboxEventRecord_.status.notEqual(OUTBOX_SYNCED.toLong()))
            .orderDesc(OutboxEventRecord_.enqueuedAtMs)
            .build()
            .use { it.find() }
        val synced = box.query(OutboxEventRecord_.status.equal(OUTBOX_SYNCED))
            .orderDesc(OutboxEventRecord_.enqueuedAtMs)
            .build()
            .use { it.find(0, syncedLimit.toLong()) }
        ArrayList<OutboxEvent>(open.size + synced.size).apply {
            open.mapTo(this, OutboxEventRecord::toModel)
            synced.mapTo(this, OutboxEventRecord::toModel)
        }
    }

    fun observeCounts(): Flow<OutboxCounts> = database.observeChanges(box) {
        OutboxCounts(
            pending = countOf(OutboxStatus.PENDING),
            inFlight = countOf(OutboxStatus.IN_FLIGHT),
            synced = countOf(OutboxStatus.SYNCED),
            conflict = countOf(OutboxStatus.CONFLICT),
        )
    }

    suspend fun get(id: Long): OutboxEntry? = database.read { box.get(id)?.let { OutboxEntry(it.toModel(), it.baseVersion) } }

    suspend fun pendingBatch(limit: Int): List<OutboxEntry> = database.read {
        box.query(OutboxEventRecord_.status.equal(OUTBOX_PENDING))
            .order(OutboxEventRecord_.enqueuedAtMs)
            .build()
            .use { it.find(0, limit.toLong()) }
            .map { OutboxEntry(it.toModel(), it.baseVersion) }
    }

    suspend fun markInFlight(ids: LongArray) = database.write {
        val records = box.get(ids).filterNotNull().filter { it.status == OUTBOX_PENDING }
        records.forEach { it.status = OUTBOX_IN_FLIGHT }
        box.put(records)
    }

    suspend fun requeue(ids: LongArray) = database.write {
        box.get(ids).filterNotNull().filter { it.status == OUTBOX_IN_FLIGHT }.forEach(box::requeue)
    }

    suspend fun recoverInFlight() = database.write {
        box.query(OutboxEventRecord_.status.equal(OUTBOX_IN_FLIGHT)).build().use { it.find() }.forEach(box::requeue)
    }

    suspend fun markSynced(id: Long, remoteVersion: Long) = database.write {
        val event = box.get(id) ?: return@write
        event.status = OUTBOX_SYNCED
        event.remoteVersion = remoteVersion
        box.put(event)
        database.entities.byKey(event.entityKey)
            ?.takeIf { it.version <= event.localVersion && box.pendingFor(event.entityKey) == null }
            ?.let { entity ->
                entity.syncState = ENTITY_SYNCED
                database.entities.put(entity)
            }
        syncedRetention.afterWrite(box) {
            box.query(OutboxEventRecord_.status.equal(OUTBOX_SYNCED)).order(OutboxEventRecord_.enqueuedAtMs).build()
        }
    }

    suspend fun markConflict(id: Long, remoteVersion: Long) = database.write {
        val event = box.get(id) ?: return@write
        box.pendingFor(event.entityKey)?.let { newer ->
            event.localVersion = maxOf(event.localVersion, newer.localVersion)
            event.payloadBytes = newer.payloadBytes
            box.remove(newer.id)
        }
        event.status = OUTBOX_CONFLICT
        event.remoteVersion = remoteVersion
        box.put(event)
        database.entities.byKey(event.entityKey)?.let { entity ->
            entity.syncState = Codes.syncState.encode(SyncState.CONFLICT)
            database.entities.put(entity)
        }
    }

    suspend fun resolveKeepEdge(id: Long, version: Long) = database.write {
        val event = box.get(id)?.takeIf { it.status == OUTBOX_CONFLICT } ?: return@write
        database.entities.byKey(event.entityKey)?.let {
            it.version = version
            it.syncState = Codes.syncState.encode(SyncState.PENDING)
            database.entities.put(it)
        }
        event.localVersion = version
        event.baseVersion = event.remoteVersion
        box.requeue(event)
    }

    suspend fun resolveTakeCloud(id: Long, cloud: OntologyEntity) = database.write {
        val event = box.get(id)?.takeIf { it.status == OUTBOX_CONFLICT } ?: return@write
        val accepted = cloud.copy(syncState = SyncState.SYNCED)
        val localId = database.entities.byKey(event.entityKey)?.id ?: cloud.id
        database.entities.put(accepted.toRecord(id = localId, embedding = hasher.embed(accepted)))
        box.pendingFor(event.entityKey)?.let { box.remove(it.id) }
        event.status = OUTBOX_SYNCED
        event.remoteVersion = cloud.version
        box.put(event)
    }

    suspend fun replicatedVersions(): Map<String, Long> = database.read {
        val versions = HashMap<String, Long>()
        val entities = database.entities.query().order(OntologyEntityRecord_.id).build().use { it.find() }
        for (entity in entities) {
            val version = when {
                entity.syncState == ENTITY_SYNCED -> entity.version
                else -> box.openFor(entity.key, OUTBOX_CONFLICT)?.remoteVersion
                    ?: box.openFor(entity.key, OUTBOX_PENDING)?.baseVersion
                    ?: box.openFor(entity.key, OUTBOX_IN_FLIGHT)?.baseVersion
            }
            if (version != null) versions[entity.key] = version
        }
        versions
    }

    private fun countOf(status: OutboxStatus): Int =
        box.query(OutboxEventRecord_.status.equal(Codes.outboxStatus.encode(status))).build().use { it.count().toInt() }

    private companion object {
        const val SYNCED_RETAINED = 256
        const val SYNCED_RETENTION_CHECK = 64
    }
}
