package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.algorithms.nlp.EntityKeys
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.SourceFormat
import java.util.Locale
import kotlin.math.pow

internal object RfPingReader {
    private const val BASE_CONFIDENCE = 0.7f
    private const val RSSI_AT_1KM_DBM = -60.0
    private const val PATH_LOSS_EXPONENT = 2.5
    private val RADAR_BANDS_GHZ = 2.0..12.0

    fun read(payload: String, landmarks: Map<String, LocalPoint>): NormalizedObservation {
        val fields = FlatJsonScanner.scan(payload)
        val missing = ArrayList<String>(3)
        val normalized = HashMap<String, String>()

        val sensorKey = fields.field("src", "sensor", "rx", "source", "station")?.let(EntityKeys::normalize)
        val frequencyGhz = fields.field("band", "freq", "frequency", "freq_ghz", "f")?.let(::frequencyGhz)
        val bearing = fields.field("az", "azimuth", "bearing", "brg", "aoa")?.let(::leadingNumber)?.mod(360.0)
        val range = fields.field("range_m", "range", "rng", "dist", "distance_m")?.let(::leadingNumber)
            ?: fields.field("rssi", "dbm", "rssi_dbm")?.let(::leadingNumber)?.let(::rangeFromRssi)

        if (sensorKey == null) missing += "src" else normalized["sensor"] = sensorKey
        if (frequencyGhz == null) missing += "band" else {
            normalized["freq_ghz"] = "%.2f".format(Locale.ROOT, frequencyGhz)
            normalized["band"] = bandLetter(frequencyGhz)
        }
        if (bearing == null) missing += "az" else normalized["az_deg"] = "%.0f".format(Locale.ROOT, bearing)

        val sensor = sensorKey?.let(landmarks::get)
        val fix = Localizer.explicit(fields) ?: when {
            sensor == null -> Localizer.unlocated
            bearing == null -> Fix(sensor, Localizer.unlocated.uncertaintyM / 2)
            else -> Localizer.alongBearing(sensor, bearing, range)
        }
        if (sensorKey != null && sensor == null) missing += "sensor_position"

        val band = normalized["band"]
        return NormalizedObservation(
            format = SourceFormat.RF_PING,
            kind = EntityKind.EMITTER,
            affiliation = if (frequencyGhz != null && frequencyGhz in RADAR_BANDS_GHZ) Affiliation.HOSTILE else Affiliation.UNKNOWN,
            position = fix.position,
            uncertaintyM = fix.uncertaintyM,
            confidence = ConfidenceFusion.penalized(BASE_CONFIDENCE, missing.size),
            ageSeconds = fields.field("age_s", "age")?.let(::leadingNumber)?.toInt(),
            attributes = fields + normalized,
            missingFields = missing,
            descriptor = listOfNotNull("rf emitter radar", band?.let { "$it-band" }, sensorKey).joinToString(" "),
        )
    }

    private fun frequencyGhz(raw: String): Double? {
        val value = leadingNumber(raw) ?: return bandCenterGhz(raw.trim().uppercase())
        val unit = raw.lowercase()
        return when {
            "mhz" in unit || (unit.endsWith("m") && value > 100) -> value / 1_000.0
            "khz" in unit -> value / 1_000_000.0
            value > 1_000 -> value / 1_000.0
            else -> value
        }
    }

    private fun bandCenterGhz(label: String): Double? = when (label.removeSuffix("-BAND").removeSuffix(" BAND")) {
        "L" -> 1.5
        "S" -> 3.0
        "C" -> 6.0
        "X" -> 10.0
        "KU" -> 15.0
        else -> null
    }

    private fun bandLetter(ghz: Double): String = when {
        ghz < 1.0 -> "UHF"
        ghz < 2.0 -> "L"
        ghz < 4.0 -> "S"
        ghz < 8.0 -> "C"
        ghz < 12.0 -> "X"
        ghz < 18.0 -> "Ku"
        else -> "Ka"
    }

    private fun rangeFromRssi(rssiDbm: Double): Double =
        1_000.0 * 10.0.pow((RSSI_AT_1KM_DBM - rssiDbm) / (10.0 * PATH_LOSS_EXPONENT))
}
