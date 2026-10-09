package com.goldbee.market

/**
 * Gold Bee 行情连接状态。
 */
enum class MarketConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * 供 UI 显示和行情管理层使用的连接状态。
 */
data class MarketConnectionState(
    val status: MarketConnectionStatus =
        MarketConnectionStatus.DISCONNECTED,
    val source: String = "",
    val symbol: String = "XAUUSD",
    val lastUpdateTime: Long? = null,
    val errorMessage: String? = null
) {

    val isConnected: Boolean
        get() = status == MarketConnectionStatus.CONNECTED

    val hasError: Boolean
        get() = status == MarketConnectionStatus.ERROR

    fun displayText(): String {
        return when (status) {
            MarketConnectionStatus.DISCONNECTED ->
                "行情未连接"

            MarketConnectionStatus.CONNECTING ->
                "正在连接行情"

            MarketConnectionStatus.CONNECTED ->
                "行情已连接"

            MarketConnectionStatus.RECONNECTING ->
                "行情重新连接中"

            MarketConnectionStatus.ERROR ->
                errorMessage?.let {
                    "行情错误：$it"
                } ?: "行情连接错误"
        }
    }

    companion object {

        fun disconnected(
            symbol: String = "XAUUSD"
        ) = MarketConnectionState(
            status = MarketConnectionStatus.DISCONNECTED,
            symbol = symbol
        )

        fun connecting(
            symbol: String,
            source: String
        ) = MarketConnectionState(
            status = MarketConnectionStatus.CONNECTING,
            symbol = symbol,
            source = source
        )

        fun connected(
            symbol: String,
            source: String,
            updateTime: Long
        ) = MarketConnectionState(
            status = MarketConnectionStatus.CONNECTED,
            symbol = symbol,
            source = source,
            lastUpdateTime = updateTime
        )

        fun reconnecting(
            symbol: String,
            source: String
        ) = MarketConnectionState(
            status = MarketConnectionStatus.RECONNECTING,
            symbol = symbol,
            source = source
        )

        fun error(
            symbol: String,
            source: String,
            message: String
        ) = MarketConnectionState(
            status = MarketConnectionStatus.ERROR,
            symbol = symbol,
            source = source,
            errorMessage = message
        )
    }
}
