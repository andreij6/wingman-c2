package com.wingmanc2.core.simulation.uas

import com.wingmanc2.core.algorithms.geo.FlightModel

internal enum class FlightPhase(val speedMps: Double, val watts: Double, val navigating: Boolean) {
    EN_ROUTE(FlightModel.CRUISE_MPS, FlightModel.CRUISE_W, navigating = true),
    ORBITING(FlightModel.LOITER_MPS, FlightModel.LOITER_W, navigating = true),
    RETURNING(FlightModel.CRUISE_MPS, FlightModel.CRUISE_W, navigating = true),
    HOLDING(FlightModel.LOITER_MPS, FlightModel.LOITER_W, navigating = false),
    LOITERING(FlightModel.LOITER_MPS, FlightModel.LOITER_W, navigating = false),
    HOME(FlightModel.LOITER_MPS, FlightModel.LOITER_W, navigating = false),
}
