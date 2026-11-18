package com.wingmanc2.core.data.ontology

import com.wingmanc2.core.common.ApplicationScope
import com.wingmanc2.core.database.store.EdgeStore
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OntologyStreams @Inject constructor(
    entityStore: EntityStore,
    edgeStore: EdgeStore,
    @ApplicationScope scope: CoroutineScope,
) {
    val entities: SharedFlow<List<OntologyEntity>> =
        entityStore.observeAll().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    val edges: SharedFlow<List<OntologyEdge>> =
        edgeStore.observeAll().shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
