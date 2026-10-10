package com.goldbee.mt5

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.goldbee.paper.PaperTradeMonitor

/**
 * Read-only observer for the official MT5 Android package.
 *
 * This service does not take screenshots, perform gestures, tap controls, or place orders.
 * It stores parsed market fields only; raw visible text is not persisted.
 */
class Mt5ScreenAccessibilityService : AccessibilityService() {
    private var lastProcessedAt = 0L

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 500
            packageNames = arrayOf(MT5_PACKAGE)
        }
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != MT5_PACKAGE) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessedAt < MIN_PROCESS_INTERVAL_MS) return
        lastProcessedAt = now

        val texts = mutableListOf<String>()
        collectNodeText(rootInActiveWindow, texts, MAX_NODES)
        event.text.forEach { item ->
            if (item != null) texts += item.toString()
        }
        val observation = Mt5ScreenObservationParser.parse(texts)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(KEY_OBSERVED_AT, System.currentTimeMillis())
            .putString(KEY_SOURCE, "ACCESSIBILITY")
            .putString(KEY_SYMBOL, observation.symbol.orEmpty())
            .putString(KEY_TIMEFRAME, observation.timeframe.orEmpty())
            .putString(KEY_DIRECTION, observation.direction.orEmpty())
            .putString(KEY_ENTRY, observation.entry?.toString().orEmpty())
            .putString(KEY_SL, observation.stopLoss?.toString().orEmpty())
            .putString(KEY_TP, observation.takeProfit?.toString().orEmpty())
            .putString(KEY_BID, observation.bid?.toString().orEmpty())
            .putString(KEY_ASK, observation.ask?.toString().orEmpty())
            .putInt(KEY_TEXT_COUNT, observation.visibleTextCount)
            .apply()
        PaperTradeMonitor.checkAndUpdate(this)
    }

    private fun collectNodeText(
        node: AccessibilityNodeInfo?,
        output: MutableList<String>,
        remaining: Int
    ) {
        if (node == null || output.size >= remaining) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) output += text
        if (description.isNotBlank()) output += description
        for (i in 0 until node.childCount) {
            if (output.size >= remaining) break
            collectNodeText(node.getChild(i), output, remaining)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        const val MT5_PACKAGE = "net.metaquotes.metatrader5"
        const val PREFS_NAME = "mt5_screen_observation"
        const val KEY_OBSERVED_AT = "observed_at"
        const val KEY_SYMBOL = "symbol"
        const val KEY_TIMEFRAME = "timeframe"
        const val KEY_DIRECTION = "direction"
        const val KEY_ENTRY = "entry"
        const val KEY_SL = "sl"
        const val KEY_TP = "tp"
        const val KEY_BID = "bid"
        const val KEY_ASK = "ask"
        const val KEY_TEXT_COUNT = "text_count"
        const val KEY_SOURCE = "observation_source"
        private const val MIN_PROCESS_INTERVAL_MS = 700L
        private const val MAX_NODES = 250
    }
}
