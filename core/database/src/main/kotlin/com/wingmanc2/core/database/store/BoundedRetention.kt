package com.wingmanc2.core.database.store

import io.objectbox.Box
import io.objectbox.query.Query
import java.util.concurrent.atomic.AtomicInteger

internal class BoundedRetention(private val capacity: Int, private val checkEvery: Int) {

    private val writesSinceCheck = AtomicInteger()

    fun <T> afterWrite(box: Box<T>, written: Int = 1, oldestFirst: () -> Query<T>) {
        if (writesSinceCheck.addAndGet(written) < checkEvery) return
        writesSinceCheck.set(0)
        oldestFirst().use { query ->
            val excess = query.count() - capacity
            if (excess > 0) box.remove(*query.findIds(0, excess))
        }
    }
}
