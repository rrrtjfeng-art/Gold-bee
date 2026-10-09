package com.goldbee.market

/**
 * 实时 Tick 事件监听器。
 */
interface MarketTickListener {

    /**
     * 收到新的有效行情。
     */
    fun onTick(tick: MarketTick)

    /**
     * 行情连接成功。
     */
    fun onConnected(source: String)

    /**
     * 行情连接断开。
     */
    fun onDisconnected(source: String)

    /**
     * 行情来源发生错误。
     */
    fun onError(
        source: String,
        message: String
    )
}
