package com.wingmanc2.feature.sync.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.feature.sync.domain.MirrorStatus
import com.wingmanc2.feature.sync.domain.SyncDashboard
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

internal enum class Tone { Ok, Warn, Critical, Primary }

@Immutable
internal data class StoreTileUi(
    val title: String,
    val version: String,
    val objects: String,
    val status: String,
    val tone: Tone,
)

@Immutable
internal data class OutboxRowUi(
    val id: Long,
    val idLabel: String,
    val title: String,
    val detail: String,
    val statusLabel: String,
    val tone: Tone,
    val settled: Boolean,
    val resolvable: Boolean,
)

@Immutable
internal sealed interface SyncUiState {
    data object Loading : SyncUiState

    data class Ready(
        val linkMode: LinkMode,
        val linkStats: String,
        val edge: StoreTileUi,
        val cloud: StoreTileUi,
        val queued: Int,
        val outboxSummary: String,
        val outbox: ImmutableList<OutboxRowUi>,
    ) : SyncUiState
}

internal fun SyncDashboard.toUiState(nowMs: Long): SyncUiState.Ready = SyncUiState.Ready(
    linkMode = link.mode,
    linkStats = link.statsLine(link.lastAckMs ?: cloud.lastAckMs, nowMs),
    edge = StoreTileUi("Edge · ObjectBox", "v${edge.version}", objectsLabel(edge.objectCount), "Authoritative", Tone.Ok),
    cloud = StoreTileUi("Cloud mirror", "v${cloud.version}", objectsLabel(cloud.objectCount), cloudStatus.label, cloudStatus.tone),
    queued = link.queued,
    outboxSummary = outboxSummary(unsyncedCount, conflictCount),
    outbox = outbox.map { it.toRow(nowMs) }.toImmutableList(),
)

private fun LinkState.statsLine(lastAckMs: Long?, nowMs: Long): String {
    val rtt = rttMs?.let { "RTT $it ms" } ?: "RTT —"
    val ack = lastAckMs?.let { "last ack ${ageLabel(nowMs - it)} ago" } ?: "last ack —"
    return "$rtt  ·  loss $lossPct%  ·  $ack"
}

private val MirrorStatus.label: String
    get() = when (this) {
        MirrorStatus.IN_SYNC -> "In sync"
        MirrorStatus.REPLICATING -> "Replicating"
        MirrorStatus.UNREACHABLE -> "Unreachable"
    }

private val MirrorStatus.tone: Tone
    get() = when (this) {
        MirrorStatus.IN_SYNC -> Tone.Ok
        MirrorStatus.REPLICATING -> Tone.Primary
        MirrorStatus.UNREACHABLE -> Tone.Warn
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
        OutboxStatus.PENDING, OutboxStatus.IN_FLIGHT -> "${op.name} · ${sizeLabel(payloadBytes)} · ${ageLabel(nowMs - enqueuedAtMs)}"
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

private val OutboxStatus.tone: Tone
    get() = when (this) {
        OutboxStatus.PENDING -> Tone.Warn
        OutboxStatus.IN_FLIGHT -> Tone.Primary
        OutboxStatus.SYNCED -> Tone.Ok
        OutboxStatus.CONFLICT -> Tone.Critical
    }

private fun objectsLabel(count: Int): String = String.format(Locale.US, "%,d objects", count)

private fun sizeLabel(bytes: Int): String = String.format(Locale.US, "%.1f KB", bytes / 1024.0)

private fun ageLabel(elapsedMs: Long): String {
    val seconds = (elapsedMs / 1_000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "$seconds s"
        seconds < 3_600 -> "${seconds / 60} min"
        else -> "${seconds / 3_600} h"
    }
}
