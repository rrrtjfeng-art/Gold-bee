package com.goldbee.market

/**
 * Describes provider plan capability separately from whether the app has an
 * active, validated streaming connection. A plan alone never proves live data.
 */
enum class RealMarketApiPlan(
    val supportsWebSocketStreaming: Boolean
) {
    FREE(false),
    STARTER(false),
    PLUS(true),
    PRO(true),
    BUSINESS(true)
}

object RealMarketPlanPolicy {

    fun allowsActionableSignals(
        plan: RealMarketApiPlan,
        liveStreamConnected: Boolean = false
    ): Boolean = plan.supportsWebSocketStreaming && liveStreamConnected

    fun blockReason(
        plan: RealMarketApiPlan,
        liveStreamConnected: Boolean = false
    ): String = when {
        !plan.supportsWebSocketStreaming ->
            "NO TRADE：RealMarketAPI ${plan.name} 套餐不提供 WebSocket 连续行情流。REST 返回的 K 线只能作为参考，不能验证当前实时 Bid/Ask；REAL/COPY 可跟随信号已关闭。"

        !liveStreamConnected ->
            "NO TRADE：套餐可能支持 WebSocket，但应用尚未建立并验证实时行情流。仅凭 REST 快照或本机收到时间不能生成可跟随信号。"

        else ->
            "实时行情连接可用；仍需通过来源时间、点差、风险和策略检查。"
    }
}
