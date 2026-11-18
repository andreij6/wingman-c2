package com.wingmanc2.core.model

data class ScenarioProfile(
    val primaryAssetKey: String,
    val home: GeoPoint,
    val defaultFocusKey: String,
    val defaultIntent: String,
)
