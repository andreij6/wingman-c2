package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.SourceFormat

internal object TransponderReader {
    private const val FEET_TO_M = 0.3048
    private const val KNOTS_TO_MPS = 0.514444

    private class Profile(
        val format: SourceFormat,
        val baseConfidence: Float,
        val platform: String,
        val identityField: String,
        val identityAliases: Array<String>,
        val nameAliases: Array<String>,
    )

    private val AIS = Profile(
        format = SourceFormat.AIS_JSON,
        baseConfidence = 0.75f,
        platform = "vessel",
        identityField = "mmsi",
        identityAliases = arrayOf("mmsi", "imo"),
        nameAliases = arrayOf("name", "shipname", "vessel", "callsign"),
    )

    private val ADSB = Profile(
        format = SourceFormat.ADSB_JSON,
        baseConfidence = 0.85f,
        platform = "aircraft",
        identityField = "icao",
        identityAliases = arrayOf("icao", "icao24", "hex", "addr"),
        nameAliases = arrayOf("callsign", "flight", "cs", "reg"),
    )

    fun readAis(payload: String) = read(payload, AIS)

    fun readAdsb(payload: String) = read(payload, ADSB)

    private fun read(payload: String, profile: Profile): NormalizedObservation {
        val fields = FlatJsonScanner.scan(payload)
        val missing = ArrayList<String>(4)
        val normalized = HashMap<String, String>()

        val identity = fields.field(*profile.identityAliases)
        val name = fields.field(*profile.nameAliases)?.trim()
        val altitudeM = fields.field("alt_m", "altitude_m")?.let(::leadingNumber)
            ?: fields.field("alt_ft", "alt_baro", "alt_geom", "altitude", "alt")?.let(::leadingNumber)?.times(FEET_TO_M)
        val speedMps = fields.field("speed_mps")?.let(::leadingNumber)
            ?: fields.field("sog", "gs", "speed", "spd")?.let(::leadingNumber)?.times(KNOTS_TO_MPS)
        val heading = fields.field("cog", "track", "heading", "hdg", "trk")?.let(::leadingNumber)?.mod(360.0)
        val fix = Localizer.explicit(fields)

        if (identity == null) missing += profile.identityField else normalized[profile.identityField] = identity
        if (fix == null) missing += "position"
        if (altitudeM == null) missing += "alt_m" else normalized["alt_m"] = altitudeM.toInt().toString()
        name?.let { normalized["name"] = it }
        speedMps?.let { normalized["speed_mps"] = it.toInt().toString() }
        heading?.let { normalized["heading_deg"] = it.toInt().toString() }

        val resolved = fix ?: Localizer.unlocated
        return NormalizedObservation(
            format = profile.format,
            kind = EntityKind.TRACK,
            affiliation = if (identity != null) Affiliation.NEUTRAL else Affiliation.UNKNOWN,
            position = resolved.position,
            uncertaintyM = resolved.uncertaintyM,
            confidence = ConfidenceFusion.penalized(profile.baseConfidence, missing.size),
            ageSeconds = fields.field("age_s", "seen", "age")?.let(::leadingNumber)?.toInt(),
            attributes = fields + normalized,
            missingFields = missing,
            descriptor = listOfNotNull("track", profile.platform, name, identity).joinToString(" "),
        )
    }
}
