package com.wingmanc2.core.database

import android.content.Context
import com.wingmanc2.core.database.record.AutonomyDecisionRecord
import com.wingmanc2.core.database.record.MissionRecord
import com.wingmanc2.core.database.record.MyObjectBox
import com.wingmanc2.core.database.record.OntologyEdgeRecord
import com.wingmanc2.core.database.record.OntologyEntityRecord
import com.wingmanc2.core.database.record.OutboxEventRecord
import com.wingmanc2.core.database.record.RawObservationRecord
import com.wingmanc2.core.database.record.TelemetryRecord
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.flow
import io.objectbox.kotlin.toFlow
import io.objectbox.query.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.Closeable

class WingmanDatabase internal constructor(
    private val store: BoxStore,
    private val io: CoroutineDispatcher,
) : Closeable {

    constructor(context: Context, io: CoroutineDispatcher = Dispatchers.IO) : this(
        store = MyObjectBox.builder().androidContext(context.applicationContext).name(STORE_NAME).build(),
        io = io,
    )

    internal val entities: Box<OntologyEntityRecord> = store.boxFor()
    internal val edges: Box<OntologyEdgeRecord> = store.boxFor()
    internal val observations: Box<RawObservationRecord> = store.boxFor()
    internal val missions: Box<MissionRecord> = store.boxFor()
    internal val decisions: Box<AutonomyDecisionRecord> = store.boxFor()
    internal val telemetry: Box<TelemetryRecord> = store.boxFor()
    internal val outbox: Box<OutboxEventRecord> = store.boxFor()

    internal suspend fun <R> read(block: () -> R): R = withContext(io) { store.callInReadTx(block) }

    internal suspend fun <R> write(block: () -> R): R = withContext(io) { store.callInTx(block) }

    internal fun <T, R> observe(query: Query<T>, transform: (List<T>) -> R): Flow<R> =
        query.flow()
            .map(transform)
            .distinctUntilChanged()
            .flowOn(io)

    internal fun <T, R> observeChanges(box: Box<T>, read: () -> R): Flow<R> =
        store.subscribe(box.entityClass).onlyChanges().toFlow()
            .onStart { emit(box.entityClass) }
            .conflate()
            .map { store.callInReadTx(read) }
            .distinctUntilChanged()
            .flowOn(io)

    override fun close() = store.close()

    private companion object {
        const val STORE_NAME = "wingman-c2"
    }
}
