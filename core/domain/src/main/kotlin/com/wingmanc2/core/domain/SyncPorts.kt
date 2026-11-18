package com.wingmanc2.core.domain

import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.StoreSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LinkStateRepository {
    val linkState: StateFlow<LinkState>
    fun setMode(mode: LinkMode)
}

interface SyncRepository {
    fun observeOutbox(): Flow<List<OutboxEvent>>
    fun observeEdge(): Flow<StoreSnapshot>
    fun observeCloud(): Flow<StoreSnapshot>
    suspend fun resolve(eventId: Long, resolution: ConflictResolution)
}
