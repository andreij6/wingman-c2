package com.wingmanc2.core.algorithms.geo

object FlightModel {
    const val CRUISE_MPS = 22.0
    const val DASH_MPS = 30.0
    const val LOITER_MPS = 16.0
    const val CLIMB_MPS = 3.0
    const val MAX_TURN_RATE_DPS = 25.0
    const val BATTERY_WH = 160.0
    const val CRUISE_W = 210.0
    const val DASH_W = 330.0
    const val LOITER_W = 150.0
    const val CLIMB_W_PER_MPS = 95.0
    const val MASKED_ALT_AGL_M = 120.0
    const val TRANSIT_ALT_AGL_M = 380.0
    const val START_BATTERY_PCT = 88.0
    const val RTB_FLOOR_PCT = 30.0

    fun drainPctPerSecond(watts: Double) = watts / (BATTERY_WH * 3_600.0) * 100.0
}
