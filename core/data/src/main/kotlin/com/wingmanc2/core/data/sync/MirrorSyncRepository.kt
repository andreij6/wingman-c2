package com.wingmanc2.core.data.sync

import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.OutboxStore
import com.wingmanc2.core.domain.SyncRepository
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.OutboxStatus
import com.wingmanc2.core.model.StoreSnapshot
import com.wingmanc2.core.simulation.cloud.SimulatedCloudMirror
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class MirrorSyncRepository @Inject constructor(
    private val outbox: OutboxStore,
    private val entities: EntityStore,
    private val mirror: SimulatedCloudMirror,
) : SyncRepository {

    override fun observeOutbox(): Flow<List<OutboxEvent>> = outbox.observeRecent(SYNCED_SHOWN)

    override fun observeEdge(): Flow<StoreSnapshot> = entities.observeStats().map { stats ->
        StoreSnapshot(EDGE_NAME, stats.maxVersion, stats.objectCount, reachable = true, lastAckMs = null)
    }

    override fun observeCloud(): Flow<StoreSnapshot> = mirror.observeSnapshot()

    override suspend fun resolve(eventId: Long, resolution: ConflictResolution) {
        val event = outbox.get(eventId)?.event?.takeIf { it.status == OutboxStatus.CONFLICT } ?: return
        val remote = event.remoteVersion ?: mirror.record(event.entityKey)?.version ?: return
        when (resolution) {
            ConflictResolution.KEEP_EDGE -> outbox.resolveKeepEdge(eventId, remote)
            ConflictResolution.TAKE_CLOUD -> outbox.resolveTakeCloud(eventId, remote)
        }
    }

    private companion object {
        const val EDGE_NAME = "Edge · ObjectBox"
        const val SYNCED_SHOWN = 40
    }
}
