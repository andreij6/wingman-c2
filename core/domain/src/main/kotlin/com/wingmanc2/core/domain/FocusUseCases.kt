package com.wingmanc2.core.domain

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveFocusUseCase @Inject constructor(private val focus: FocusRepository) {
    operator fun invoke(): StateFlow<Long?> = focus.focusedEntityId
}

class FocusEntityUseCase @Inject constructor(private val focus: FocusRepository) {
    operator fun invoke(entityId: Long?) = focus.focus(entityId)
}
