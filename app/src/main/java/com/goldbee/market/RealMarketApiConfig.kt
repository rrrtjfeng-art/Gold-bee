package com.goldbee.market

/**
 * RealMarketAPI 连接配置。
 *
 * 不在代码中硬编码真实 API Key。
 */
data class RealMarketApiConfig(
    val apiKey: String,
    val symbol: String = "XAUUSD",
    val timeframe: String = "M1"
) {
    fun isValid(): Boolean {
        return apiKey.isNotBlank() &&
            symbol.isNotBlank() &&
            timeframe in setOf("M1", "M5", "M15", "H1", "H4", "D1")
    }

    fun normalizedSymbol(): String = symbol.trim().uppercase()
}
