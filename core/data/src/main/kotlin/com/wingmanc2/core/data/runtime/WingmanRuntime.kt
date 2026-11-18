package com.wingmanc2.core.data.runtime

import android.util.Log
import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.data.ingest.IngestionPipeline
import com.wingmanc2.core.data.mission.MissionRuntime
import com.wingmanc2.core.data.sync.SyncEngine
import com.wingmanc2.core.simulation.feed.MessyFeedSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WingmanRuntime @Inject internal constructor(
    private val bootstrap: ScenarioBootstrap,
    private val feed: MessyFeedSimulator,
    private val ingestion: IngestionPipeline,
    private val sync: SyncEngine,
    private val missions: MissionRuntime,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            bootstrap.run()
            launch { feed.feed().collect { message -> ingestSafely { ingestion.ingest(message) } } }
            sync.start()
            missions.start()
        }
    }

    private suspend fun ingestSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "Feed message rejected", failure)
        }
    }

    private companion object {
        const val TAG = "WingmanRuntime"
    }
}
