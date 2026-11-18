package com.wingmanc2.core.simulation.cloud

import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.OutboxOp
import com.wingmanc2.core.model.StoreSnapshot
import com.wingmanc2.core.simulation.link.LinkSample
import com.wingmanc2.core.simulation.link.LinkSimulator
import com.wingmanc2.core.simulation.scenario.ScenarioSeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class SimulatedCloudMirror @Inject constructor(
    private val link: LinkSimulator,
    private val clock: Clock,
) {
    private val records = ConcurrentHashMap<String, MirrorRecord>()
    private val revision = MutableStateFlow(MirrorRevision(0L, 0))
    private val mutableLastAckMs = MutableStateFlow<Long?>(null)

    val lastAckMs: StateFlow<Long?> = mutableLastAckMs.asStateFlow()

    init {
        val seededAt = clock.nowMs()
        ScenarioSeed.entities.forEach { records[it.key] = MirrorRecord(it.version, SEED_PAYLOAD_BYTES, seededAt) }
        publish(ScenarioSeed.entities.maxOf { it.version })
    }

    fun restore(versions: Map<String, Long>) {
        val restoredAt = clock.nowMs()
        var maxVersion = 0L
        for ((key, version) in versions) {
            records.merge(key, MirrorRecord(version, SEED_PAYLOAD_BYTES, restoredAt)) { current, restored ->
                if (current.version == restored.version) current else restored.copy(payloadBytes = current.payloadBytes)
            }
            maxVersion = max(maxVersion, version)
        }
        publish(maxVersion)
    }

    suspend fun push(batch: List<MirrorWrite>): List<PushResult> {
        if (batch.isEmpty()) return emptyList()
        val delivered = BooleanArray(batch.size)
        var roundTripMs = -1
        for (i in batch.indices) {
            val sample = link.sample()
            if (sample is LinkSample.Delivered) {
                delivered[i] = true
                roundTripMs = max(roundTripMs, sample.rttMs)
            }
        }
        delay(if (roundTripMs < 0) TIMEOUT_MS else roundTripMs.toLong())
        val now = clock.nowMs()
        var maxVersion = 0L
        val results = batch.mapIndexed { i, write ->
            if (!delivered[i]) PushResult.Dropped(write.eventId) else apply(write, now).also {
                if (it is PushResult.Ack) maxVersion = max(maxVersion, it.version)
            }
        }
        if (maxVersion > 0L) {
            mutableLastAckMs.value = now
            publish(maxVersion)
        }
        return results
    }

    fun scriptConflict(entityKey: String): Long {
        var bumped = ScenarioSeed.INITIAL_VERSION
        records.compute(entityKey) { _, current ->
            MirrorRecord(
                version = (current?.version ?: ScenarioSeed.INITIAL_VERSION) + 1,
                payloadBytes = current?.payloadBytes ?: SEED_PAYLOAD_BYTES,
                updatedAtMs = clock.nowMs(),
            ).also { bumped = it.version }
        }
        publish(bumped)
        return bumped
    }

    fun record(entityKey: String): MirrorRecord? = records[entityKey]

    fun snapshot(): StoreSnapshot = snapshotOf(revision.value, link.mode != LinkMode.DENIED, mutableLastAckMs.value)

    fun observeSnapshot(): Flow<StoreSnapshot> = combine(
        revision,
        link.quality.map { it.mode != LinkMode.DENIED }.distinctUntilChanged(),
        mutableLastAckMs,
        ::snapshotOf,
    )

    private fun apply(write: MirrorWrite, now: Long): PushResult {
        var result: PushResult = PushResult.Ack(write.eventId, write.localVersion, now)
        records.compute(write.entityKey) { _, current ->
            when {
                current == null || current.version == write.expectedRemoteVersion -> next(write, now)
                else -> current.also { result = PushResult.Conflict(write.eventId, it.version) }
            }
        }
        return result
    }

    private fun next(write: MirrorWrite, now: Long): MirrorRecord? =
        if (write.op == OutboxOp.DELETE) null else MirrorRecord(write.localVersion, write.payloadBytes, now)

    private fun publish(version: Long) {
        revision.update { MirrorRevision(max(it.version, version), records.size) }
    }

    private fun snapshotOf(revision: MirrorRevision, reachable: Boolean, lastAckMs: Long?) =
        StoreSnapshot(NAME, revision.version, revision.objectCount, reachable, lastAckMs)

    private data class MirrorRevision(val version: Long, val objectCount: Int)

    private companion object {
        const val NAME = "Cloud mirror"
        const val TIMEOUT_MS = 1_500L
        const val SEED_PAYLOAD_BYTES = 612
    }
}
