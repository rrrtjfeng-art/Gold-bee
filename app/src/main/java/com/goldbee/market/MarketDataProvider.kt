package com.goldbee.market

interface MarketDataProvider {

    /**
     * 当前是否已经连接到行情数据源。
     */
    fun isConnected(): Boolean

    /**
     * 获取当前最新的市场快照。
     *
     * 如果暂时没有有效数据，返回 null。
     */
    fun getLatestSnapshot(): MarketSnapshot?

    /**
     * 开始接收实时行情。
     */
    fun connect()

    /**
     * 停止接收实时行情。
     */
    fun disconnect()

    /**
     * 注册行情更新监听器。
     */
    fun setListener(
        listener: MarketDataListener?
    )
}

interface MarketDataListener {

    /**
     * 收到新的实时行情。
     */
    fun onMarketUpdate(
        snapshot: MarketSnapshot
    )

    /**
     * 行情连接成功。
     */
    fun onConnected()

    /**
     * 行情连接断开。
     */
    fun onDisconnected()

    /**
     * 行情发生错误。
     */
    fun onError(
        message: String
    )
}
