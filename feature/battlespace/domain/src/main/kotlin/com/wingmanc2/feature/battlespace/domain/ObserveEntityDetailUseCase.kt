package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.SourceFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.roundToInt

class ObserveEntityDetailUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val ontology: OntologyRepository,
    private val assets: AssetRegistry,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<EntityDetail?> = focus.focusedEntityId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(ontology.observeEntity(id), ontology.observeLineage(id), primaryAsset(), minuteTicks()) { entity, lineage, asset, now ->
                entity?.toDetail(lineage, asset, now)
            }
        }
    }

    private fun primaryAsset(): Flow<OntologyEntity> = flow { emit(assets.primaryAsset()) }

    private fun minuteTicks(): Flow<Long> = flow {
        while (true) {
            emit(clock.nowMs())
            delay(TICK_MS)
        }
    }

    private fun OntologyEntity.toDetail(lineage: List<RawObservation>, asset: OntologyEntity, nowMs: Long): EntityDetail {
        val lastSeenMs = lineage.maxOfOrNull { it.ingestedAtMs } ?: updatedAtMs
        return EntityDetail(
            id = id,
            key = key,
            kind = kind,
            affiliation = affiliation,
            isAir = isAir,
            position = position,
            subtitle = subtitle,
            confidence = confidence,
            verified = verified,
            frequency = attributes[ATTR_FREQ],
            bearingDeg = attributes[ATTR_BEARING]?.leadingDegrees() ?: bearingFrom(asset),
            lastSeenMinutes = ((nowMs - lastSeenMs) / MINUTE_MS).coerceAtLeast(0),
            sources = lineage.map(::sourceLabel).distinct(),
        )
    }

    private fun OntologyEntity.bearingFrom(asset: OntologyEntity): Int? =
        if (asset.id == id) null else bearing(asset.position, position)

    private fun bearing(from: GeoPoint, to: GeoPoint): Int {
        val a = OperationalArea.projection.toLocal(from)
        val b = OperationalArea.projection.toLocal(to)
        val degrees = Math.toDegrees(atan2(b.x - a.x, b.y - a.y))
        return ((degrees + FULL_CIRCLE).roundToInt()) % FULL_CIRCLE.toInt()
    }

    private fun String.leadingDegrees(): Int? = takeWhile(Char::isDigit).toIntOrNull()

    private fun sourceLabel(observation: RawObservation): String {
        val base = when (observation.format) {
            SourceFormat.RF_PING -> "RF ping"
            SourceFormat.SPOT_TEXT -> observation.note.substringAfterLast(" · ", "").takeIf(EntityKeys::looksLikeKey) ?: "Spot report"
            SourceFormat.AIS_JSON -> "AIS"
            SourceFormat.ADSB_JSON -> "ADS-B"
        }
        return if (observation.payload.contains(":null")) "$base (partial)" else base
    }

    private companion object {
        const val TICK_MS = 15_000L
        const val MINUTE_MS = 60_000L
        const val FULL_CIRCLE = 360.0
        const val ATTR_FREQ = "Freq"
        const val ATTR_BEARING = "Bearing"
    }
}
