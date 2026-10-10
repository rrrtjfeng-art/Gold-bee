package com.goldbee.mt5

/**
 * A UI accessibility event alone is not proof of a fresh market tick.
 * Only a valid XAUUSD quote with a changed Bid or Ask refreshes quote age.
 */
object Mt5QuoteFreshness {
    fun isNewExecutableQuote(
        symbol: String?,
        bid: Double?,
        ask: Double?,
        previousBid: Double?,
        previousAsk: Double?
    ): Boolean {
        val normalized = symbol.orEmpty().uppercase().replace("/", "").trim()
        if (normalized !in setOf("XAUUSD", "GOLD")) return false
        if (bid == null || ask == null || !bid.isFinite() || !ask.isFinite() ||
            bid <= 0.0 || ask <= bid
        ) return false
        return previousBid == null || previousAsk == null ||
            bid != previousBid || ask != previousAsk
    }
}
