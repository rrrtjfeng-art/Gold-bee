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
 * Diagnostics are recorded before package filtering so a package-name mismatch
 * cannot make a running service look completely inactive. Only metadata is saved;
 * raw screen text is never persisted.
 */
class Mt5ScreenAccessibilityService : AccessibilityService() {
    private var lastProcessedAt = 0L

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            // Listen broadly enough to diagnose whether Android is delivering events.
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 300
            packageNames = null
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_SERVICE_CONNECTED, true)
            .putLong(KEY_SERVICE_CONNECTED_AT, System.currentTimeMillis())
            .apply()
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventPackage = event.packageName?.toString().orEmpty()
        val nowWall = System.currentTimeMillis()
        val diagnosticPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // Record every delivered event before filtering. This reveals whether the
        // service is alive but MT5's actual package name differs from expectations.
        diagnosticPrefs.edit()
            .putLong(KEY_LAST_ANY_EVENT_AT, nowWall)
            .putString(KEY_LAST_ANY_EVENT_PACKAGE, eventPackage)
            .putInt(KEY_LAST_ANY_EVENT_TYPE, event.eventType)
            .putInt(KEY_ANY_EVENT_COUNT, diagnosticPrefs.getInt(KEY_ANY_EVENT_COUNT, 0) + 1)
            .apply()

        if (!isSupportedMt5Package(eventPackage)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessedAt < MIN_PROCESS_INTERVAL_MS) return
        lastProcessedAt = now

        val texts = mutableListOf<String>()
        val root = rootInActiveWindow
        val scanStats = NodeScanStats(rootWasNull = root == null)
        collectNodeText(root, texts, scanStats, MAX_NODES)
        var eventTextCount = 0
        event.text.forEach { item ->
            if (item != null && item.toString().isNotBlank()) {
                texts += item.toString()
                eventTextCount++
            }
        }
        val observation = Mt5ScreenObservationParser.parse(texts)
        val observedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        observedPrefs.edit()
            .putLong(KEY_LAST_EVENT_AT, nowWall)
            .putString(KEY_LAST_EVENT_PACKAGE, eventPackage)
            .putInt(KEY_LAST_EVENT_TEXT_COUNT, texts.size)
            .putInt(KEY_LAST_EVENT_NODE_COUNT, scanStats.nodesVisited)
            .putInt(KEY_LAST_EVENT_NODE_TEXT_COUNT, scanStats.textNodeCount)
            .putInt(KEY_LAST_EVENT_DESCRIPTION_COUNT, scanStats.descriptionCount)
            .putInt(KEY_LAST_EVENT_EVENT_TEXT_COUNT, eventTextCount)
            .putBoolean(KEY_LAST_EVENT_ROOT_NULL, scanStats.rootWasNull)
            .putBoolean(KEY_LAST_PARSE_HAS_SYMBOL, observation.symbol != null)
            .putBoolean(KEY_LAST_PARSE_HAS_BID, observation.bid != null)
            .putBoolean(KEY_LAST_PARSE_HAS_ASK, observation.ask != null)
            .putInt(KEY_EVENT_COUNT, observedPrefs.getInt(KEY_EVENT_COUNT, 0) + 1)
            .apply()

        val previousBid = observedPrefs.getString(KEY_BID, "").orEmpty().toDoubleOrNull()
        val previousAsk = observedPrefs.getString(KEY_ASK, "").orEmpty().toDoubleOrNull()
        val hasNewQuote = Mt5QuoteFreshness.isNewExecutableQuote(
            symbol = observation.symbol,
            bid = observation.bid,
            ask = observation.ask,
            previousBid = previousBid,
            previousAsk = previousAsk
        )
        val editor = observedPrefs.edit()
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
        if (hasNewQuote) {
            editor.putLong(KEY_OBSERVED_AT, nowWall)
        } else if (previousBid == null || previousAsk == null) {
            editor.putLong(KEY_OBSERVED_AT, 0L)
        }
        editor.apply()
        PaperTradeMonitor.checkAndUpdate(this)
    }

    private fun isSupportedMt5Package(packageName: String): Boolean {
        if (packageName == MT5_PACKAGE || packageName == "net.metaquotes.metatrader5ce") return true
        val appLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault("")
        return appLabel.contains("MetaTrader 5", ignoreCase = true) ||
            appLabel.equals("MT5", ignoreCase = true) ||
            packageName.contains("metatrader", ignoreCase = true) ||
            packageName.contains("metaquotes", ignoreCase = true)
    }

    private data class NodeScanStats(
        val rootWasNull: Boolean,
        var nodesVisited: Int = 0,
        var textNodeCount: Int = 0,
        var descriptionCount: Int = 0
    )

    private fun collectNodeText(
        node: AccessibilityNodeInfo?,
        output: MutableList<String>,
        stats: NodeScanStats,
        maximumNodes: Int
    ) {
        if (node == null || stats.nodesVisited >= maximumNodes) return
        stats.nodesVisited++
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) {
            output += text
            stats.textNodeCount++
        }
        if (description.isNotBlank()) {
            output += description
            stats.descriptionCount++
        }
        for (i in 0 until node.childCount) {
            if (stats.nodesVisited >= maximumNodes) break
            collectNodeText(node.getChild(i), output, stats, maximumNodes)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_SERVICE_CONNECTED, false)
            .apply()
        return super.onUnbind(intent)
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
        const val KEY_LAST_EVENT_AT = "last_accessibility_event_at"
        const val KEY_LAST_EVENT_PACKAGE = "last_accessibility_event_package"
        const val KEY_LAST_EVENT_TEXT_COUNT = "last_accessibility_event_text_count"
        const val KEY_LAST_EVENT_NODE_COUNT = "last_accessibility_event_node_count"
        const val KEY_LAST_EVENT_NODE_TEXT_COUNT = "last_accessibility_event_node_text_count"
        const val KEY_LAST_EVENT_DESCRIPTION_COUNT = "last_accessibility_event_description_count"
        const val KEY_LAST_EVENT_EVENT_TEXT_COUNT = "last_accessibility_event_event_text_count"
        const val KEY_LAST_EVENT_ROOT_NULL = "last_accessibility_event_root_null"
        const val KEY_LAST_PARSE_HAS_SYMBOL = "last_parse_has_symbol"
        const val KEY_LAST_PARSE_HAS_BID = "last_parse_has_bid"
        const val KEY_LAST_PARSE_HAS_ASK = "last_parse_has_ask"
        const val KEY_EVENT_COUNT = "accessibility_event_count"
        const val KEY_SERVICE_CONNECTED = "accessibility_service_connected"
        const val KEY_SERVICE_CONNECTED_AT = "accessibility_service_connected_at"
        const val KEY_LAST_ANY_EVENT_AT = "last_any_accessibility_event_at"
        const val KEY_LAST_ANY_EVENT_PACKAGE = "last_any_accessibility_event_package"
        const val KEY_LAST_ANY_EVENT_TYPE = "last_any_accessibility_event_type"
        const val KEY_ANY_EVENT_COUNT = "any_accessibility_event_count"
        private const val MIN_PROCESS_INTERVAL_MS = 300L
        private const val MAX_NODES = 350
    }
}
