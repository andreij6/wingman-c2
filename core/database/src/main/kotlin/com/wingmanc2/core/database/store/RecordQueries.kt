package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.mapping.Codes
import com.wingmanc2.core.database.record.OntologyEntityRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord_
import com.wingmanc2.core.database.record.OutboxEventRecord
import com.wingmanc2.core.database.record.OutboxEventRecord_
import com.wingmanc2.core.model.OutboxOp
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.core.model.SyncState
import io.objectbox.Box
import io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE

internal const val ONTOLOGY_ENTITY_TYPE = "OntologyEntity"

internal val OUTBOX_PENDING = Codes.outboxStatus.encode(OutboxStatus.PENDING)
internal val OUTBOX_IN_FLIGHT = Codes.outboxStatus.encode(OutboxStatus.IN_FLIGHT)
internal val OUTBOX_SYNCED = Codes.outboxStatus.encode(OutboxStatus.SYNCED)
internal val OUTBOX_CONFLICT = Codes.outboxStatus.encode(OutboxStatus.CONFLICT)
internal val ENTITY_SYNCED = Codes.syncState.encode(SyncState.SYNCED)

internal fun Box<OntologyEntityRecord>.byKey(key: String): OntologyEntityRecord? =
    query(OntologyEntityRecord_.key.equal(key, CASE_SENSITIVE)).build().use { it.findUnique() }

internal fun Box<OntologyEntityRecord>.byKeys(keys: Collection<String>): Map<String, OntologyEntityRecord> =
    if (keys.isEmpty()) {
        emptyMap()
    } else {
        query(OntologyEntityRecord_.key.oneOf(keys.toTypedArray(), CASE_SENSITIVE)).build()
            .use { it.find() }
            .associateBy(OntologyEntityRecord::key)
    }

internal fun Box<OutboxEventRecord>.openFor(entityKey: String, status: Int): OutboxEventRecord? =
    query(
        OutboxEventRecord_.entityKey.equal(entityKey, CASE_SENSITIVE)
            .and(OutboxEventRecord_.status.equal(status)),
    ).orderDesc(OutboxEventRecord_.id).build().use { it.findFirst() }

internal fun Box<OutboxEventRecord>.pendingFor(entityKey: String): OutboxEventRecord? = openFor(entityKey, OUTBOX_PENDING)

internal fun Box<OutboxEventRecord>.enqueueCoalesced(
    entityType: String,
    entityKey: String,
    op: OutboxOp,
    payloadBytes: Int,
    nowMs: Long,
    localVersion: Long,
    baseVersion: Long?,
): SyncState {
    val conflict = openFor(entityKey, OUTBOX_CONFLICT)
    if (conflict != null) {
        conflict.localVersion = localVersion
        conflict.payloadBytes = payloadBytes
        put(conflict)
        return SyncState.CONFLICT
    }
    val pending = pendingFor(entityKey)
    if (pending == null) {
        put(
            OutboxEventRecord(
                entityType = entityType,
                entityKey = entityKey,
                op = Codes.outboxOp.encode(op),
                payloadBytes = payloadBytes,
                enqueuedAtMs = nowMs,
                status = OUTBOX_PENDING,
                localVersion = localVersion,
                baseVersion = openFor(entityKey, OUTBOX_IN_FLIGHT)?.localVersion ?: baseVersion,
            ),
        )
        return SyncState.PENDING
    }
    val merged = Codes.outboxOp.decode(pending.op).followedBy(op)
    if (merged == null) {
        remove(pending.id)
        return SyncState.PENDING
    }
    pending.op = Codes.outboxOp.encode(merged)
    pending.payloadBytes = payloadBytes
    pending.localVersion = localVersion
    put(pending)
    return SyncState.PENDING
}

internal fun Box<OutboxEventRecord>.requeue(event: OutboxEventRecord) {
    val newer = pendingFor(event.entityKey)?.takeIf { it.id != event.id }
    if (newer == null) {
        event.status = OUTBOX_PENDING
        event.retryCount += 1
        put(event)
        return
    }
    remove(event.id)
    val merged = Codes.outboxOp.decode(event.op).followedBy(Codes.outboxOp.decode(newer.op))
    if (merged == null) {
        remove(newer.id)
        return
    }
    newer.op = Codes.outboxOp.encode(merged)
    newer.enqueuedAtMs = minOf(newer.enqueuedAtMs, event.enqueuedAtMs)
    newer.retryCount = maxOf(newer.retryCount, event.retryCount + 1)
    newer.localVersion = maxOf(newer.localVersion, event.localVersion)
    newer.baseVersion = event.baseVersion
    put(newer)
}

private fun OutboxOp.followedBy(next: OutboxOp): OutboxOp? = when {
    this == OutboxOp.INSERT && next == OutboxOp.DELETE -> null
    this == OutboxOp.INSERT -> OutboxOp.INSERT
    else -> next
}
