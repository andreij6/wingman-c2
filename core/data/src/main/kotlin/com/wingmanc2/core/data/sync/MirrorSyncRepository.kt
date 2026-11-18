package com.wingmanc2.core.data.sync

import com.wingmanc2.core.algorithms.sync.Reconciliation
import com.wingmanc2.core.algorithms.sync.VersionReconciler
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
    private val reconciler: VersionReconciler,
) : SyncRepository {

    override fun observeOutbox(): Flow<List<OutboxEvent>> = outbox.observeRecent(SYNCED_SHOWN)

    override fun observeEdge(): Flow<StoreSnapshot> = entities.observeStats().map { stats ->
        StoreSnapshot(EDGE_NAME, stats.maxVersion, stats.objectCount, reachable = true, lastAckMs = null)
    }

    override fun observeCloud(): Flow<StoreSnapshot> = mirror.observeSnapshot()

    override suspend fun resolve(eventId: Long, resolution: ConflictResolution) {
        val entry = outbox.get(eventId)?.takeIf { it.event.status == OutboxStatus.CONFLICT } ?: return
        val event = entry.event
        val remote = event.remoteVersion ?: mirror.record(event.entityKey)?.version ?: return
        val conflict = Reconciliation.Conflict(base = entry.baseVersion ?: 0L, local = event.localVersion, remote = remote)
        when (val outcome = reconciler.resolve(conflict, resolution)) {
            is Reconciliation.Push -> outbox.resolveKeepEdge(eventId, outcome.version)
            is Reconciliation.Pull -> entities.findByKey(event.entityKey)?.let { local ->
                outbox.resolveTakeCloud(eventId, local.copy(version = outcome.version))
            }
            Reconciliation.AlreadySynced, is Reconciliation.Conflict -> Unit
        }
    }

    private companion object {
        const val EDGE_NAME = "Edge · ObjectBox"
        const val SYNCED_SHOWN = 40
    }
}
