package com.wingmanc2.core.simulation.cloud

import com.wingmanc2.core.model.OutboxOp

data class MirrorRecord(val version: Long, val payloadBytes: Int, val updatedAtMs: Long)

data class MirrorWrite(
    val eventId: Long,
    val entityKey: String,
    val op: OutboxOp,
    val localVersion: Long,
    val expectedRemoteVersion: Long?,
    val payloadBytes: Int,
)

sealed interface PushResult {
    val eventId: Long

    data class Ack(override val eventId: Long, val version: Long, val ackedAtMs: Long) : PushResult

    data class Conflict(override val eventId: Long, val remoteVersion: Long) : PushResult

    data class Dropped(override val eventId: Long) : PushResult
}
