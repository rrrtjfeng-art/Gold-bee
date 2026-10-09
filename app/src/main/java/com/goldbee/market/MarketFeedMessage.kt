package com.goldbee.market

/**
 * 统一行情消息类型。
 *
 * 用于区分连接状态、价格更新和错误。
 */
sealed class MarketFeedMessage {

    data class Price(
        val tick: MarketTick
    ) : MarketFeedMessage()

    data class Status(
        val state: MarketConnectionState
    ) : MarketFeedMessage()

    data class Error(
        val message: String
    ) : MarketFeedMessage()
}
