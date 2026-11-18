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
            bearing == null -> Fix(sensor, Localizer.UNLOCATED_THRESHOLD_M)
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
            bearing = fix.bearing,
            frequencyGhz = frequencyGhz ?: Double.NaN,
        )
    }

    private fun rangeFromRssi(rssiDbm: Double): Double =
        1_000.0 * 10.0.pow((RSSI_AT_1KM_DBM - rssiDbm) / (10.0 * PATH_LOSS_EXPONENT))
}
