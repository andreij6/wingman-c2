package com.wingmanc2.core.data.link

import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.database.store.OutboxStore
import com.wingmanc2.core.domain.LinkStateRepository
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.simulation.cloud.SimulatedCloudMirror
import com.wingmanc2.core.simulation.link.LinkSimulator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SimulatedLinkStateRepository @Inject constructor(
    private val link: LinkSimulator,
    outbox: OutboxStore,
    mirror: SimulatedCloudMirror,
    @ApplicationScope scope: CoroutineScope,
    @Dispatcher(WingmanDispatcher.Default) default: CoroutineDispatcher,
) : LinkStateRepository {

    override val linkState: StateFlow<LinkState> = combine(
        link.quality,
        outbox.observeCounts().map { it.unsynced }.distinctUntilChanged(),
        mirror.lastAckMs,
    ) { quality, queued, lastAckMs -> LinkState(quality.mode, quality.rttMs, quality.lossPct, queued, lastAckMs) }
        .distinctUntilChanged()
        .flowOn(default)
        .stateIn(scope, SharingStarted.Eagerly, link.quality.value.let { LinkState(it.mode, it.rttMs, it.lossPct, 0, null) })

    override fun setMode(mode: LinkMode) = link.setMode(mode)
}
