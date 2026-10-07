package com.goldbee.decision

data class CopySignal(
    val direction: TradeDirection,
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val rawText: String
) {

    fun hasExplicitEntry(): Boolean {
        return entry != null && entry > 0.0
    }

    fun hasStopLoss(): Boolean {
        return stopLoss != null && stopLoss > 0.0
    }

    fun hasTakeProfit(): Boolean {
        return takeProfit != null && takeProfit > 0.0
    }
}

object CopySignalParser {

    fun parse(
        text: String
    ): Result<CopySignal> {

        if (text.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "复制的信号为空。"
                )
            )
        }

        val upper =
            text.uppercase()

        val direction =
            when {
                containsBuy(upper) ->
                    TradeDirection.BUY

                containsSell(upper) ->
                    TradeDirection.SELL

                else ->
                    return Result.failure(
                        IllegalArgumentException(
                            "没有识别到 BUY 或 SELL。"
                        )
                    )
            }

        val entry =
            findPrice(
                upper,
                listOf(
                    "ENTRY",
                    "ENTER",
                    "ENT",
                    "入场",
                    "进场"
                )
            )

        val stopLoss =
            findPrice(
                upper,
                listOf(
                    "SL",
                    "STOP LOSS",
                    "止损"
                )
            )

        val takeProfit =
            findPrice(
                upper,
                listOf(
                    "TP",
                    "TAKE PROFIT",
                    "止盈"
                )
            )

        return Result.success(
            CopySignal(
                direction = direction,
                entry = entry,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rawText = text
            )
        )
    }

    private fun containsBuy(
        text: String
    ): Boolean {

        return Regex(
            """\bBUY\b"""
        ).containsMatchIn(text) ||
                text.contains("买入") ||
                text.contains("做多")
    }

    private fun containsSell(
        text: String
    ): Boolean {

        return Regex(
            """\bSELL\b"""
        ).containsMatchIn(text) ||
                text.contains("卖出") ||
                text.contains("做空")
    }

    private fun findPrice(
        text: String,
        labels: List<String>
    ): Double? {

        for (label in labels) {

            val escaped =
                Regex.escape(label)

            val regex =
                Regex(
                    """$escaped\s*[:=@]?\s*(\d+(?:\.\d+)?)"""
                )

            val match =
                regex.find(text)
                    ?: continue

            return match
                .groupValues
                .getOrNull(1)
                ?.toDoubleOrNull()
        }

        return null
    }
}
