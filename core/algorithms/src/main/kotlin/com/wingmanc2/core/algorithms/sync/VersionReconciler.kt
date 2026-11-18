package com.wingmanc2.core.algorithms.sync

import com.wingmanc2.core.model.ConflictResolution
import com.wingmanc2.core.model.OutboxEvent
import javax.inject.Inject

sealed interface Reconciliation {
    data object AlreadySynced : Reconciliation
    data class Push(val version: Long) : Reconciliation
    data class Pull(val version: Long) : Reconciliation
    data class Conflict(val base: Long, val local: Long, val remote: Long) : Reconciliation
}

class VersionReconciler @Inject constructor() {

    fun classify(local: Long, remote: Long?, base: Long): Reconciliation = when {
        remote == null -> Reconciliation.Push(local)
        local == remote -> Reconciliation.AlreadySynced
        remote == base -> Reconciliation.Push(local)
        local == base -> Reconciliation.Pull(remote)
        else -> Reconciliation.Conflict(base, local, remote)
    }

    fun classify(event: OutboxEvent, base: Long): Reconciliation = classify(event.localVersion, event.remoteVersion, base)

    fun resolve(conflict: Reconciliation.Conflict, resolution: ConflictResolution): Reconciliation = when (resolution) {
        ConflictResolution.KEEP_EDGE -> Reconciliation.Push(maxOf(conflict.local, conflict.remote) + 1)
        ConflictResolution.TAKE_CLOUD -> Reconciliation.Pull(conflict.remote)
    }
}
