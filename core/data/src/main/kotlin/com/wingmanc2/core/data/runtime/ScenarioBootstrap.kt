package com.wingmanc2.core.data.runtime

import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.data.ontology.SessionFocusRepository
import com.wingmanc2.core.database.DatabaseSeeder
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.OutboxStore
import com.wingmanc2.core.model.ScenarioProfile
import com.wingmanc2.core.simulation.cloud.SimulatedCloudMirror
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ScenarioBootstrap @Inject constructor(
    private val seeder: DatabaseSeeder,
    private val entities: EntityStore,
    private val outbox: OutboxStore,
    private val index: EntityIndex,
    private val focus: SessionFocusRepository,
    private val mirror: SimulatedCloudMirror,
    private val profile: ScenarioProfile,
    private val clock: Clock,
) {
    private val completion = CompletableDeferred<Unit>()

    suspend fun awaitReady() = completion.await()

    suspend fun run() {
        try {
            seeder.seedIfEmpty(ScenarioSeedMapper.seedData(clock.nowMs()))
            val stored = entities.loadIndexed()
            stored.forEach { index.register(it.entity, it.embedding) }
            mirror.restore(outbox.replicatedVersions())
            stored.firstOrNull { it.entity.key == profile.defaultFocusKey }?.let { focus.focusIfUnset(it.entity.id) }
            completion.complete(Unit)
        } catch (failure: Throwable) {
            completion.completeExceptionally(failure)
            throw failure
        }
    }
}
