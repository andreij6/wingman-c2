package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.mapping.Codes
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.MissionRecord
import com.wingmanc2.core.database.record.MissionRecord_
import com.wingmanc2.core.model.MissionPlan
import com.wingmanc2.core.model.MissionStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MissionStore @Inject constructor(private val database: WingmanDatabase) {

    private val box get() = database.missions

    fun observeLatest(): Flow<MissionPlan?> = database.observeChanges(MissionRecord::class.java) {
        box.query().orderDesc(MissionRecord_.startedAtMs).build().use { it.findFirst() }?.toModel()
    }

    suspend fun get(id: Long): MissionPlan? = database.read { box.get(id)?.toModel() }

    suspend fun save(plan: MissionPlan): MissionPlan = database.write { plan.copy(id = box.put(plan.toRecord())) }

    suspend fun updateStatus(id: Long, status: MissionStatus): Boolean = database.write {
        val record = box.get(id) ?: return@write false
        record.status = Codes.missionStatus.encode(status)
        box.put(record)
        true
    }
}
