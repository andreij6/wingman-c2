package com.wingmanc2.feature.intel.domain

import com.wingmanc2.core.domain.FocusRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class FocusEntityUseCase @Inject constructor(private val focus: FocusRepository) {
    val focusedEntityId: StateFlow<Long?> get() = focus.focusedEntityId

    operator fun invoke(entityId: Long?) = focus.focus(entityId)
}
