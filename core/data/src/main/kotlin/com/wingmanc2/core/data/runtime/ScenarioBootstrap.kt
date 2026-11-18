package com.wingmanc2.core.data.runtime

import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.data.index.EntityIndex
import com.wingmanc2.core.data.ontology.SessionFocusRepository
import com.wingmanc2.core.database.DatabaseSeeder
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.OutboxStore
import com.wingmanc2.core.simulation.cloud.SimulatedCloudMirror
import com.wingmanc2.core.simulation.scenario.ScenarioKeys
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class ScenarioBootstrap @Inject constructor(
    private val seeder: DatabaseSeeder,
    private val entities: EntityStore,
    private val outbox: OutboxStore,
    private val index: EntityIndex,
    private val focus: SessionFocusRepository,
    private val mirror: SimulatedCloudMirror,
    private val clock: Clock,
) {
    suspend fun run() {
        seeder.seedIfEmpty(ScenarioSeedMapper.seedData(clock.nowMs()))
        val stored = entities.observeAll().first()
        stored.forEach(index::register)
        mirror.restore(outbox.replicatedVersions())
        stored.firstOrNull { it.key == ScenarioKeys.TRK_07 }?.let { focus.focusIfUnset(it.id) }
    }
}
