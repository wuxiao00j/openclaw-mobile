package com.openclaw.mobile.a11y

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * 截图辅助类
 */
class ScreenshotHelper(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "A11yScreenshot"
        private const val SCREENSHOT_TIMEOUT = 5000L
    }

    /**
     * 设置 MediaProjection
     */
    fun setMediaProjection(projection: MediaProjection?) {
        mediaProjection = projection
        Timber.i("MediaProjection ${if (projection != null) "set" else "cleared"}")
    }

    /**
     * 检查是否有截图权限
     */
    fun canTakeScreenshot(): Boolean {
        return mediaProjection != null
    }

    /**
     * 获取截图（Base64 编码）
     */
    suspend fun takeScreenshot(width: Int, height: Int, density: Int): Result<String> = withContext(Dispatchers.IO) {
        if (mediaProjection == null) {
            return@withContext Result.failure(IllegalStateException("MediaProjection not available"))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return@withContext Result.failure(UnsupportedOperationException("Screenshot requires API 21+"))
        }

        try {
            val base64String = captureScreen(width, height, density)
            Result.success(base64String)
        } catch (e: Exception) {
            Timber.e(e, "Screenshot failed")
            Result.failure(e)
        }
    }

    /**
     * 实际的截图实现
     */
    private suspend fun captureScreen(width: Int, height: Int, density: Int): String =
        suspendCancellableCoroutine { continuation ->
            val projection = mediaProjection ?: run {
                continuation.resumeWith(Result.failure(IllegalStateException("MediaProjection is null")))
                return@suspendCancellableCoroutine
            }

            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                width, height,
                PixelFormat.RGBA_8888,
                2
            )

            // 创建 VirtualDisplay
            virtualDisplay = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                Handler(Looper.getMainLooper())
            )

            // 等待一帧
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image)
                        image.close()

                        val base64 = bitmapToBase64(bitmap)
                        releaseResources()

                        continuation.resume(base64)
                    } else {
                        releaseResources()
                        continuation.resumeWith(Result.failure(RuntimeException("Failed to acquire image")))
                    }
                } catch (e: Exception) {
                    releaseResources()
                    continuation.resumeWith(Result.failure(e))
                }
            }, 100)

            // 超时处理
            Handler(Looper.getMainLooper()).postDelayed({
                if (continuation.isActive) {
                    releaseResources()
                    continuation.resumeWith(Result.failure(TimeoutException("Screenshot timeout")))
                }
            }, SCREENSHOT_TIMEOUT)
        }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    /**
     * 将 Bitmap 转换为 Base64 字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 释放资源
     */
    private fun releaseResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /**
     * 清理所有资源
     */
    fun cleanup() {
        releaseResources()
        mediaProjection?.stop()
        mediaProjection = null
    }

    class TimeoutException(message: String) : Exception(message)
}
