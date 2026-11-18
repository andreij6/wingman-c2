package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.AutonomyDecisionRecord
import com.wingmanc2.core.database.record.AutonomyDecisionRecord_
import com.wingmanc2.core.model.AutonomyDecision
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionStore @Inject constructor(private val database: WingmanDatabase) {

    private val box get() = database.decisions

    fun observe(missionId: Long): Flow<List<AutonomyDecision>> =
        database.observe({
            box.query(AutonomyDecisionRecord_.missionId.equal(missionId))
                .order(AutonomyDecisionRecord_.tPlusMs)
                .build()
        }) { records -> records.map(AutonomyDecisionRecord::toModel) }

    suspend fun append(decision: AutonomyDecision): AutonomyDecision =
        database.write { decision.copy(id = box.put(decision.toRecord())) }
}
