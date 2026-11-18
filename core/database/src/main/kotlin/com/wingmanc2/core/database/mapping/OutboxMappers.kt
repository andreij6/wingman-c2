package com.wingmanc2.core.database.mapping

import com.wingmanc2.core.database.record.OutboxEventRecord
import com.wingmanc2.core.model.OutboxEvent

internal fun OutboxEventRecord.toModel(): OutboxEvent = OutboxEvent(
    id = id,
    entityType = entityType,
    entityKey = entityKey,
    op = Codes.outboxOp.decode(op),
    payloadBytes = payloadBytes,
    enqueuedAtMs = enqueuedAtMs,
    retryCount = retryCount,
    status = Codes.outboxStatus.decode(status),
    localVersion = localVersion,
    remoteVersion = remoteVersion,
)
