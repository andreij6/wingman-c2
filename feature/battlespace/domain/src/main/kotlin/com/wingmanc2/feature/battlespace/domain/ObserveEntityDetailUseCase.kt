package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.common.Clock
import com.wingmanc2.core.common.ticks
import com.wingmanc2.core.domain.AssetRegistry
import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RawObservation
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.model.isAir
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import kotlin.math.roundToInt

class ObserveEntityDetailUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val ontology: OntologyRepository,
    private val assets: AssetRegistry,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<EntityDetail?> = focus.focusedEntityId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(ontology.observeEntity(id), ontology.observeLineage(id), primaryAsset(), clock.ticks(TICK_MS)) { entity, lineage, asset, now ->
                entity?.toDetail(lineage, asset, now)
            }
        }
    }

    private fun primaryAsset(): Flow<OntologyEntity> = flow { emit(assets.primaryAsset()) }

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
            frequency = attributes[EntityAttributes.FREQ],
            bearingDeg = attributes[EntityAttributes.BEARING]?.leadingDegrees() ?: bearingFrom(asset),
            lastSeenMinutes = ((nowMs - lastSeenMs) / MINUTE_MS).coerceAtLeast(0),
            sources = lineage.map(::sourceLabel).distinct(),
        )
    }

    private fun OntologyEntity.bearingFrom(asset: OntologyEntity): Int? =
        if (asset.id == id) null else OperationalArea.projection.bearingDeg(asset.position, position).roundToInt().mod(FULL_CIRCLE)

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
        const val FULL_CIRCLE = 360
    }
}
