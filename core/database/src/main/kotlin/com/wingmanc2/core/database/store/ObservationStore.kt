package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.RawObservationRecord
import com.wingmanc2.core.database.record.RawObservationRecord_
import com.wingmanc2.core.model.RawObservation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservationStore @Inject constructor(private val database: WingmanDatabase) {

    private val box get() = database.observations

    fun observeFor(entityId: Long): Flow<List<RawObservation>> =
        database.observe({
            box.query(RawObservationRecord_.entityId.equal(entityId))
                .orderDesc(RawObservationRecord_.ingestedAtMs)
                .build()
        }) { records -> records.map(RawObservationRecord::toModel) }

    suspend fun putAll(observations: Collection<RawObservation>): List<RawObservation> = database.write {
        val records = observations.map(RawObservation::toRecord)
        box.put(records)
        records.map(RawObservationRecord::toModel)
    }
}
