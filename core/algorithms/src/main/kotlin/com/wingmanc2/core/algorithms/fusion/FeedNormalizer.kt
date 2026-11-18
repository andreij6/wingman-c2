package com.wingmanc2.core.algorithms.fusion

import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.SourceFormat
import javax.inject.Inject

class FeedNormalizer @Inject constructor() {

    fun normalize(format: SourceFormat, payload: String, landmarks: Map<String, LocalPoint>): NormalizedObservation = when (format) {
        SourceFormat.RF_PING -> RfPingReader.read(payload, landmarks)
        SourceFormat.AIS_JSON -> TransponderReader.readAis(payload)
        SourceFormat.ADSB_JSON -> TransponderReader.readAdsb(payload)
        SourceFormat.SPOT_TEXT -> SpotReportReader.read(payload, landmarks)
    }
}
