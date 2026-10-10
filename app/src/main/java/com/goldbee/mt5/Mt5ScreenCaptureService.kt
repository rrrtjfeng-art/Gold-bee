package com.goldbee.mt5

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-approved screen capture for local OCR. It never saves screenshots or raw OCR text.
 * Only parsed market-related fields are persisted to private app preferences.
 */
class Mt5ScreenCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val processingFrame = AtomicBoolean(false)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastOcrAt = 0L
    private var resourcesReleased = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseCaptureResources(stopProjection = false)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startCaptureNotification()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startCapture(resultCode, resultData)
        } catch (_: Exception) {
            getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_OCR_STATUS, "屏幕读取启动失败；请重新授权并重试。")
                .apply()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCaptureNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Gold Bee 屏幕识别",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "用户主动开启的 MT5 屏幕文字识别" }
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Gold Bee 屏幕识别正在运行")
            .setContentText("本机提取可见交易文字；不会保存截图或原始文字。")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("Screen capture permission was not granted")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader -> processNextFrame(reader) }, mainHandler)

        projection?.registerCallback(projectionCallback, mainHandler)
        virtualDisplay = projection?.createVirtualDisplay(
            "GoldBee-MT5-OCR",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        ) ?: throw IllegalStateException("Unable to create screen capture display")

        getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_OCR_STATUS, "屏幕 OCR 已启动；请在系统提示中选择 MT5 窗口（如果可选）。")
            .apply()
    }

    private fun processNextFrame(reader: ImageReader) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOcrAt < MIN_OCR_INTERVAL_MS || !processingFrame.compareAndSet(false, true)) {
            reader.acquireLatestImage()?.close()
            return
        }
        lastOcrAt = now
        val image = reader.acquireLatestImage()
        if (image == null) {
            processingFrame.set(false)
            return
        }

        val bitmap = try {
            imageToBitmap(image)
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
        if (bitmap == null) {
            processingFrame.set(false)
            return
        }

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { line -> line.text }
                }
                val observation = Mt5ScreenObservationParser.parse(lines)
                saveObservation(observation)
            }
            .addOnFailureListener {
                getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_OCR_STATUS, "OCR 暂时无法识别当前画面；请调整图表缩放或文字大小。")
                    .apply()
            }
            .addOnCompleteListener {
                bitmap.recycle()
                processingFrame.set(false)
            }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun saveObservation(observation: Mt5ScreenObservation) {
        getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, System.currentTimeMillis())
            .putString(Mt5ScreenAccessibilityService.KEY_SYMBOL, observation.symbol.orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_TIMEFRAME, observation.timeframe.orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_DIRECTION, observation.direction.orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_ENTRY, observation.entry?.toString().orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_SL, observation.stopLoss?.toString().orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_TP, observation.takeProfit?.toString().orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_BID, observation.bid?.toString().orEmpty())
            .putString(Mt5ScreenAccessibilityService.KEY_ASK, observation.ask?.toString().orEmpty())
            .putInt(Mt5ScreenAccessibilityService.KEY_TEXT_COUNT, observation.visibleTextCount)
            .putString(Mt5ScreenAccessibilityService.KEY_SOURCE, "SCREEN_OCR")
            .putString(
                KEY_OCR_STATUS,
                if (observation.hasTradeLevels) "已识别方向与 Entry/SL/TP；仍需人工核对。"
                else "已读取屏幕文字，但未能完整识别信号价位；缺少价位时禁止跟随。"
            )
            .apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseCaptureResources(stopProjection = true)
        if (::recognizer.isInitialized) recognizer.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun releaseCaptureResources(stopProjection: Boolean) {
        if (resourcesReleased) return
        resourcesReleased = true
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.unregisterCallback(projectionCallback)
        if (stopProjection) projection?.stop()
        projection = null
    }

    companion object {
        const val ACTION_STOP = "com.goldbee.mt5.STOP_SCREEN_OCR"
        const val EXTRA_RESULT_CODE = "screen_capture_result_code"
        const val EXTRA_RESULT_DATA = "screen_capture_result_data"
        const val KEY_OCR_STATUS = "ocr_status"
        private const val CHANNEL_ID = "gold_bee_screen_ocr"
        private const val NOTIFICATION_ID = 7402
        private const val MIN_OCR_INTERVAL_MS = 1200L
    }
}
