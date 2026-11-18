package com.wingmanc2.feature.sync.domain

import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.core.model.StoreSnapshot

enum class MirrorStatus { IN_SYNC, REPLICATING, UNREACHABLE }

data class SyncDashboard(
    val link: LinkState,
    val edge: StoreSnapshot,
    val cloud: StoreSnapshot,
    val cloudStatus: MirrorStatus,
    val outbox: List<OutboxEvent>,
    val unsyncedCount: Int,
    val conflictCount: Int,
) {
    companion object {
        fun of(link: LinkState, outbox: List<OutboxEvent>, edge: StoreSnapshot, cloud: StoreSnapshot): SyncDashboard {
            var unsynced = 0
            var conflicts = 0
            for (event in outbox) {
                if (event.status != OutboxStatus.SYNCED) unsynced++
                if (event.status == OutboxStatus.CONFLICT) conflicts++
            }
            val status = when {
                link.mode == LinkMode.DENIED || !cloud.reachable -> MirrorStatus.UNREACHABLE
                cloud.version >= edge.version && unsynced == 0 -> MirrorStatus.IN_SYNC
                else -> MirrorStatus.REPLICATING
            }
            return SyncDashboard(link, edge, cloud, status, outbox, unsynced, conflicts)
        }
    }
}
