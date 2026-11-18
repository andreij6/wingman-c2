package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.domain.FocusRepository
import javax.inject.Inject

class SelectEntityUseCase @Inject constructor(private val focus: FocusRepository) {
    operator fun invoke(entityId: Long?) = focus.focus(entityId)
}
