package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.OntologyEdgeRecord
import com.wingmanc2.core.database.record.OntologyEdgeRecord_
import com.wingmanc2.core.model.OntologyEdge
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeStore @Inject constructor(private val database: WingmanDatabase) {

    private val box get() = database.edges

    fun observeAll(): Flow<List<OntologyEdge>> =
        database.observe(box.query().order(OntologyEdgeRecord_.id).build()) { records ->
            records.map(OntologyEdgeRecord::toModel)
        }

    fun observeIncident(entityId: Long): Flow<List<OntologyEdge>> =
        database.observe(
            box.query(OntologyEdgeRecord_.sourceId.equal(entityId).or(OntologyEdgeRecord_.targetId.equal(entityId))).build(),
        ) { records -> records.map(OntologyEdgeRecord::toModel) }

    suspend fun incident(entityIds: LongArray): List<OntologyEdge> = database.read {
        box.query(OntologyEdgeRecord_.sourceId.oneOf(entityIds).or(OntologyEdgeRecord_.targetId.oneOf(entityIds)))
            .build()
            .use { it.find() }
            .map(OntologyEdgeRecord::toModel)
    }

    suspend fun putAll(edges: Collection<OntologyEdge>): List<OntologyEdge> = database.write {
        val records = edges.map(OntologyEdge::toRecord)
        box.put(records)
        records.map(OntologyEdgeRecord::toModel)
    }
}
