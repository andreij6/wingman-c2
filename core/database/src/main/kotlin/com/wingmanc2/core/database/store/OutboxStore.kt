package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.mapping.Codes
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.record.OntologyEntityRecord
import com.wingmanc2.core.database.record.OutboxEventRecord
import com.wingmanc2.core.database.record.OutboxEventRecord_
import com.wingmanc2.core.model.OutboxEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class OutboxCounts(val pending: Int, val unsynced: Int)

data class OutboxEntry(val event: OutboxEvent, val baseVersion: Long?)

sealed interface OutboxOutcome {
    val eventId: Long

    data class Synced(override val eventId: Long, val version: Long) : OutboxOutcome

    data class Conflicted(override val eventId: Long, val remoteVersion: Long) : OutboxOutcome

    data class Dropped(override val eventId: Long) : OutboxOutcome
}

@Singleton
class OutboxStore @Inject constructor(private val database: WingmanDatabase) {

    private val box get() = database.outbox
    private val syncedRetention = BoundedRetention(capacity = SYNCED_RETAINED, checkEvery = SYNCED_RETENTION_CHECK)

    fun observeRecent(syncedLimit: Int): Flow<List<OutboxEvent>> = database.observeChanges(OutboxEventRecord::class.java) {
        val open = box.query(OutboxEventRecord_.status.oneOf(OUTBOX_OPEN))
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

    fun observeCounts(): Flow<OutboxCounts> = database.observeChanges(OutboxEventRecord::class.java) {
        val statuses = box.query(OutboxEventRecord_.status.oneOf(OUTBOX_OPEN)).build()
            .use { it.property(OutboxEventRecord_.status).findInts() }
        OutboxCounts(pending = statuses.count { it == OUTBOX_PENDING }, unsynced = statuses.size)
    }

    suspend fun get(id: Long): OutboxEntry? = database.read { box.get(id)?.let { OutboxEntry(it.toModel(), it.baseVersion) } }

    suspend fun claimPending(limit: Int): List<OutboxEntry> = database.write {
        val claimed = box.query(OutboxEventRecord_.status.equal(OUTBOX_PENDING))
            .order(OutboxEventRecord_.enqueuedAtMs)
            .build()
            .use { it.find(0, limit.toLong()) }
        claimed.forEach { it.status = OUTBOX_IN_FLIGHT }
        box.put(claimed)
        claimed.map { OutboxEntry(it.toModel(), it.baseVersion) }
    }

    suspend fun requeue(ids: LongArray) = database.write {
        box.get(ids).filterNotNull().filter { it.status == OUTBOX_IN_FLIGHT }.forEach(box::requeue)
    }

    suspend fun recoverInFlight() = database.write {
        box.query(OutboxEventRecord_.status.equal(OUTBOX_IN_FLIGHT)).build().use { it.find() }.forEach(box::requeue)
    }

    suspend fun applyOutcomes(outcomes: List<OutboxOutcome>) {
        if (outcomes.isEmpty()) return
        database.write {
            val events = box.get(LongArray(outcomes.size) { outcomes[it].eventId })
            val keys = HashSet<String>()
            var synced = 0
            outcomes.forEachIndexed { i, outcome ->
                val event = events[i]?.takeIf { it.status == OUTBOX_IN_FLIGHT } ?: return@forEachIndexed
                keys += event.entityKey
                when (outcome) {
                    is OutboxOutcome.Synced -> if (event.localVersion > outcome.version) {
                        event.baseVersion = outcome.version
                        box.requeue(event)
                    } else {
                        event.status = OUTBOX_SYNCED
                        event.remoteVersion = outcome.version
                        box.put(event)
                        synced++
                    }
                    is OutboxOutcome.Conflicted -> box.markConflicted(event, outcome.remoteVersion, box.openFor(event.entityKey))
                    is OutboxOutcome.Dropped -> box.requeue(event)
                }
            }
            refreshSyncStates(database.entities.byKeys(keys))
            if (synced > 0) {
                syncedRetention.afterWrite(box, synced) {
                    box.query(OutboxEventRecord_.status.equal(OUTBOX_SYNCED)).order(OutboxEventRecord_.enqueuedAtMs).build()
                }
            }
        }
    }

    suspend fun markOpenConflict(entityKey: String, remoteVersion: Long): Boolean = database.write {
        val open = box.openFor(entityKey)
        val event = open.firstOrNull { it.status == OUTBOX_CONFLICT }
            ?: open.firstOrNull { it.status == OUTBOX_PENDING }
            ?: open.firstOrNull { it.status == OUTBOX_IN_FLIGHT }
            ?: return@write false
        box.markConflicted(event, remoteVersion, open)
        refreshSyncStates(listOfNotNull(database.entities.byKey(entityKey)))
        true
    }

    suspend fun resolveKeepEdge(id: Long, remoteVersion: Long) = database.write {
        val event = box.get(id)?.takeIf { it.status == OUTBOX_CONFLICT } ?: return@write
        val remote = maxOf(event.remoteVersion ?: remoteVersion, remoteVersion)
        val entity = database.entities.byKey(event.entityKey)
        val version = maxOf(entity?.version ?: 0L, event.localVersion, remote) + 1
        event.localVersion = version
        event.baseVersion = remote
        event.remoteVersion = remote
        box.requeue(event)
        entity?.let {
            it.version = version
            refreshSyncStates(listOf(it))
        }
    }

    suspend fun resolveTakeCloud(id: Long, remoteVersion: Long) = database.write {
        val event = box.get(id)?.takeIf { it.status == OUTBOX_CONFLICT } ?: return@write
        val remote = maxOf(event.remoteVersion ?: remoteVersion, remoteVersion)
        box.openFor(event.entityKey).filter { it.status == OUTBOX_PENDING }.forEach { box.remove(it.id) }
        event.status = OUTBOX_SYNCED
        event.remoteVersion = remote
        box.put(event)
        database.entities.byKey(event.entityKey)?.let {
            it.version = remote
            refreshSyncStates(listOf(it))
        }
    }

    suspend fun replicatedVersions(): Map<String, Long> = database.read {
        val pendingBase = HashMap<String, Long>()
        val inFlightBase = HashMap<String, Long>()
        val conflictRemote = HashMap<String, Long>()
        box.query(OutboxEventRecord_.status.oneOf(OUTBOX_OPEN)).orderDesc(OutboxEventRecord_.id).build().use { it.find() }
            .forEach { event ->
                when (event.status) {
                    OUTBOX_CONFLICT -> event.remoteVersion?.let { conflictRemote.putIfAbsent(event.entityKey, it) }
                    OUTBOX_PENDING -> event.baseVersion?.let { pendingBase.putIfAbsent(event.entityKey, it) }
                    else -> event.baseVersion?.let { inFlightBase.putIfAbsent(event.entityKey, it) }
                }
            }
        val versions = HashMap<String, Long>()
        for (entity in database.entities.all) {
            val version = if (entity.syncState == ENTITY_SYNCED) {
                entity.version
            } else {
                conflictRemote[entity.key] ?: pendingBase[entity.key] ?: inFlightBase[entity.key]
            }
            if (version != null) versions[entity.key] = version
        }
        versions
    }

    private fun refreshSyncStates(entities: List<OntologyEntityRecord>) {
        if (entities.isEmpty()) return
        val open = box.openFor(entities.map(OntologyEntityRecord::key))
        for (entity in entities) entity.syncState = Codes.syncState.encode(derivedState(open[entity.key].orEmpty()))
        database.entities.put(entities)
    }

    private companion object {
        const val SYNCED_RETAINED = 256
        const val SYNCED_RETENTION_CHECK = 64
    }
}
