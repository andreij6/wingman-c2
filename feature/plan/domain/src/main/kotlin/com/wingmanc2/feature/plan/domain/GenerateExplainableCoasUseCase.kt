package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.MissionPlannerService
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject

class GenerateExplainableCoasUseCase @Inject constructor(
    private val assets: AssetRegistry,
    private val planner: MissionPlannerService,
) {
    suspend operator fun invoke(intent: CommanderIntent, target: OntologyEntity): CoaPlan {
        val threats = assets.threats()
        val asset = assets.primaryAsset()
        val courses = planner.plan(intent, target, asset, threats, assets.noFlyZones())
        return CoaPlan(intent, target, asset, courses, planPicture(target, asset, threats, courses))
    }
}
