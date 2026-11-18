package com.wingmanc2.core.database.mapping

import com.wingmanc2.core.model.CourseOfAction
import com.wingmanc2.core.model.GeoPoint
import com.wingmanc2.core.model.IntentConstraint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object BinaryCodec {

    private const val FORMAT_VERSION = 3
    private const val ALTITUDES_VERSION = 2
    private const val NO_ORBIT = -1
    private const val FIRST_FORMAT_VERSION = 1
    private const val TAG_STANDOFF = 1
    private const val TAG_AVOID_RADAR = 2
    private const val TAG_RESERVE = 3
    private const val TAG_MAX_ALTITUDE = 4
    private const val TAG_ZERO_RF = 5

    fun encodeConstraints(constraints: List<IntentConstraint>): ByteArray = encode {
        writeShort(constraints.size)
        for (constraint in constraints) {
            when (constraint) {
                is IntentConstraint.StandoffMin -> writeTagged(TAG_STANDOFF) { writeDouble(constraint.meters) }
                IntentConstraint.AvoidRadar -> writeByte(TAG_AVOID_RADAR)
                is IntentConstraint.ReserveMin -> writeTagged(TAG_RESERVE) { writeInt(constraint.percent) }
                is IntentConstraint.MaxAltitude -> writeTagged(TAG_MAX_ALTITUDE) { writeDouble(constraint.metersAgl) }
                IntentConstraint.ZeroRf -> writeByte(TAG_ZERO_RF)
            }
        }
    }

    fun decodeConstraints(bytes: ByteArray?): List<IntentConstraint> = if (bytes == null) emptyList() else decode(bytes) { _ ->
        List(readUnsignedShort()) {
            when (val tag = readUnsignedByte()) {
                TAG_STANDOFF -> IntentConstraint.StandoffMin(readDouble())
                TAG_AVOID_RADAR -> IntentConstraint.AvoidRadar
                TAG_RESERVE -> IntentConstraint.ReserveMin(readInt())
                TAG_MAX_ALTITUDE -> IntentConstraint.MaxAltitude(readDouble())
                TAG_ZERO_RF -> IntentConstraint.ZeroRf
                else -> throw IllegalStateException("Unknown constraint tag $tag")
            }
        }
    }

    fun encodeCoa(coa: CourseOfAction): ByteArray = encode {
        writeUTF(coa.code)
        writeByte(Codes.coaProfile.encode(coa.profile))
        writeUTF(coa.title)
        writeFloat(coa.matchScore)
        writeFloat(coa.riskScore)
        writeInt(coa.etaSeconds)
        writeInt(coa.projectedReservePct)
        writeDouble(coa.minStandoffM)
        writeDouble(coa.maxAltitudeAglM)
        writeInt(coa.rfExposureSeconds)
        writeInt(coa.waypoints.size)
        for (point in coa.waypoints) {
            writeDouble(point.lat)
            writeDouble(point.lon)
        }
        writeShort(coa.rationale.size)
        for (line in coa.rationale) writeUTF(line)
        writeBoolean(coa.tradeOff != null)
        coa.tradeOff?.let(::writeUTF)
        writeBoolean(coa.recommended)
        writeInt(coa.altitudesAglM.size)
        for (altitude in coa.altitudesAglM) writeDouble(altitude)
        writeInt(coa.orbitEntryIndex ?: NO_ORBIT)
    }

    fun decodeCoa(bytes: ByteArray): CourseOfAction = decode(bytes) { version ->
        CourseOfAction(
            code = readUTF(),
            profile = Codes.coaProfile.decode(readUnsignedByte()),
            title = readUTF(),
            matchScore = readFloat(),
            riskScore = readFloat(),
            etaSeconds = readInt(),
            projectedReservePct = readInt(),
            minStandoffM = readDouble(),
            maxAltitudeAglM = readDouble(),
            rfExposureSeconds = readInt(),
            waypoints = List(readInt()) { GeoPoint(readDouble(), readDouble()) },
            rationale = List(readUnsignedShort()) { readUTF() },
            tradeOff = if (readBoolean()) readUTF() else null,
            recommended = readBoolean(),
            altitudesAglM = if (version >= ALTITUDES_VERSION) List(readInt()) { readDouble() } else emptyList(),
            orbitEntryIndex = if (version >= FORMAT_VERSION) readInt().takeIf { it != NO_ORBIT } else null,
        )
    }

    private inline fun DataOutputStream.writeTagged(tag: Int, body: DataOutputStream.() -> Unit) {
        writeByte(tag)
        body()
    }

    private inline fun encode(body: DataOutputStream.() -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeByte(FORMAT_VERSION)
            it.body()
        }
        return buffer.toByteArray()
    }

    private inline fun <T> decode(bytes: ByteArray, body: DataInputStream.(version: Int) -> T): T =
        DataInputStream(ByteArrayInputStream(bytes)).use {
            val version = it.readUnsignedByte()
            check(version in FIRST_FORMAT_VERSION..FORMAT_VERSION) { "Unsupported codec version $version" }
            it.body(version)
        }
}
