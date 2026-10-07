package com.goldbee.decision

import com.goldbee.market.MarketSnapshot

object DecisionConverter {

    fun fromDecision(
        mode: DecisionMode,
        decision: DecisionResult,
        snapshot: MarketSnapshot
    ): GoldBeeDecision {

        return GoldBeeDecision(
            mode = mode,
            action = decision.action,
            setup = decision.setup,
            confidence = decision.confidence,
            reason = decision.reason,
            currentPrice = snapshot.midPrice
        )
    }
}
