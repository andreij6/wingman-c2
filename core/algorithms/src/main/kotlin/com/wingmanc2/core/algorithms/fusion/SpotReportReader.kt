package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.nlp.Lexer
import com.wingmanc2.core.algorithms.nlp.Quantity
import com.wingmanc2.core.algorithms.nlp.TokenStream
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.SourceFormat

internal object SpotReportReader {
    private const val BASE_CONFIDENCE = 0.45f
    private const val DEFAULT_OFFSET_M = 400.0
    private const val RELATIVE_UNCERTAINTY_M = 500.0
    private const val PROXIMITY_UNCERTAINTY_M = 300.0
    private const val DIAGONAL = 0.70710678

    private class Heading(val ux: Double, val uy: Double, val label: String)

    private val DIRECTIONS: Map<String, Heading> = buildMap {
        fun bind(heading: Heading, vararg words: String) = words.forEach { put(it, heading) }
        bind(Heading(0.0, 1.0, "N"), "north", "northern", "northward")
        bind(Heading(0.0, -1.0, "S"), "south", "southern", "southward")
        bind(Heading(1.0, 0.0, "E"), "east", "eastern", "eastward")
        bind(Heading(-1.0, 0.0, "W"), "west", "western", "westward")
        bind(Heading(DIAGONAL, DIAGONAL, "NE"), "northeast", "north-east", "ne")
        bind(Heading(-DIAGONAL, DIAGONAL, "NW"), "northwest", "north-west", "nw")
        bind(Heading(DIAGONAL, -DIAGONAL, "SE"), "southeast", "south-east", "se")
        bind(Heading(-DIAGONAL, -DIAGONAL, "SW"), "southwest", "south-west", "sw")
    }

    private val PROXIMITY = setOf("near", "at", "by", "beside", "outside", "vicinity", "around")
    private val RIDGE_KEYS = setOf("RIDGE", "RIDGELINE", "CREST", "HILL", "HILLTOP")

    private val SUBJECTS: Map<String, String> = buildMap {
        fun bind(subject: String, vararg words: String) = words.forEach { put(it, subject) }
        bind("personnel", "personnel", "troops", "people", "men", "team", "squad", "dismounts")
        bind("vehicle", "van", "truck", "technical", "vehicle", "car", "suv", "pickup", "convoy", "apc")
        bind("aircraft", "drone", "uav", "uas", "aircraft", "helicopter", "quadcopter")
        bind("emitter", "dish", "antenna", "antennas", "mast", "radar", "emitter", "jammer", "transmitter", "array")
    }
    private val SUBJECT_PRIORITY = listOf("personnel", "vehicle", "aircraft", "emitter")

    private val AFFILIATIONS: Map<String, Affiliation> = buildMap {
        listOf("hostile", "enemy", "armed", "weapon", "weapons", "rifle", "opfor", "insurgent").forEach { put(it, Affiliation.HOSTILE) }
        listOf("friendly", "blue", "blufor", "ours").forEach { put(it, Affiliation.FRIEND) }
        listOf("civilian", "civilians", "civ", "farmer", "local", "locals").forEach { put(it, Affiliation.NEUTRAL) }
    }

    fun read(text: String, landmarks: Map<String, LocalPoint>): NormalizedObservation {
        val tokens = Lexer.tokenize(text)
        val attributes = HashMap<String, String>()
        attributes["text"] = text.trim().trim('"')
        var fix: Fix? = null
        var ageSeconds: Int? = null
        var affiliation = Affiliation.UNKNOWN

        for (i in tokens.indices) {
            val token = tokens[i]
            AFFILIATIONS[token]?.let { affiliation = it }
            SUBJECTS[token]?.let { subject -> attributes.merge("subject", subject, ::morePrecise) }
            if (token == "ago" && ageSeconds == null) ageSeconds = quantityBefore(tokens, i)?.minutes?.let { (it * 60).toInt() }
            if (token == "now" && ageSeconds == null) ageSeconds = 0
            if (fix != null) continue
            fix = when {
                token in DIRECTIONS && tokens.getOrNull(i + 1) == "of" -> relative(tokens, i, DIRECTIONS.getValue(token), landmarks, attributes)
                token in PROXIMITY -> proximate(tokens, i, landmarks, attributes)
                else -> null
            }
        }

        val missing = buildList {
            if (fix == null) add("location")
            if (ageSeconds == null) add("time")
        }
        ageSeconds?.let { attributes["age_s"] = it.toString() }
        val resolved = fix ?: Localizer.unlocated
        return NormalizedObservation(
            format = SourceFormat.SPOT_TEXT,
            kind = EntityKind.REPORT,
            affiliation = affiliation,
            position = resolved.position,
            uncertaintyM = resolved.uncertaintyM,
            confidence = ConfidenceFusion.penalized(BASE_CONFIDENCE, missing.size),
            ageSeconds = ageSeconds,
            attributes = attributes,
            missingFields = missing,
            descriptor = listOfNotNull("spot report", attributes["subject"], text).joinToString(" "),
        )
    }

    private fun relative(
        tokens: List<String>,
        at: Int,
        heading: Heading,
        landmarks: Map<String, LocalPoint>,
        attributes: MutableMap<String, String>,
    ): Fix? {
        val key = TokenStream(tokens.subList(at + 2, tokens.size)).anchor() ?: return null
        val origin = resolve(key, landmarks) ?: return null
        val distance = quantityBefore(tokens, at)?.meters
        attributes["landmark"] = key
        attributes["direction"] = heading.label
        return Fix(
            Localizer.offset(origin, heading.ux, heading.uy, distance ?: DEFAULT_OFFSET_M),
            distance?.let { it * 0.3 + PROXIMITY_UNCERTAINTY_M / 2 } ?: RELATIVE_UNCERTAINTY_M,
        )
    }

    private fun proximate(tokens: List<String>, at: Int, landmarks: Map<String, LocalPoint>, attributes: MutableMap<String, String>): Fix? {
        val key = TokenStream(tokens.subList(at + 1, tokens.size)).anchor() ?: return null
        val origin = resolve(key, landmarks) ?: return null
        attributes["landmark"] = key
        return Fix(origin, PROXIMITY_UNCERTAINTY_M)
    }

    private fun morePrecise(current: String, candidate: String): String =
        if (SUBJECT_PRIORITY.indexOf(candidate) > SUBJECT_PRIORITY.indexOf(current)) candidate else current

    private fun resolve(key: String, landmarks: Map<String, LocalPoint>): LocalPoint? =
        landmarks[key] ?: Localizer.ridgeReference.takeIf { key in RIDGE_KEYS }

    private fun quantityBefore(tokens: List<String>, at: Int): Quantity? {
        val previous = tokens.getOrNull(at - 1) ?: return null
        Quantity.of(previous)?.let { return it }
        if (!Quantity.isUnitWord(previous)) return null
        return tokens.getOrNull(at - 2)?.let(Quantity::of)?.takeIf { it.isUnitless }?.copy(unit = previous)
    }
}
