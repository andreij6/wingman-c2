package com.wingmanc2.core.simulation.scenario

import com.wingmanc2.core.model.GeoPoint

object ScenarioSeed {
    const val INITIAL_VERSION = 2L
    const val COMMANDER_INTENT = "Recon TRK-07 from 200 m standoff, avoid the ridge radar, keep 30% battery reserve."

    val entities: List<SeedEntity> = SeedEntities.all
    val popupThreat: SeedEntity = SeedEntities.popupThreat

    private val byKey: Map<String, SeedEntity> = (entities + popupThreat).associateBy(SeedEntity::key)
    private val graph = SeedGraph(byKey)

    val edges: List<SeedEdge> = graph.edges
    val observations: List<SeedObservation> = graph.observations
    val home: GeoPoint = entity(ScenarioKeys.UAS_01).position

    fun entity(key: String): SeedEntity = byKey.getValue(key)
}
