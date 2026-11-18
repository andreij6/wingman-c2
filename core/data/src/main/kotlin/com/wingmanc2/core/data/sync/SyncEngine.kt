package com.wingmanc2.core.data.sync

import android.util.Log
import com.wingmanc2.core.algorithms.sync.Backoff
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.database.store.OutboxEntry
import com.wingmanc2.core.database.store.OutboxOutcome
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
                .collect { ready ->
                    if (!ready) return@collect
                    try {
                        drain()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.e(TAG, "Outbox drain failed", failure)
                    }
                }
        }
    }

    private suspend fun drain() {
        var attempt = 0
        while (link.mode != LinkMode.DENIED) {
            val batch = outbox.claimPending(BATCH_SIZE)
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
        val results = try {
            mirror.push(
                batch.map { (event, baseVersion) ->
                    MirrorWrite(event.id, event.entityKey, event.op, event.localVersion, baseVersion, event.payloadBytes)
                },
            )
        } catch (failure: Throwable) {
            withContext(NonCancellable) { outbox.requeue(LongArray(batch.size) { batch[it].event.id }) }
            throw failure
        }
        var dropped = 0
        outbox.applyOutcomes(
            results.map { result ->
                when (result) {
                    is PushResult.Ack -> OutboxOutcome.Synced(result.eventId, result.version)
                    is PushResult.Conflict -> OutboxOutcome.Conflicted(result.eventId, result.remoteVersion)
                    is PushResult.Dropped -> OutboxOutcome.Dropped(result.eventId).also { dropped++ }
                }
            },
        )
        return dropped
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val BATCH_SIZE = 32
    }
}
