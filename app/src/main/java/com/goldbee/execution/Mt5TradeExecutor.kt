package com.goldbee.execution

interface Mt5TradeExecutor {

    fun isAvailable(): Boolean

    fun prepare(
        request: Mt5OrderRequest
    ): Result<Unit>

    fun execute(
        request: Mt5OrderRequest
    ): Result<Mt5OrderResult>

    fun cancel()
}

data class Mt5OrderResult(
    val success: Boolean,
    val message: String,
    val orderId: String? = null,
    val executedPrice: Double? = null,
    val executedVolume: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
