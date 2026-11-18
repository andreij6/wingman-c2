package com.wingmanc2.core.domain

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.ParsedQuery
import com.wingmanc2.core.model.QueryResult
import com.wingmanc2.core.model.RawObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface OntologyRepository {
    fun observeEntities(): Flow<List<OntologyEntity>>
    fun observeEntity(id: Long): Flow<OntologyEntity?>
    fun observeGraph(focusId: Long): Flow<EntityGraph>
    fun observeLineage(entityId: Long): Flow<List<RawObservation>>
    suspend fun findByKey(key: String): OntologyEntity?
    suspend fun query(query: ParsedQuery): QueryResult
}

interface FocusRepository {
    val focusedEntityId: StateFlow<Long?>
    fun focus(entityId: Long?)
}

interface AssetRegistry {
    suspend fun primaryAsset(): OntologyEntity
    suspend fun threats(): List<OntologyEntity>
    suspend fun noFlyZones(): List<OntologyEntity>
    fun affiliationCounts(): Flow<Map<Affiliation, Int>>
}
