package com.wingmanc2.core.ui.format

import com.wingmanc2.core.model.IntentConstraint
import com.wingmanc2.core.model.QueryPredicate

private const val METERS_PER_KM = 1_000

val IntentConstraint.label: String
    get() = when (this) {
        is IntentConstraint.StandoffMin -> "Standoff ≥ ${meters.toInt()} m"
        IntentConstraint.AvoidRadar -> "Avoid radar"
        is IntentConstraint.ReserveMin -> "Reserve ≥ $percent%"
        is IntentConstraint.MaxAltitude -> "Max ${metersAgl.toInt()} m AGL"
        IntentConstraint.ZeroRf -> "Zero RF"
    }

val QueryPredicate.label: String
    get() = when (this) {
        is QueryPredicate.KindIs -> "kind:${kind.name}"
        is QueryPredicate.AffiliationIs -> "affil:${affiliation.name.lowercase()}"
        is QueryPredicate.VerifiedIs -> if (verified) "status:verified" else "status:unverified"
        is QueryPredicate.Near -> {
            val meters = radiusM.toInt()
            if (meters >= METERS_PER_KM && meters % METERS_PER_KM == 0) "near:$anchorKey ≤${meters / METERS_PER_KM}km" else "near:$anchorKey ≤${meters}m"
        }
        is QueryPredicate.SeenWithin -> "seen:≤${minutes}m"
        is QueryPredicate.Text -> "text:$term"
    }
