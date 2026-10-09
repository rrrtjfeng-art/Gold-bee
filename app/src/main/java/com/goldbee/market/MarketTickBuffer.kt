package com.goldbee.market

/**
 * 管理实时 Tick 缓存。
 *
 * 用途：
 * 1. 保存最近的实时行情。
 * 2. 拒绝无效或过期 Tick。
 * 3. 避免旧行情覆盖新行情。
 * 4. 为实时 K 线引擎提供价格数据。
 */
class MarketTickBuffer(
    private val maxTicks: Int = 1000,
    private val maxAgeMillis: Long = 60_000L
) {

    private val ticks = ArrayDeque<MarketTick>()

    init {
        require(maxTicks > 0) {
            "maxTicks must be greater than zero"
        }
        require(maxAgeMillis > 0L) {
            "maxAgeMillis must be greater than zero"
        }
    }

    @Synchronized
    fun add(
        tick: MarketTick,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!tick.isValid()) return false
        if (!tick.isFresh(nowMillis, maxAgeMillis)) return false

        val latest = ticks.lastOrNull()

        if (latest != null) {
            if (!latest.symbol.equals(tick.symbol, ignoreCase = true)) {
                return false
            }

            if (tick.timestamp < latest.timestamp) {
                return false
            }

            if (
                tick.timestamp == latest.timestamp &&
                tick.bid == latest.bid &&
                tick.ask == latest.ask
            ) {
                return false
            }
        }

        ticks.addLast(tick)

        while (ticks.size > maxTicks) {
            ticks.removeFirst()
        }

        removeExpired(nowMillis)

        return true
    }

    @Synchronized
    fun latest(): MarketTick? = ticks.lastOrNull()

    @Synchronized
    fun recent(count: Int): List<MarketTick> {
        if (count <= 0) return emptyList()
        return ticks.toList().takeLast(count)
    }

    @Synchronized
    fun size(): Int = ticks.size

    @Synchronized
    fun clear() {
        ticks.clear()
    }

    @Synchronized
    fun removeExpired(
        nowMillis: Long = System.currentTimeMillis()
    ) {
        while (ticks.isNotEmpty()) {
            val oldest = ticks.first()
            val age = nowMillis - oldest.timestamp

            if (age <= maxAgeMillis && age >= 0L) {
                break
            }

            ticks.removeFirst()
        }
    }
}
