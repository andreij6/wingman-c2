package com.wingmanc2.core.algorithms.sync

import com.wingmanc2.core.model.OutboxEvent
import com.wingmanc2.core.model.OutboxOp
import com.wingmanc2.core.model.OutboxStatus
import javax.inject.Inject

class OutboxCoalescer @Inject constructor() {

    fun coalesce(events: List<OutboxEvent>): List<OutboxEvent> {
        val slots = arrayOfNulls<OutboxEvent>(events.size)
        val openSlot = HashMap<EntityRef, Int>(events.size * 2)
        var used = 0
        for (event in events) {
            val ref = EntityRef(event.entityType, event.entityKey)
            if (event.status != OutboxStatus.PENDING) {
                openSlot.remove(ref)
                slots[used++] = event
                continue
            }
            val slot = openSlot[ref]
            if (slot == null) {
                openSlot[ref] = used
                slots[used++] = event
                continue
            }
            val merged = merge(slots[slot]!!, event)
            slots[slot] = merged
            if (merged == null) openSlot.remove(ref)
        }
        val result = ArrayList<OutboxEvent>(used)
        for (i in 0 until used) slots[i]?.let(result::add)
        return result
    }

    private fun merge(earlier: OutboxEvent, later: OutboxEvent): OutboxEvent? {
        val op = when (later.op) {
            OutboxOp.DELETE -> if (earlier.op == OutboxOp.INSERT) return null else OutboxOp.DELETE
            OutboxOp.INSERT, OutboxOp.UPSERT -> when (earlier.op) {
                OutboxOp.INSERT -> OutboxOp.INSERT
                OutboxOp.UPSERT, OutboxOp.DELETE -> OutboxOp.UPSERT
            }
        }
        return later.copy(op = op, enqueuedAtMs = earlier.enqueuedAtMs, retryCount = maxOf(earlier.retryCount, later.retryCount))
    }

    private data class EntityRef(val type: String, val key: String)
}
