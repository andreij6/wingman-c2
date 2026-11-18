package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.TelemetryStreamSource
import com.wingmanc2.core.model.LocalPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class ObserveLiveAssetUseCase @Inject constructor(
    private val telemetry: TelemetryStreamSource,
    private val assets: AssetRegistry,
) {
    operator fun invoke(): Flow<LiveAsset> {
        val trail = telemetry.trail()
            .map { frames -> frames.map { OperationalArea.projection.toLocal(it.position) } }
            .onStart { emit(emptyList<LocalPoint>()) }
        return combine(flow { emit(assets.primaryAsset()) }, telemetry.telemetry(), trail) { asset, frame, points ->
            LiveAsset(
                entityId = asset.id,
                key = asset.key,
                position = OperationalArea.projection.toLocal(frame.position),
                headingDeg = frame.headingDeg,
                altitudeAglM = frame.altitudeAglM,
                trail = points,
            )
        }
    }
}
