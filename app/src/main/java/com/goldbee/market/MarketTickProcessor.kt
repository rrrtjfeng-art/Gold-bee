package com.goldbee.market

/**
 * 统一处理进入 Gold Bee 的实时 Tick。
 *
 * 负责：
 * 1. 检查 Tick 是否有效。
 * 2. 检查 Tick 是否过期。
 * 3. 缓存最新行情。
 * 4. 将价格交给 K 线聚合器。
 * 5. 通知上层行情更新。
 *
 * 本类不连接外部行情服务器。
 */
class MarketTickProcessor(
    private val symbol: String = "XAUUSD",
    private val tickBuffer: MarketTickBuffer = MarketTickBuffer(),
    private val candleAggregator: CandleAggregator =
        CandleAggregator(symbol),
    private val maxTickAgeMillis: Long = 3000L
) {

    @Volatile
    private var latestTick: MarketTick? = null

    @Volatile
    private var listener: MarketTickProcessorListener? = null

    init {
        require(symbol.isNotBlank()) {
            "Symbol cannot be blank"
        }
        require(maxTickAgeMillis > 0L) {
            "maxTickAgeMillis must be greater than zero"
        }
    }

    fun setListener(
        listener: MarketTickProcessorListener?
    ) {
        this.listener = listener
    }

    @Synchronized
    fun process(
        tick: MarketTick,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!tick.isValid()) {
            listener?.onRejected(tick, "无效行情数据")
            return false
        }

        if (!tick.isSymbol(symbol)) {
            listener?.onRejected(tick, "行情品种不匹配")
            return false
        }

        if (!tick.isFresh(nowMillis, maxTickAgeMillis)) {
            listener?.onRejected(tick, "行情已过期")
            return false
        }

        val previous = latestTick

        if (
            previous != null &&
            tick.timestamp < previous.timestamp
        ) {
            listener?.onRejected(tick, "收到较旧的行情")
            return false
        }

        if (!tickBuffer.add(tick, nowMillis)) {
            listener?.onRejected(tick, "行情重复或缓存拒绝")
            return false
        }

        latestTick = tick

        // CandleAggregator 使用秒级 Unix 时间戳。
        val timestampSeconds = tick.timestamp / 1000L

        candleAggregator.onPrice(
            price = tick.midPrice,
            timestampSeconds = timestampSeconds
        )

        listener?.onProcessed(
            tick = tick,
            currentCandleM1 =
                candleAggregator.getCurrentCandle(Timeframe.M1)
        )

        return true
    }

    fun getLatestTick(): MarketTick? = latestTick

    fun getLatestPrice(): Double? = latestTick?.midPrice

    fun getLatestBid(): Double? = latestTick?.bid

    fun getLatestAsk(): Double? = latestTick?.ask

    fun getSpread(): Double? = latestTick?.spread

    fun getCandles(
        timeframe: Timeframe
    ): List<Candle> {
        return candleAggregator.getCandlesIncludingCurrent(timeframe)
    }

    fun getTickBuffer(): MarketTickBuffer = tickBuffer

    fun getCandleAggregator(): CandleAggregator =
        candleAggregator

    fun clear() {
        synchronized(this) {
            latestTick = null
            tickBuffer.clear()
        }
    }
}

/**
 * Tick 处理结果监听器。
 */
interface MarketTickProcessorListener {

    fun onProcessed(
        tick: MarketTick,
        currentCandleM1: Candle?
    )

    fun onRejected(
        tick: MarketTick,
        reason: String
    )
}
