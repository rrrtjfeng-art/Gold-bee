package com.goldbee.market

enum class Timeframe(
    val code: String,
    val seconds: Long
) {

    M1(
        code = "1min",
        seconds = 60
    ),

    M5(
        code = "5min",
        seconds = 5 * 60
    ),

    M15(
        code = "15min",
        seconds = 15 * 60
    ),

    H1(
        code = "1h",
        seconds = 60 * 60
    );

    companion object {

        fun fromCode(code: String): Timeframe? {
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true)
            }
        }
    }
}
