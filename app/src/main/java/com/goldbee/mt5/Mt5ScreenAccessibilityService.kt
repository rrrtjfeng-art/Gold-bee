package com.goldbee.mt5

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.goldbee.paper.PaperTradeMonitor

/**
 * Read-only observer for MT5 Android.
 *
 * Some chart UIs update prices without reliably emitting content-change events.
 * In addition to events, periodically inspect the active accessibility tree while
 * MT5 is the foreground window. Only parsed fields and diagnostic metadata are saved.
 */
class Mt5ScreenAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastProcessedAt = 0L

    private val activeWindowPoll = object : Runnable {
        override fun run() {
            val root = runCatching { rootInActiveWindow }.getOrNull()
            val activePackage = root?.packageName?.toString().orEmpty()
            val now = System.currentTimeMillis()
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_POLL_AT, now)
                .putString(KEY_LAST_POLL_PACKAGE, activePackage)
                .putBoolean(KEY_LAST_POLL_ROOT_NULL, root == null)
                .apply()

            if (root != null && isSupportedMt5Package(activePackage)) {
                scanAndStore(activePackage, root, emptyList(), 0)
            }
            mainHandler.postDelayed(this, ACTIVE_WINDOW_POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
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
        mainHandler.removeCallbacks(activeWindowPoll)
        mainHandler.post(activeWindowPoll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString().orEmpty()
        val nowWall = System.currentTimeMillis()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_ANY_EVENT_AT, nowWall)
            .putString(KEY_LAST_ANY_EVENT_PACKAGE, eventPackage)
            .putInt(KEY_LAST_ANY_EVENT_TYPE, event.eventType)
            .putInt(KEY_ANY_EVENT_COUNT, prefs.getInt(KEY_ANY_EVENT_COUNT, 0) + 1)
            .apply()

        if (!isSupportedMt5Package(eventPackage)) return
        val eventTexts = event.text.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        scanAndStore(eventPackage, runCatching { rootInActiveWindow }.getOrNull(), eventTexts, eventTexts.size)
    }

    private fun scanAndStore(
        packageName: String,
        root: AccessibilityNodeInfo?,
        eventTexts: List<String>,
        eventTextCount: Int
    ) {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastProcessedAt < MIN_PROCESS_INTERVAL_MS) return
        lastProcessedAt = elapsed

        val texts = mutableListOf<String>()
        val stats = NodeScanStats(rootWasNull = root == null)
        collectNodeText(root, texts, stats, MAX_NODES)
        texts.addAll(eventTexts)
        val observation = Mt5ScreenObservationParser.parse(texts)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val nowWall = System.currentTimeMillis()

        prefs.edit()
            .putLong(KEY_LAST_EVENT_AT, nowWall)
            .putString(KEY_LAST_EVENT_PACKAGE, packageName)
            .putInt(KEY_LAST_EVENT_TEXT_COUNT, texts.size)
            .putInt(KEY_LAST_EVENT_NODE_COUNT, stats.nodesVisited)
            .putInt(KEY_LAST_EVENT_NODE_TEXT_COUNT, stats.textNodeCount)
            .putInt(KEY_LAST_EVENT_DESCRIPTION_COUNT, stats.descriptionCount)
            .putInt(KEY_LAST_EVENT_EVENT_TEXT_COUNT, eventTextCount)
            .putBoolean(KEY_LAST_EVENT_ROOT_NULL, stats.rootWasNull)
            .putBoolean(KEY_LAST_PARSE_HAS_SYMBOL, observation.symbol != null)
            .putBoolean(KEY_LAST_PARSE_HAS_BID, observation.bid != null)
            .putBoolean(KEY_LAST_PARSE_HAS_ASK, observation.ask != null)
            .putInt(KEY_EVENT_COUNT, prefs.getInt(KEY_EVENT_COUNT, 0) + 1)
            .apply()

        val previousBid = prefs.getString(KEY_BID, "").orEmpty().toDoubleOrNull()
        val previousAsk = prefs.getString(KEY_ASK, "").orEmpty().toDoubleOrNull()
        val hasNewQuote = Mt5QuoteFreshness.isNewExecutableQuote(
            symbol = observation.symbol,
            bid = observation.bid,
            ask = observation.ask,
            previousBid = previousBid,
            previousAsk = previousAsk
        )
        val editor = prefs.edit()
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
        if (packageName.isBlank()) return false
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
        mainHandler.removeCallbacks(activeWindowPoll)
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
        const val KEY_LAST_POLL_AT = "last_active_window_poll_at"
        const val KEY_LAST_POLL_PACKAGE = "last_active_window_poll_package"
        const val KEY_LAST_POLL_ROOT_NULL = "last_active_window_poll_root_null"
        private const val MIN_PROCESS_INTERVAL_MS = 300L
        private const val ACTIVE_WINDOW_POLL_INTERVAL_MS = 700L
        private const val MAX_NODES = 350
    }
}
