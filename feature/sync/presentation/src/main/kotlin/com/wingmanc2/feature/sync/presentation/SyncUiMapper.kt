package com.wingmanc2.feature.sync.presentation

import com.wingmanc2.core.common.formatAge
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.core.ui.StatusTone
import com.wingmanc2.feature.sync.domain.MirrorStatus
import com.wingmanc2.feature.sync.domain.SyncDashboard
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

internal fun SyncDashboard.toUiState(nowMs: Long, userMessage: String?): SyncUiState.Ready {
    val shown = outbox.sortedBy { it.status != OutboxStatus.CONFLICT }.take(OUTBOX_ROWS_SHOWN)
    return SyncUiState.Ready(
        linkMode = link.mode,
        linkStats = link.statsLine(link.lastAckMs ?: cloud.lastAckMs, nowMs),
        edge = StoreTileUi(edge.name, "v${edge.version}", objectsLabel(edge.objectCount), "Authoritative", StatusTone.Ok),
        cloud = StoreTileUi(cloud.name, "v${cloud.version}", objectsLabel(cloud.objectCount), cloudStatus.label, cloudStatus.tone),
        queued = link.queued,
        outboxSummary = outboxSummary(unsyncedCount, conflictCount),
        outbox = shown.map { it.toRow(nowMs) }.toImmutableList(),
        hiddenCount = (unsyncedCount - shown.count { it.status != OutboxStatus.SYNCED }).coerceAtLeast(0),
        userMessage = userMessage,
    )
}

private fun LinkState.statsLine(lastAckMs: Long?, nowMs: Long): String {
    val rtt = rttMs?.let { "RTT $it ms" } ?: "RTT —"
    val ack = lastAckMs?.let { "last ack ${formatAge(nowMs - it)} ago" } ?: "last ack —"
    return "$rtt  ·  loss $lossPct%  ·  $ack"
}

private val MirrorStatus.label: String
    get() = when (this) {
        MirrorStatus.IN_SYNC -> "In sync"
        MirrorStatus.REPLICATING -> "Replicating"
        MirrorStatus.UNREACHABLE -> "Unreachable"
    }

private val MirrorStatus.tone: StatusTone
    get() = when (this) {
        MirrorStatus.IN_SYNC -> StatusTone.Ok
        MirrorStatus.REPLICATING -> StatusTone.Primary
        MirrorStatus.UNREACHABLE -> StatusTone.Warn
    }

private fun outboxSummary(unsynced: Int, conflicts: Int): String = when {
    unsynced == 0 -> "All synced"
    conflicts == 0 -> "$unsynced pending"
    conflicts == 1 -> "$unsynced pending · 1 conflict"
    else -> "$unsynced pending · $conflicts conflicts"
}

private fun OutboxEvent.toRow(nowMs: Long): OutboxRowUi {
    val detail = when (status) {
        OutboxStatus.CONFLICT -> "${op.name} · v$localVersion vs cloud ${remoteVersion?.let { "v$it" } ?: "—"}"
        OutboxStatus.SYNCED -> "${op.name} · acked by mirror"
        OutboxStatus.PENDING, OutboxStatus.IN_FLIGHT -> "${op.name} · ${sizeLabel(payloadBytes)} · ${formatAge(nowMs - enqueuedAtMs)}"
    }
    return OutboxRowUi(
        id = id,
        idLabel = "#$id",
        title = "$entityType · $entityKey",
        detail = detail,
        statusLabel = status.label,
        tone = status.tone,
        settled = status == OutboxStatus.SYNCED,
        resolvable = status == OutboxStatus.CONFLICT,
    )
}

private val OutboxStatus.label: String
    get() = when (this) {
        OutboxStatus.PENDING -> "Pending"
        OutboxStatus.IN_FLIGHT -> "In flight"
        OutboxStatus.SYNCED -> "Synced"
        OutboxStatus.CONFLICT -> "Conflict"
    }

private val OutboxStatus.tone: StatusTone
    get() = when (this) {
        OutboxStatus.PENDING -> StatusTone.Warn
        OutboxStatus.IN_FLIGHT -> StatusTone.Primary
        OutboxStatus.SYNCED -> StatusTone.Ok
        OutboxStatus.CONFLICT -> StatusTone.Critical
    }

private fun objectsLabel(count: Int): String = String.format(Locale.ROOT, "%,d objects", count)

private fun sizeLabel(bytes: Int): String = String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)


private const val OUTBOX_ROWS_SHOWN = 5
