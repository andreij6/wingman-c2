package com.wingmanc2.core.data.ontology

import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.data.runtime.ScenarioBootstrap
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ScenarioProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class StoreAssetRegistry @Inject constructor(
    private val streams: OntologyStreams,
    private val entityStore: EntityStore,
    private val bootstrap: ScenarioBootstrap,
    private val profile: ScenarioProfile,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) : AssetRegistry {

    override suspend fun primaryAsset(): OntologyEntity {
        bootstrap.awaitReady()
        return checkNotNull(entityStore.findByKey(profile.primaryAssetKey)) { "Primary asset ${profile.primaryAssetKey} is not seeded" }
    }

    override suspend fun threats(): List<OntologyEntity> = seeded().filter { it.affiliation == Affiliation.HOSTILE && it.threatRadiusM > 0.0 }

    override suspend fun noFlyZones(): List<OntologyEntity> =
        seeded().filter { it.kind == EntityKind.ZONE && it.footprint.size >= MIN_FOOTPRINT_VERTICES }

    override fun affiliationCounts(): Flow<Map<Affiliation, Int>> = streams.entities
        .map { entities ->
            val counts = EnumMap<Affiliation, Int>(Affiliation::class.java)
            Affiliation.entries.forEach { counts[it] = 0 }
            for (entity in entities) {
                if (entity.kind == EntityKind.ZONE || entity.kind == EntityKind.CLASS) continue
                counts[entity.affiliation] = counts.getValue(entity.affiliation) + 1
            }
            counts as Map<Affiliation, Int>
        }
        .distinctUntilChanged()
        .flowOn(default)

    private suspend fun seeded(): List<OntologyEntity> {
        bootstrap.awaitReady()
        return streams.entities.first()
    }

    private companion object {
        const val MIN_FOOTPRINT_VERTICES = 3
    }
}
