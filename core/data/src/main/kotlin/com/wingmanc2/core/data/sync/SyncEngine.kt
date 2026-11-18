package com.wingmanc2.core.data.sync

import com.wingmanc2.core.algorithms.sync.Backoff
import com.wingmanc2.core.algorithms.sync.OutboxCoalescer
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.database.store.OutboxEntry
import com.wingmanc2.core.database.store.OutboxStore
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.simulation.cloud.MirrorWrite
import com.wingmanc2.core.simulation.cloud.PushResult
import com.wingmanc2.core.simulation.cloud.SimulatedCloudMirror
import com.wingmanc2.core.simulation.link.LinkSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SyncEngine @Inject constructor(
    private val outbox: OutboxStore,
    private val mirror: SimulatedCloudMirror,
    private val link: LinkSimulator,
    private val coalescer: OutboxCoalescer,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) {
    private val backoff = Backoff()

    fun start() {
        scope.launch(default) {
            outbox.recoverInFlight()
            combine(
                link.quality.map { it.mode }.distinctUntilChanged(),
                outbox.observeCounts().map { it.pending }.distinctUntilChanged(),
            ) { mode, pending -> mode != LinkMode.DENIED && pending > 0 }
                .conflate()
                .collect { ready -> if (ready) drain() }
        }
    }

    private suspend fun drain() {
        var attempt = 0
        while (link.mode != LinkMode.DENIED) {
            val batch = outbox.pendingBatch(BATCH_SIZE)
            if (batch.isEmpty()) return
            if (push(batch) == 0) {
                attempt = 0
                continue
            }
            delay(backoff.delayMs(attempt, salt = batch.first().event.id))
            attempt++
        }
    }

    private suspend fun push(batch: List<OutboxEntry>): Int {
        val baseByKey = HashMap<String, Long?>(batch.size * 2)
        for (entry in batch) if (entry.event.entityKey !in baseByKey) baseByKey[entry.event.entityKey] = entry.baseVersion
        val writes = coalescer.coalesce(batch.map(OutboxEntry::event))
        val ids = LongArray(writes.size) { writes[it].id }
        val keyById = HashMap<Long, String>(writes.size * 2)
        writes.forEach { keyById[it.id] = it.entityKey }
        val superseded = batch.filter { it.event.id !in keyById }.groupBy({ it.event.entityKey }, { it.event.id })
        outbox.markInFlight(ids)
        val results = try {
            mirror.push(writes.map { MirrorWrite(it.id, it.entityKey, it.op, it.localVersion, baseByKey[it.entityKey], it.payloadBytes) })
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { outbox.requeue(ids) }
            throw cancelled
        }
        val dropped = ArrayList<Long>()
        for (result in results) {
            when (result) {
                is PushResult.Ack -> {
                    outbox.markSynced(result.eventId, result.version)
                    superseded[keyById[result.eventId]]?.forEach { outbox.markSynced(it, result.version) }
                }
                is PushResult.Conflict -> outbox.markConflict(result.eventId, result.remoteVersion)
                is PushResult.Dropped -> dropped += result.eventId
            }
        }
        if (dropped.isNotEmpty()) outbox.requeue(dropped.toLongArray())
        return dropped.size
    }

    private companion object {
        const val BATCH_SIZE = 32
    }
}
