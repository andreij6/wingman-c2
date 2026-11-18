package com.wingmanc2.feature.sync.domain

import com.wingmanc2.core.domain.LinkStateRepository
import com.wingmanc2.core.domain.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveSyncDashboardUseCase @Inject constructor(
    private val links: LinkStateRepository,
    private val sync: SyncRepository,
) {
    operator fun invoke(): Flow<SyncDashboard> =
        combine(links.linkState, sync.observeOutbox(), sync.observeEdge(), sync.observeCloud(), SyncDashboard::of)
}
