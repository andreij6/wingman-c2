package com.wingmanc2.core.data.runtime

import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.data.ingest.IngestionPipeline
import com.wingmanc2.core.data.mission.MissionRuntime
import com.wingmanc2.core.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WingmanRuntime @Inject internal constructor(
    private val bootstrap: ScenarioBootstrap,
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
            ingestion.start()
            sync.start()
            missions.start()
        }
    }
}
