package com.wingmanc2.feature.battlespace.domain

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.domain.FocusRepository
import com.wingmanc2.core.domain.OntologyRepository
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveBattlespaceUseCase @Inject constructor(
    private val ontology: OntologyRepository,
    private val focus: FocusRepository,
) {
    operator fun invoke(filter: Flow<AffiliationFilter>, nova: Flow<NovaResult?>): Flow<BattlespacePicture> =
        combine(ontology.observeEntities().map(::place), filter, nova, focus.focusedEntityId) { placed, selectedFilter, result, selectedId ->
            BattlespacePicture(
                tracks = placed.tracks.filter { it.matches(selectedFilter, result) },
                zones = placed.zones,
                counts = placed.counts,
                filter = selectedFilter,
                selectedId = selectedId,
                novaActive = result != null,
            )
        }

    private fun PlacedEntity.matches(filter: AffiliationFilter, nova: NovaResult?): Boolean =
        (filter.affiliation == null || entity.affiliation == filter.affiliation) && (nova == null || entity.id in nova.matchIds)

    private fun place(entities: List<OntologyEntity>): Placed {
        val tracks = ArrayList<PlacedEntity>(entities.size)
        val zones = ArrayList<PlacedEntity>()
        var hostile = 0
        var unknown = 0
        var friendly = 0
        for (entity in entities) {
            when (entity.kind) {
                EntityKind.CLASS -> Unit
                EntityKind.ZONE -> zones += entity.placed()
                else -> {
                    tracks += entity.placed()
                    when (entity.affiliation) {
                        Affiliation.HOSTILE -> hostile++
                        Affiliation.UNKNOWN -> unknown++
                        Affiliation.FRIEND -> friendly++
                        Affiliation.NEUTRAL -> Unit
                    }
                }
            }
        }
        return Placed(tracks, zones, TrackCounts(tracks.size, hostile, unknown, friendly))
    }

    private class Placed(val tracks: List<PlacedEntity>, val zones: List<PlacedEntity>, val counts: TrackCounts)
}

internal fun OntologyEntity.placed(): PlacedEntity = PlacedEntity(
    entity = this,
    position = OperationalArea.projection.toLocal(position),
    footprint = footprint.map(OperationalArea.projection::toLocal),
    isAir = isAir,
)

internal val OntologyEntity.isAir: Boolean
    get() = attributes[ATTR_DOMAIN].equals(DOMAIN_AIR, ignoreCase = true)

private const val ATTR_DOMAIN = "Domain"
private const val DOMAIN_AIR = "Air"
