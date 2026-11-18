package com.wingmanc2.core.simulation.feed

import com.wingmanc2.core.model.SourceFormat

data class RawFeedMessage(
    val format: SourceFormat,
    val payload: String,
    val sensorKey: String,
    val receivedAtMs: Long,
)
