package com.wingmanc2.core.simulation.feed

import com.wingmanc2.core.algorithms.geo.OperationalArea
import com.wingmanc2.core.algorithms.geo.Terrain
import com.wingmanc2.core.algorithms.geo.bearingDeg
import com.wingmanc2.core.algorithms.geo.compassIndex
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityAttributes
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.RawFeedMessage
import com.wingmanc2.core.model.SourceFormat
import com.wingmanc2.core.simulation.chance
import com.wingmanc2.core.simulation.feed.CoordinateText.decimal
import com.wingmanc2.core.simulation.feed.CoordinateText.one
import com.wingmanc2.core.simulation.nextGaussian
import com.wingmanc2.core.simulation.scenario.ScenarioKeys
import com.wingmanc2.core.simulation.scenario.ScenarioSeed
import com.wingmanc2.core.simulation.scenario.SeedEntity
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

internal class FeedComposer(private val random: Random) {
    private val projection = OperationalArea.projection

    fun nextGapMs(meanMs: Long): Long = (meanMs * random.nextDouble(0.4, 1.6)).toLong()

    fun next(receivedAtMs: Long): RawFeedMessage {
        val subject = SUBJECTS[random.nextInt(SUBJECTS.size)]
        val x = subject.local.x + random.nextGaussian() * POSITION_SIGMA_M
        val y = subject.local.y + random.nextGaussian() * POSITION_SIGMA_M
        return when (formatFor(subject)) {
            SourceFormat.RF_PING -> rfPing(subject, x, y, receivedAtMs)
            SourceFormat.SPOT_TEXT -> spotText(subject, x, y, receivedAtMs)
            SourceFormat.AIS_JSON -> ais(subject, projection.toGeo(x, y), receivedAtMs)
            SourceFormat.ADSB_JSON -> adsb(subject, projection.toGeo(x, y), receivedAtMs)
        }
    }

    private fun formatFor(subject: SeedEntity): SourceFormat {
        val roll = random.nextDouble()
        return when {
            subject.attributes[EntityAttributes.DOMAIN] == EntityAttributes.AIR -> if (roll < 0.8) SourceFormat.ADSB_JSON else SourceFormat.RF_PING
            subject.kind == EntityKind.EMITTER -> if (roll < 0.65) SourceFormat.RF_PING else if (roll < 0.85) SourceFormat.SPOT_TEXT else SourceFormat.AIS_JSON
            else -> if (roll < 0.55) SourceFormat.SPOT_TEXT else SourceFormat.AIS_JSON
        }
    }

    private fun rfPing(subject: SeedEntity, x: Double, y: Double, at: Long): RawFeedMessage {
        val azimuth = (bearingDeg(x - RF_SENSOR.local.x, y - RF_SENSOR.local.y) + random.nextGaussian() * AZIMUTH_SIGMA_DEG).roundToInt().mod(360)
        val payload = buildJsonObject {
            field("src", "\"rf_rx2\"")
            field("band", "\"${bandOf(subject)}\"")
            field("az", if (random.chance(0.2)) String.format(Locale.ROOT, "\"%03dT\"", azimuth) else azimuth.toString())
            if (!random.chance(0.3)) field("snr_db", one(8.0 + random.nextDouble() * 14.0))
        }
        return RawFeedMessage(SourceFormat.RF_PING, payload, ScenarioKeys.RF_RX2, at)
    }

    private fun spotText(subject: SeedEntity, x: Double, y: Double, at: Long): RawFeedMessage {
        val observer = OBSERVERS[random.nextInt(OBSERVERS.size)]
        val geo = projection.toGeo(x, y)
        val distance = (hypot(x - observer.local.x, y - observer.local.y) / 50.0).roundToInt() * 50
        val direction = COMPASS[compassIndex(bearingDeg(x - observer.local.x, y - observer.local.y))]
        val what = subject.subtitle.substringBefore(" · ").lowercase()
        val payload = when (random.nextInt(3)) {
            0 -> "Saw $what approx $distance m $direction of ${observer.key}, vic ${decimal(geo.lat)},${decimal(geo.lon)}"
            1 -> "${observer.key}: $what ${ridgeSide(x, y)} of ridge, grid ${CoordinateText.latitudeDm(geo.lat)} ${CoordinateText.longitudeDm(geo.lon)}"
            else -> "possible $what, ${direction.lowercase()} of our pos ~${distance}m, no further info"
        }
        return RawFeedMessage(SourceFormat.SPOT_TEXT, payload, observer.key, at)
    }

    private fun ais(subject: SeedEntity, geo: GeoPoint, at: Long): RawFeedMessage {
        val speed = random.nextDouble(0.0, 8.0)
        val payload = buildJsonObject {
            if (!random.chance(0.3)) field("mmsi", (366_000_000 + subject.key.hashCode().mod(999_999)).toString())
            field("name", "\"${if (random.chance(0.4)) "UNKNOWN" else subject.key}\"")
            if (random.chance(0.2)) {
                field("lat", "\"${CoordinateText.latitudeDm(geo.lat)}\"")
                field("lon", "\"${CoordinateText.longitudeDm(geo.lon)}\"")
            } else {
                field("lat", decimal(geo.lat))
                field("lon", decimal(geo.lon))
            }
            if (random.chance(0.1)) field("latitude", decimal(geo.lat + random.nextGaussian() * CONFLICT_SIGMA_DEG))
            field("sog", if (random.chance(0.15)) "\"${one(speed)}kn\"" else one(speed))
            field("cog", subject.headingDeg?.roundToInt()?.toString() ?: "null")
            if (!random.chance(0.5)) field("alt_m", "0")
        }
        return RawFeedMessage(SourceFormat.AIS_JSON, payload, AIS_SENSOR, at)
    }

    private fun adsb(subject: SeedEntity, geo: GeoPoint, at: Long): RawFeedMessage {
        val altitudeFt = (FEET_PER_M * random.nextDouble(90.0, 420.0)).roundToInt()
        val payload = buildJsonObject {
            field("icao", String.format(Locale.ROOT, "\"%06X\"", subject.key.hashCode() and 0xFFFFFF))
            if (!random.chance(0.4)) field("flight", "\"${subject.key.replace("-", "")}\"")
            field("lat", decimal(geo.lat))
            field("lon", decimal(geo.lon))
            when {
                random.chance(0.3) -> field("alt_ft", "null")
                random.chance(0.25) -> field("alt_ft", String.format(Locale.ROOT, "\"%,d ft\"", altitudeFt))
                else -> field("alt_ft", altitudeFt.toString())
            }
            field("gs_kt", (subject.headingDeg?.let { random.nextInt(24, 64) } ?: 0).toString())
            subject.headingDeg?.let { field("trk", it.roundToInt().toString()) }
        }
        return RawFeedMessage(SourceFormat.ADSB_JSON, payload, ADSB_SENSOR, at)
    }

    private fun ridgeSide(x: Double, y: Double): String =
        if (Terrain.signedDistanceToCrest(x, y) >= 0) "east" else "west"

    private fun bandOf(subject: SeedEntity): String =
        subject.attributes[EntityAttributes.FREQ]?.let(::compactFrequency) ?: "UHF"

    private fun compactFrequency(text: String): String {
        val value = text.substringBefore(' ')
        return if (text.endsWith("GHz")) "${value}G" else "${value}M"
    }

    companion object {
        private const val POSITION_SIGMA_M = 35.0
        private const val AZIMUTH_SIGMA_DEG = 3.0
        private const val CONFLICT_SIGMA_DEG = 0.004
        private const val FEET_PER_M = 3.28084
        private const val AIS_SENSOR = "AIS-SHORE"
        private const val ADSB_SENSOR = "ADSB-RX1"
        private val SUBJECTS = ScenarioSeed.entities.filter { it.isTrack && it.kind != EntityKind.SENSOR }
        private val OBSERVERS = ScenarioSeed.entities.filter {
            it.affiliation == Affiliation.FRIEND && it.kind == EntityKind.TRACK
        }
        private val RF_SENSOR = ScenarioSeed.entity(ScenarioKeys.RF_RX2)
        private val COMPASS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

        fun conflictingReport(receivedAtMs: Long): RawFeedMessage {
            val emitter = ScenarioSeed.entity(ScenarioKeys.TRK_07)
            val geo = OperationalArea.projection.toGeo(emitter.local.x + 40.0, emitter.local.y - 25.0)
            val payload = "${ScenarioKeys.BLUE_4}: re ${emitter.key}, van east of ridge is civilian utility truck, " +
                "dish is sat-TV antenna, assess NEUTRAL. vic ${decimal(geo.lat)},${decimal(geo.lon)}"
            return RawFeedMessage(SourceFormat.SPOT_TEXT, payload, ScenarioKeys.BLUE_4, receivedAtMs)
        }
    }
}
