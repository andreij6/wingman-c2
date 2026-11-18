package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.domain.MissionRepository
import com.wingmanc2.core.model.MissionPlan
import javax.inject.Inject

class DispatchWingmanMissionUseCase @Inject constructor(private val missions: MissionRepository) {
    suspend operator fun invoke(plan: CoaPlan, code: String): MissionPlan? {
        val coa = plan.courses.firstOrNull { it.code == code } ?: return null
        return missions.dispatch(plan.intent, plan.target, plan.asset, coa)
    }
}
