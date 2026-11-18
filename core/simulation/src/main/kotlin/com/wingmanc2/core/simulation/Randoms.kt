package com.wingmanc2.core.simulation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

internal fun Random.nextGaussian(): Double = sqrt(-2.0 * ln(1.0 - nextDouble())) * cos(2.0 * PI * nextDouble())

internal fun Random.chance(probability: Double): Boolean = nextDouble() < probability
