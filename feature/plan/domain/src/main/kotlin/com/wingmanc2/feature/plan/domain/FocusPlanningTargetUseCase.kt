package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.model.OntologyEntity
import javax.inject.Inject

class FocusPlanningTargetUseCase @Inject constructor(private val focus: FocusRepository) {
    operator fun invoke(target: OntologyEntity) = focus.focus(target.id)
}
