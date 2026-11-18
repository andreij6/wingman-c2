package com.wingmanc2.core.model

enum class LinkMode { ONLINE, DEGRADED, DENIED }

data class LinkState(val mode: LinkMode, val rttMs: Int?, val lossPct: Int, val queued: Int, val lastAckMs: Long?)

enum class OutboxOp { INSERT, UPSERT, DELETE }

enum class OutboxStatus { PENDING, IN_FLIGHT, SYNCED, CONFLICT }

data class OutboxEvent(
    val id: Long,
    val entityType: String,
    val entityKey: String,
    val op: OutboxOp,
    val payloadBytes: Int,
    val enqueuedAtMs: Long,
    val retryCount: Int,
    val status: OutboxStatus,
    val localVersion: Long,
    val remoteVersion: Long?,
)

data class StoreSnapshot(val name: String, val version: Long, val objectCount: Int, val reachable: Boolean, val lastAckMs: Long?)

enum class ConflictResolution { KEEP_EDGE, TAKE_CLOUD }

enum class ScenarioInject { POPUP_RF_THREAT, CONFLICTING_REPORT, SENSOR_BURST, RESTORE_LINK }
