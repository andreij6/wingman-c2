package com.wingmanc2.core.database.store

import com.wingmanc2.core.database.WingmanDatabase
import com.wingmanc2.core.database.mapping.toModel
import com.wingmanc2.core.database.mapping.toRecord
import com.wingmanc2.core.database.record.TelemetryRecord
import com.wingmanc2.core.database.record.TelemetryRecord_
import com.wingmanc2.core.model.TelemetryFrame
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryStore @Inject constructor(private val database: WingmanDatabase) {

    private val box get() = database.telemetry
    private val retention = BoundedRetention(capacity = RETAINED_FRAMES, checkEvery = RETENTION_CHECK)

    suspend fun trail(missionId: Long, limit: Int): List<TelemetryFrame> = database.read {
        box.query(TelemetryRecord_.missionId.equal(missionId))
            .orderDesc(TelemetryRecord_.id)
            .build()
            .use { it.find(0, limit.toLong()) }
            .asReversed()
            .map(TelemetryRecord::toModel)
    }

    suspend fun append(missionId: Long, frame: TelemetryFrame) = database.write {
        box.put(frame.toRecord(missionId))
        retention.afterWrite(box) { box.query().build() }
    }

    private companion object {
        const val RETAINED_FRAMES = 3_000
        const val RETENTION_CHECK = 500
    }
}
