package com.goldbee.analysis

data class MarketStructure(
val trend: Trend,
val strength: Double,
val higherHigh: Boolean,
val higherLow: Boolean,
val lowerHigh: Boolean,
val lowerLow: Boolean,
val support: Double?,
val resistance: Double?,
val breakout: Breakout
)

enum class Trend {
BULLISH,
BEARISH,
SIDEWAYS
}

enum class Breakout {
BULLISH,
BEARISH,
NONE
}
