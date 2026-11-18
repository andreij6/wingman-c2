package com.wingmanc2.feature.mission.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.domain.TelemetryStreamSource
import com.wingmanc2.core.model.LocalPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveTelemetryUseCase @Inject constructor(private val source: TelemetryStreamSource) {
    operator fun invoke(): Flow<LiveTelemetry> =
        source.telemetry().map { LiveTelemetry(it, OperationalArea.projection.toLocal(it.position)) }
}

class ObserveFlownTrackUseCase @Inject constructor(private val source: TelemetryStreamSource) {
    operator fun invoke(): Flow<List<LocalPoint>> =
        source.trail().map { frames -> frames.map { OperationalArea.projection.toLocal(it.position) } }
}
