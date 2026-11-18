package com.wingmanc2.core.algorithms.nlp

import com.wingmanc2.core.model.CommanderIntent
import com.wingmanc2.core.model.IntentConstraint
import com.wingmanc2.core.model.IntentConstraint.AvoidRadar
import com.wingmanc2.core.model.IntentConstraint.MaxAltitude
import com.wingmanc2.core.model.IntentConstraint.ReserveMin
import com.wingmanc2.core.model.IntentConstraint.StandoffMin
import com.wingmanc2.core.model.IntentConstraint.ZeroRf
import javax.inject.Inject
import kotlin.math.roundToInt

class IntentParser @Inject constructor() {

    fun parse(text: String): CommanderIntent {
        val tokens = Lexer.tokenize(text)
        val quantities = measure(tokens)
        val claimed = BooleanArray(tokens.size)
        val constraints = ArrayList<IntentConstraint>(Slot.entries.size)
        val filled = BooleanArray(Slot.entries.size)
        var targetKey: String? = null

        for (i in tokens.indices) {
            val token = tokens[i]
            if (targetKey == null && EntityKeys.looksLikeKey(token)) targetKey = EntityKeys.normalize(token)
            val slot = slotOf(tokens, i) ?: continue
            if (filled[slot.ordinal]) continue
            val constraint = when (slot) {
                Slot.AVOID_RADAR -> AvoidRadar
                Slot.ZERO_RF -> ZeroRf
                else -> bind(slot, i, quantities, claimed) ?: continue
            }
            filled[slot.ordinal] = true
            constraints += constraint
        }
        return CommanderIntent(text.trim(), targetKey, constraints)
    }

    private fun measure(tokens: List<String>): Array<Quantity?> {
        val quantities = arrayOfNulls<Quantity>(tokens.size)
        for (i in tokens.indices) {
            val quantity = Quantity.of(tokens[i]) ?: continue
            val unit = tokens.getOrNull(i + 1)
            quantities[i] = if (quantity.isUnitless && unit != null && Quantity.isUnitWord(unit)) quantity.copy(unit = unit) else quantity
        }
        return quantities
    }

    private fun slotOf(tokens: List<String>, i: Int): Slot? {
        val token = tokens[i]
        return when {
            token in STANDOFF_WORDS -> Slot.STANDOFF
            token in RESERVE_WORDS -> Slot.RESERVE
            token in ALTITUDE_WORDS -> Slot.ALTITUDE
            token in MASKING_WORDS -> Slot.AVOID_RADAR
            token in SILENCE_WORDS -> Slot.ZERO_RF
            token == "avoid" && followedBy(tokens, i, RADAR_WORDS, AVOID_WINDOW) -> Slot.AVOID_RADAR
            token in NEGATIONS && followedBy(tokens, i, EMISSION_WORDS, NEGATION_WINDOW) -> Slot.ZERO_RF
            else -> null
        }
    }

    private fun followedBy(tokens: List<String>, i: Int, words: Set<String>, window: Int): Boolean {
        val end = minOf(tokens.lastIndex, i + window)
        for (j in i + 1..end) if (tokens[j] in words) return true
        return false
    }

    private fun bind(slot: Slot, at: Int, quantities: Array<Quantity?>, claimed: BooleanArray): IntentConstraint? {
        for (distance in 1..BIND_WINDOW) {
            claim(slot, at - distance, quantities, claimed)?.let { return it }
            claim(slot, at + distance, quantities, claimed)?.let { return it }
        }
        return null
    }

    private fun claim(slot: Slot, j: Int, quantities: Array<Quantity?>, claimed: BooleanArray): IntentConstraint? {
        if (j !in quantities.indices || claimed[j]) return null
        val constraint = quantities[j]?.let { constraintFor(slot, it) } ?: return null
        claimed[j] = true
        return constraint
    }

    private fun constraintFor(slot: Slot, quantity: Quantity): IntentConstraint? = when (slot) {
        Slot.STANDOFF -> quantity.meters?.let(::StandoffMin)
        Slot.ALTITUDE -> quantity.meters?.let(::MaxAltitude)
        Slot.RESERVE -> quantity.percent?.let { ReserveMin(it.roundToInt()) }
        Slot.AVOID_RADAR, Slot.ZERO_RF -> null
    }

    private enum class Slot { STANDOFF, AVOID_RADAR, RESERVE, ALTITUDE, ZERO_RF }

    private companion object {
        const val BIND_WINDOW = 4
        const val AVOID_WINDOW = 4
        const val NEGATION_WINDOW = 2

        val STANDOFF_WORDS = setOf("standoff", "stand-off", "standoffs", "offset", "clearance", "away", "buffer")
        val RESERVE_WORDS = setOf("reserve", "reserves", "battery", "batt", "charge", "bingo", "power", "energy")
        val ALTITUDE_WORDS = setOf("below", "under", "beneath", "ceiling", "altitude", "alt", "agl", "max", "maximum", "higher")
        val MASKING_WORDS = setOf("masked", "mask", "terrain-masked", "undetected", "stealth", "stealthy", "covert", "unseen", "hidden", "low-observable")
        val SILENCE_WORDS = setOf("silent", "silence", "emcon", "passive", "passively", "receive-only", "radio-silent", "zero-rf")
        val RADAR_WORDS = setOf("radar", "radars", "detection", "emitter", "emitters", "sam", "sams", "air-defense", "threat", "threats", "lobe", "s-band", "coverage")
        val NEGATIONS = setOf("no", "zero", "without", "don", "never", "minimal")
        val EMISSION_WORDS = setOf("rf", "emission", "emissions", "emit", "emitting", "transmit", "transmitting", "transmission", "transmissions", "radio", "radiating", "radiate")
    }
}
