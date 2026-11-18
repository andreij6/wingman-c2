package com.wingmanc2.feature.sync.domain

import com.wingmanc2.core.domain.LinkStateRepository
import com.wingmanc2.core.domain.ScenarioController
import com.wingmanc2.core.domain.SyncRepository
import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.ScenarioInject
import javax.inject.Inject

class SetLinkModeUseCase @Inject constructor(private val links: LinkStateRepository) {
    operator fun invoke(mode: LinkMode) {
        if (links.linkState.value.mode != mode) links.setMode(mode)
    }
}

class ResolveConflictUseCase @Inject constructor(private val sync: SyncRepository) {
    suspend operator fun invoke(eventId: Long, resolution: ConflictResolution) = sync.resolve(eventId, resolution)
}

class InjectScenarioUseCase @Inject constructor(private val scenario: ScenarioController) {
    suspend operator fun invoke(inject: ScenarioInject) = scenario.inject(inject)
}
