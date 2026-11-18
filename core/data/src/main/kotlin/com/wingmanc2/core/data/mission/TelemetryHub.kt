package com.wingmanc2.core.data.mission

import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.TelemetryStore
import com.wingmanc2.core.domain.TelemetryStreamSource
import com.wingmanc2.core.model.FlownTrack
import com.wingmanc2.core.model.TelemetryFrame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TelemetryHub @Inject constructor(
    private val telemetryStore: TelemetryStore,
    private val entities: EntityStore,
    private val index: EntityIndex,
) : TelemetryStreamSource {

    private val ring = TelemetryRing(TRAIL_CAPACITY)
    private val frames = MutableSharedFlow<TelemetryFrame>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val trackState = MutableStateFlow(FlownTrack.EMPTY)
    private var missionId = NO_MISSION
    private var assetKey: String? = null
    private var framesSinceTrail = 0
    private var lastPersistedMs = NEVER
    private var lastAssetSyncMs = NEVER

    @Volatile
    private var assetSyncRequested = false

    val latest: TelemetryFrame? get() = frames.replayCache.lastOrNull()

    override fun telemetry(): Flow<TelemetryFrame> = frames.asSharedFlow()

    override fun flownTrack(): Flow<FlownTrack> = trackState.asStateFlow()

    suspend fun begin(missionId: Long, assetKey: String?) {
        end()
        this.missionId = missionId
        this.assetKey = assetKey
        telemetryStore.trail(missionId, TRAIL_CAPACITY).forEach(ring::append)
        publishTrail()
    }

    fun end() {
        missionId = NO_MISSION
        assetKey = null
        ring.clear()
        frames.resetReplayCache()
        trackState.value = FlownTrack.EMPTY
        framesSinceTrail = 0
        lastPersistedMs = NEVER
        lastAssetSyncMs = NEVER
        assetSyncRequested = false
    }

    fun requestAssetSync() {
        assetSyncRequested = true
    }

    suspend fun onFrame(frame: TelemetryFrame) {
        if (missionId == NO_MISSION) return
        ring.append(frame)
        frames.emit(frame)
        if (++framesSinceTrail >= TRAIL_EVERY_FRAMES) {
            framesSinceTrail = 0
            publishTrail()
        }
        if (lastPersistedMs == NEVER || frame.tMs - lastPersistedMs >= PERSIST_INTERVAL_MS) {
            lastPersistedMs = frame.tMs
            telemetryStore.append(missionId, frame)
        }
        if (assetSyncRequested || lastAssetSyncMs == NEVER || frame.tMs - lastAssetSyncMs >= ASSET_SYNC_INTERVAL_MS) {
            assetSyncRequested = false
            lastAssetSyncMs = frame.tMs
            syncAsset(frame)
        }
    }

    private suspend fun syncAsset(frame: TelemetryFrame) {
        val key = assetKey ?: return
        val stored = entities.upsertWithOutbox(key, ASSET_PAYLOAD_BYTES, frame.tMs) { asset ->
            asset?.copy(position = frame.position, headingDeg = frame.headingDeg)
        } ?: return
        index.register(stored.entity, stored.embedding)
    }

    private fun publishTrail() {
        trackState.value = ring.track()
    }

    private companion object {
        const val NO_MISSION = Long.MIN_VALUE
        const val NEVER = -1L
        const val TRAIL_CAPACITY = 240
        const val TRAIL_EVERY_FRAMES = 5
        const val PERSIST_INTERVAL_MS = 1_000L
        const val ASSET_SYNC_INTERVAL_MS = 30_000L
        const val ASSET_PAYLOAD_BYTES = 184
    }
}
