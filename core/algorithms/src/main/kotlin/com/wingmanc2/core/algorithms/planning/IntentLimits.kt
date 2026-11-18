package com.wingmanc2.core.algorithms.planning

import com.wingmanc2.core.algorithms.geo.FlightModel
import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.IntentConstraint

private const val DEFAULT_STANDOFF_M = 200.0

fun CommanderIntent.standoffM(): Double =
    constraints.filterIsInstance<IntentConstraint.StandoffMin>().maxOfOrNull { it.meters } ?: DEFAULT_STANDOFF_M

fun CommanderIntent.reserveFloorPct(): Double =
    constraints.filterIsInstance<IntentConstraint.ReserveMin>().maxOfOrNull { it.percent.toDouble() } ?: FlightModel.RTB_FLOOR_PCT

internal fun CommanderIntent.ceilingAglM(): Double =
    constraints.filterIsInstance<IntentConstraint.MaxAltitude>().minOfOrNull { it.metersAgl } ?: Double.POSITIVE_INFINITY
