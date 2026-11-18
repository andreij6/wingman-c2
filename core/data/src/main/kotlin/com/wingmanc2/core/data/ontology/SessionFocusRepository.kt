package com.wingmanc2.core.data.ontology

import com.wingmanc2.core.domain.FocusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionFocusRepository @Inject constructor() : FocusRepository {
    private val focused = MutableStateFlow<Long?>(null)

    override val focusedEntityId: StateFlow<Long?> = focused.asStateFlow()

    override fun focus(entityId: Long?) {
        focused.value = entityId
    }

    fun focusIfUnset(entityId: Long) {
        focused.compareAndSet(null, entityId)
    }
}
