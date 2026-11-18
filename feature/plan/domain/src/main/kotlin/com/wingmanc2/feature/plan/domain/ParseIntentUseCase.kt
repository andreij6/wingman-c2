package com.wingmanc2.feature.plan.domain

import com.wingmanc2.core.algorithms.nlp.IntentParser
import com.wingmanc2.core.model.CommanderIntent
import javax.inject.Inject

class ParseIntentUseCase @Inject constructor(private val parser: IntentParser) {
    operator fun invoke(text: String): CommanderIntent = parser.parse(text)

    fun defaultFor(targetKey: String): CommanderIntent =
        parser.parse("Recon $targetKey from 200 m standoff, avoid the ridge radar, keep 30% battery reserve.")
}
