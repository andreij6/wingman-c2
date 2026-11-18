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
internal val OUTBOX_OPEN = intArrayOf(OUTBOX_PENDING, OUTBOX_IN_FLIGHT, OUTBOX_CONFLICT)
internal val ENTITY_SYNCED = Codes.syncState.encode(SyncState.SYNCED)

internal fun Box<OntologyEntityRecord>.byKey(key: String): OntologyEntityRecord? =
    query(OntologyEntityRecord_.key.equal(key, CASE_SENSITIVE)).build().use { it.findUnique() }

internal fun Box<OntologyEntityRecord>.byKeys(keys: Collection<String>): List<OntologyEntityRecord> =
    if (keys.isEmpty()) {
        emptyList()
    } else {
        query(OntologyEntityRecord_.key.oneOf(keys.toTypedArray(), CASE_SENSITIVE)).build().use { it.find() }
    }

internal fun Box<OutboxEventRecord>.openFor(entityKey: String): List<OutboxEventRecord> =
    query(OutboxEventRecord_.entityKey.equal(entityKey, CASE_SENSITIVE).and(OutboxEventRecord_.status.oneOf(OUTBOX_OPEN)))
        .orderDesc(OutboxEventRecord_.id)
        .build()
        .use { it.find() }

internal fun Box<OutboxEventRecord>.openFor(entityKeys: Collection<String>): Map<String, List<OutboxEventRecord>> =
    if (entityKeys.isEmpty()) {
        emptyMap()
    } else {
        query(
            OutboxEventRecord_.entityKey.oneOf(entityKeys.toTypedArray(), CASE_SENSITIVE)
                .and(OutboxEventRecord_.status.oneOf(OUTBOX_OPEN)),
        ).orderDesc(OutboxEventRecord_.id).build().use { it.find() }.groupBy(OutboxEventRecord::entityKey)
    }

internal fun derivedState(open: List<OutboxEventRecord>): SyncState = when {
    open.any { it.status == OUTBOX_CONFLICT } -> SyncState.CONFLICT
    open.any { it.status == OUTBOX_PENDING || it.status == OUTBOX_IN_FLIGHT } -> SyncState.PENDING
    else -> SyncState.SYNCED
}

internal fun Box<OutboxEventRecord>.enqueueCoalesced(
    entityType: String,
    entityKey: String,
    op: OutboxOp,
    payloadBytes: Int,
    nowMs: Long,
    localVersion: Long,
    baseVersion: Long?,
): SyncState {
    val open = openFor(entityKey)
    val conflict = open.firstOrNull { it.status == OUTBOX_CONFLICT }
    if (conflict != null) {
        conflict.localVersion = localVersion
        conflict.payloadBytes = payloadBytes
        put(conflict)
        return SyncState.CONFLICT
    }
    val inFlight = open.firstOrNull { it.status == OUTBOX_IN_FLIGHT }
    val pending = open.firstOrNull { it.status == OUTBOX_PENDING }
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
                baseVersion = inFlight?.localVersion ?: baseVersion,
            ),
        )
        return SyncState.PENDING
    }
    val merged = Codes.outboxOp.decode(pending.op).followedBy(op)
    if (merged == null) {
        remove(pending.id)
        return if (inFlight == null) SyncState.SYNCED else SyncState.PENDING
    }
    pending.op = Codes.outboxOp.encode(merged)
    pending.payloadBytes = payloadBytes
    pending.localVersion = localVersion
    put(pending)
    return SyncState.PENDING
}

internal fun Box<OutboxEventRecord>.requeue(event: OutboxEventRecord) {
    val newer = openFor(event.entityKey).firstOrNull { it.status == OUTBOX_PENDING && it.id != event.id }
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

internal fun Box<OutboxEventRecord>.markConflicted(event: OutboxEventRecord, remoteVersion: Long, open: List<OutboxEventRecord>) {
    val target = open.firstOrNull { it.status == OUTBOX_CONFLICT && it.id != event.id } ?: event
    if (target !== event) absorb(target, event)
    for (other in open) {
        if (other.id != target.id && other.id != event.id && other.status == OUTBOX_PENDING) absorb(target, other)
    }
    target.status = OUTBOX_CONFLICT
    target.remoteVersion = maxOf(target.remoteVersion ?: remoteVersion, remoteVersion)
    put(target)
}

private fun Box<OutboxEventRecord>.absorb(target: OutboxEventRecord, other: OutboxEventRecord) {
    if (other.localVersion >= target.localVersion) {
        target.localVersion = other.localVersion
        target.payloadBytes = other.payloadBytes
    }
    remove(other.id)
}
