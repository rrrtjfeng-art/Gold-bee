package com.goldbee.decision

import com.goldbee.analysis.MultiTimeframeAnalyzer
import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.market.MarketSnapshot
import com.goldbee.market.Timeframe
import com.goldbee.risk.RiskLimits
import com.goldbee.risk.RiskState

data class RealDecisionResult(
    val analysis: MultiTimeframeAnalysis,
    val gateResult: TradeDecisionGateResult
)

object RealDecisionService {

    fun analyze(
        snapshot: MarketSnapshot,
        limits: RiskLimits = RiskLimits(),
        riskState: RiskState = RiskState()
    ): RealDecisionResult {

        val candles = mapOf(
            Timeframe.M5 to snapshot.candlesFor(Timeframe.M5),
            Timeframe.M15 to snapshot.candlesFor(Timeframe.M15),
            Timeframe.H1 to snapshot.candlesFor(Timeframe.H1)
        )

        val analysis =
            MultiTimeframeAnalyzer.analyze(candles)

        val gateResult =
            TradeDecisionGate.evaluate(
                snapshot = snapshot,
                analysis = analysis,
                candles = candles,
                limits = limits,
                riskState = riskState
            )

        return RealDecisionResult(
            analysis = analysis,
            gateResult = gateResult
        )
    }
}
