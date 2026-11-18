package com.wingmanc2.core.database.record

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.IndexType

@Entity
internal class OutboxEventRecord(
    @Id var id: Long = 0,
    var entityType: String = "",
    @Index(type = IndexType.HASH) var entityKey: String = "",
    var op: Int = 0,
    var payloadBytes: Int = 0,
    @Index var enqueuedAtMs: Long = 0,
    var retryCount: Int = 0,
    @Index var status: Int = 0,
    var localVersion: Long = 0,
    var remoteVersion: Long? = null,
    var baseVersion: Long? = null,
)
