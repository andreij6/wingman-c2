package com.wingmanc2.core.data.ontology

import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.common.Dispatcher
import com.wingmanc2.core.common.WingmanDispatcher
import com.wingmanc2.core.database.store.EntityStore
import com.wingmanc2.core.database.store.ObservationStore
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.OntologyEdge
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ParsedQuery
import com.wingmanc2.core.model.QueryResult
import com.wingmanc2.core.model.RawObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineFirstOntologyRepository @Inject constructor(
    private val streams: OntologyStreams,
    private val entityStore: EntityStore,
    private val observationStore: ObservationStore,
    private val queryEngine: OntologyQueryEngine,
    @Dispatcher(WingmanDispatcher.Default) private val default: CoroutineDispatcher,
) : OntologyRepository {

    override fun observeEntities(): Flow<List<OntologyEntity>> = streams.entities

    override fun observeEntity(id: Long): Flow<OntologyEntity?> = entityStore.observe(id)

    override fun observeGraph(focusId: Long): Flow<EntityGraph> =
        combine(streams.entities, streams.edges) { entities, edges -> graphAround(focusId, entities, edges) }
            .distinctUntilChanged()
            .flowOn(default)

    override fun observeLineage(entityId: Long): Flow<List<RawObservation>> = observationStore.observeFor(entityId)

    override suspend fun findByKey(key: String): OntologyEntity? =
        entityStore.findByKey(key) ?: entityStore.findByKey(EntityKeys.normalize(key))

    override suspend fun query(query: ParsedQuery): QueryResult = withContext(default) { queryEngine.run(query) }

    private fun graphAround(focusId: Long, entities: List<OntologyEntity>, edges: List<OntologyEdge>): EntityGraph {
        val members = LinkedHashSet<Long>().apply { add(focusId) }
        for (edge in edges) {
            if (edge.sourceId == focusId) members += edge.targetId
            if (edge.targetId == focusId) members += edge.sourceId
        }
        val nodes = ArrayList<OntologyEntity>(members.size)
        for (entity in entities) if (entity.id in members) nodes += entity
        val graphEdges = edges.filter { it.sourceId in members && it.targetId in members }
        return EntityGraph(focusId, nodes, graphEdges, totalNodes = entities.size, totalEdges = edges.size)
    }
}
