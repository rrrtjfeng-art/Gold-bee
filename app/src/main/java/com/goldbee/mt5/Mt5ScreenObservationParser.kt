package com.goldbee.mt5

data class Mt5ScreenObservation(
    val symbol: String?,
    val timeframe: String?,
    val direction: String?,
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val bid: Double?,
    val ask: Double?,
    val visibleTextCount: Int
) {
    val hasTradeLevels: Boolean
        get() = direction != null && entry != null && stopLoss != null && takeProfit != null
}

/**
 * Extracts only a small set of market-related fields from visible MT5 accessibility text.
 * Raw screen text is deliberately not returned or stored.
 */
object Mt5ScreenObservationParser {
    private val symbolRegex = Regex("(?i)(?<![A-Z0-9])(XAUUSD|XAU/USD|GOLD)(?![A-Z0-9])")
    private val timeframeRegex = Regex("(?i)(?<![A-Z0-9])(M1|M5|M15|M30|H1|H4|D1|W1|MN1?)(?![A-Z0-9])")
    private val number = """([0-9]{3,5}(?:\.[0-9]{1,3})?)"""
    private val entryRegex = Regex("(?i)(?:ENTRY|ENT|OPEN PRICE|入场价|进场价|入场|进场)\\s*[:：=@-]?\\s*$number")
    private val slRegex = Regex("(?i)(?:STOP\\s*LOSS|STOPLOSS|S\\.?L\\.?|止损)\\s*[:：=@-]?\\s*$number")
    private val tpRegex = Regex("(?i)(?:TAKE\\s*PROFIT|TAKEPROFIT|T\\.?P\\.?[0-9]*|止盈)\\s*[:：=@-]?\\s*$number")
    private val bidRegex = Regex("(?i)(?:BID|买价|卖出价)\\s*[:：=@-]?\\s*$number")
    private val askRegex = Regex("(?i)(?:ASK|卖价|买入价)\\s*[:：=@-]?\\s*$number")
    private val priceRegex = Regex("(?i)(?:CURRENT\\s*PRICE|PRICE|报价|当前价格)\\s*[:：=@-]?\\s*$number")

    fun parse(visibleTexts: List<String>): Mt5ScreenObservation {
        val safeTexts = visibleTexts
            .asSequence()
            .map { it.trim().take(300) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(250)
            .toList()
        val joined = safeTexts.joinToString(" | ")
        val symbol = symbolRegex.find(joined)?.groupValues?.getOrNull(1)?.uppercase()
            ?.replace("/", "")
        val timeframe = timeframeRegex.find(joined)?.groupValues?.getOrNull(1)?.uppercase()
        val direction = when {
            Regex("(?i)(?<![A-Z])BUY(?![A-Z])").containsMatchIn(joined) -> "BUY"
            Regex("(?i)(?<![A-Z])SELL(?![A-Z])").containsMatchIn(joined) -> "SELL"
            else -> null
        }

        return Mt5ScreenObservation(
            symbol = symbol,
            timeframe = timeframe,
            direction = direction,
            entry = findFirst(entryRegex, safeTexts),
            stopLoss = findFirst(slRegex, safeTexts),
            takeProfit = findFirst(tpRegex, safeTexts),
            bid = findFirst(bidRegex, safeTexts) ?: findFirst(priceRegex, safeTexts),
            ask = findFirst(askRegex, safeTexts),
            visibleTextCount = safeTexts.size
        )
    }

    private fun findFirst(regex: Regex, texts: List<String>): Double? =
        texts.firstNotNullOfOrNull { text ->
            regex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
        }
}
