package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateExplainableCoasUseCase @Inject constructor(
    private val assets: AssetRegistry,
    private val planner: MissionPlannerService,
) {
    private val cache = object : LinkedHashMap<PlanKey, CoaPlan>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlanKey, CoaPlan>?) = size > CACHE_SIZE
    }

    suspend operator fun invoke(intent: CommanderIntent, target: OntologyEntity): CoaPlan {
        val threats = assets.threats()
        val noFly = assets.noFlyZones()
        val key = PlanKey(intent, target.key, target.position, (threats + noFly).map { Hazard(it.key, it.position, it.threatRadiusM) })
        synchronized(cache) { cache[key] }?.let { return it }
        val asset = assets.primaryAsset()
        val plan = CoaPlan(intent, target, asset, threats, planner.plan(intent, target, asset, threats, noFly))
        synchronized(cache) { cache[key] = plan }
        return plan
    }

    private data class Hazard(val key: String, val position: GeoPoint, val radiusM: Double)

    private data class PlanKey(
        val intent: CommanderIntent,
        val targetKey: String,
        val targetPosition: GeoPoint,
        val hazards: List<Hazard>,
    )

    private companion object {
        const val CACHE_SIZE = 8
    }
}
