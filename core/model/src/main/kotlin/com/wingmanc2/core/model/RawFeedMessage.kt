package com.wingmanc2.core.model

data class RawFeedMessage(
    val format: SourceFormat,
    val payload: String,
    val sensorKey: String,
    val receivedAtMs: Long,
)
