package com.goldbee.market

class MarketFreshnessGuard(
    private val maxAgeMillis: Long = 3000L
) {

    fun isFresh(
        snapshot: MarketSnapshot?
    ): Boolean {

        if (snapshot == null) {
            return false
        }

        if (!snapshot.isPriceValid()) {
            return false
        }

        return snapshot.isFresh(
            maxAgeMillis = maxAgeMillis
        )
    }

    fun getAgeMillis(
        snapshot: MarketSnapshot?
    ): Long? {

        snapshot ?: return null

        val age =
            System.currentTimeMillis() -
                    snapshot.receivedAt

        return if (age >= 0L) {
            age
        } else {
            null
        }
    }
}
