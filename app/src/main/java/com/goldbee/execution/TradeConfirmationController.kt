package com.goldbee.execution

import com.goldbee.decision.DecisionResult

enum class ConfirmationState {
    WAITING,
    CONFIRMED,
    CANCELLED
}

data class ConfirmedOrderRequest(
    val decision: DecisionResult,
    val confirmedAt: Long
)

class TradeConfirmationController {

    private var state =
        ConfirmationState.WAITING

    private var confirmedRequest:
        ConfirmedOrderRequest? = null

    fun getState(): ConfirmationState {
        return state
    }

    fun getConfirmedRequest():
        ConfirmedOrderRequest? {
        return confirmedRequest
    }

    fun confirm(
        decision: DecisionResult
    ): ConfirmedOrderRequest? {

        if (decision.setup == null) {
            return null
        }

        if (
            decision.action.name != "BUY" &&
            decision.action.name != "SELL"
        ) {
            return null
        }

        if (!decision.setup.isValid()) {
            return null
        }

        val request =
            ConfirmedOrderRequest(
                decision = decision,
                confirmedAt =
                    System.currentTimeMillis()
            )

        confirmedRequest =
            request

        state =
            ConfirmationState.CONFIRMED

        return request
    }

    fun cancel() {

        confirmedRequest = null

        state =
            ConfirmationState.CANCELLED
    }

    fun reset() {

        confirmedRequest = null

        state =
            ConfirmationState.WAITING
    }
}
