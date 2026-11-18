package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.algorithms.nlp.Lexer
import com.wingmanc2.core.algorithms.nlp.Quantity
import com.wingmanc2.core.algorithms.nlp.TokenStream
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.SourceFormat
import kotlin.math.PI
import kotlin.math.sin

internal object SpotReportReader {
    private const val BASE_CONFIDENCE = 0.45f
    private const val DEFAULT_OFFSET_M = 400.0
    private const val RELATIVE_UNCERTAINTY_M = 500.0
    private const val PROXIMITY_UNCERTAINTY_M = 300.0
    private const val DIAGONAL = 0.70710678
    private val COMPASS_HALF_SECTOR = sin(PI / 8)

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
    private val SELF_KEYS = setOf("OUR", "MY", "ME", "US")
    private val POSITION_WORDS = listOf("pos", "position", "location", "loc")
    private val EXPLICIT_FIX = Regex(
        """\b(?:vic|grid|pos|loc)\b[\s:]*([-+]?\d[\d.°'′"″]*\s*[NSns]?)\s*[,\s]\s*([-+]?\d[\d.°'′"″]*\s*[EWew]?)""",
        RegexOption.IGNORE_CASE,
    )

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

    fun read(text: String, observerKey: String?, landmarks: Map<String, LocalPoint>): NormalizedObservation {
        val tokens = Lexer.tokenize(text)
        val attributes = HashMap<String, String>()
        attributes["text"] = text.trim().trim('"')
        val anchors = Anchors(observerKey?.let { landmarks[EntityKeys.normalize(it)] }, landmarks)
        var fix: Fix? = explicitFix(text)
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
                token in DIRECTIONS && tokens.getOrNull(i + 1) == "of" -> relative(tokens, i, DIRECTIONS.getValue(token), anchors, attributes)
                token in PROXIMITY -> proximate(tokens, i, anchors, attributes)
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

    private class Anchors(private val observer: LocalPoint?, private val landmarks: Map<String, LocalPoint>) {
        fun resolve(key: String): LocalPoint? = if (key in SELF_KEYS) observer else landmarks[key]
    }

    private fun explicitFix(text: String): Fix? {
        val match = EXPLICIT_FIX.find(text) ?: return null
        val lat = coordinate(match.groupValues[1].uppercase())?.takeIf { it in -90.0..90.0 } ?: return null
        val lon = coordinate(match.groupValues[2].uppercase())?.takeIf { it in -180.0..180.0 } ?: return null
        return Localizer.geo(lat, lon)
    }

    private fun relative(
        tokens: List<String>,
        at: Int,
        heading: Heading,
        anchors: Anchors,
        attributes: MutableMap<String, String>,
    ): Fix? {
        val stream = TokenStream(tokens.subList(at + 2, tokens.size))
        val key = stream.anchor() ?: return null
        attributes["direction"] = heading.label
        if (key in RIDGE_KEYS) return ridgeSide(key, attributes)
        val origin = anchors.resolve(key) ?: return null
        attributes["landmark"] = key
        for (word in POSITION_WORDS) if (stream.skip(word)) break
        val distance = (quantityBefore(tokens, at) ?: stream.quantity()?.takeUnless { it.isUnitless })?.meters
        return Fix(
            Localizer.offset(origin, heading.ux, heading.uy, distance ?: DEFAULT_OFFSET_M),
            distance?.let { it * COMPASS_HALF_SECTOR + PROXIMITY_UNCERTAINTY_M / 2 } ?: RELATIVE_UNCERTAINTY_M,
        )
    }

    private fun proximate(tokens: List<String>, at: Int, anchors: Anchors, attributes: MutableMap<String, String>): Fix? {
        val key = TokenStream(tokens.subList(at + 1, tokens.size)).anchor() ?: return null
        if (key in RIDGE_KEYS) return ridgeSide(key, attributes)
        val origin = anchors.resolve(key) ?: return null
        attributes["landmark"] = key
        return Fix(origin, PROXIMITY_UNCERTAINTY_M)
    }

    private fun ridgeSide(key: String, attributes: MutableMap<String, String>): Fix {
        attributes["landmark"] = key
        return Localizer.unlocated
    }

    private fun morePrecise(current: String, candidate: String): String =
        if (SUBJECT_PRIORITY.indexOf(candidate) > SUBJECT_PRIORITY.indexOf(current)) candidate else current

    private fun quantityBefore(tokens: List<String>, at: Int): Quantity? {
        val previous = tokens.getOrNull(at - 1) ?: return null
        Quantity.of(previous)?.let { return it }
        if (!Quantity.isUnitWord(previous)) return null
        return tokens.getOrNull(at - 2)?.let(Quantity::of)?.takeIf { it.isUnitless }?.copy(unit = previous)
    }
}
