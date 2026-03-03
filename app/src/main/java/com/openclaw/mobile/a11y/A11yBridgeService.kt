package com.openclaw.mobile.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openclaw.mobile.config.SecureStorage
import dagger.hilt.android.AndroidEntryPoint
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class A11yBridgeService : AccessibilityService() {

    @Inject
    lateinit var secureStorage: SecureStorage

    private var httpServer: A11yHttpServer? = null
    private lateinit var screenshotHelper: ScreenshotHelper
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var instance: A11yBridgeService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        screenshotHelper = ScreenshotHelper(this)
        Timber.i("A11yBridgeService connected")
        
        // 启动 HTTP 服务器
        startHttpServer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 将事件转发给感兴趣的组件
        httpServer?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Timber.w("A11yBridgeService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        instance = null
        stopHttpServer()
        Timber.i("A11yBridgeService unbound")
        return super.onUnbind(intent)
    }

    private fun startHttpServer() {
        try {
            val port = secureStorage.getA11yPort()
            httpServer = A11yHttpServer(port, this)
            httpServer?.start()
            Timber.i("HTTP server started on port $port")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start HTTP server")
        }
    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
        Timber.i("HTTP server stopped")
    }

    /**
     * 获取当前窗口根节点
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInWindow
    }

    /**
     * 执行点击操作
     */
    fun performClick(x: Float, y: Float): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            dispatchGesture(gesture, null, null)
        } else {
            // 低版本通过查找节点点击
            val node = findNodeAt(x.toInt(), y.toInt())
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }
    }

    /**
     * 执行滑动操作
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            dispatchGesture(gesture, null, null)
        } else {
            false
        }
    }

    /**
     * 输入文本
     */
    fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * 在指定坐标查找节点
     */
    fun findNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val root = rootInWindow ?: return null
        return findNodeAtRecursive(root, x, y)
    }

    private fun findNodeAtRecursive(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        if (rect.contains(x, y)) {
            // 优先返回可点击的子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findNodeAtRecursive(child, x, y)
                if (found != null) {
                    return found
                }
            }
            return node
        }
        return null
    }

    /**
     * 获取屏幕元素树（JSON 格式）
     */
    fun getElementTree(): String {
        val root = rootInWindow
        return if (root != null) {
            NodeInfoConverter.toJson(root)
        } else {
            "{\"error\": \"No window content available\"}"
        }
    }

    /**
     * 设置 MediaProjection（用于截图）
     */
    fun setMediaProjection(projection: MediaProjection?) {
        screenshotHelper.setMediaProjection(projection)
        Timber.i("MediaProjection ${if (projection != null) "set" else "cleared"}")
    }

    /**
     * 检查是否有截图权限
     */
    fun canTakeScreenshot(): Boolean {
        return screenshotHelper.canTakeScreenshot()
    }
    
    /**
     * 获取截图 Helper
     */
    fun getScreenshotHelper(): ScreenshotHelper = screenshotHelper

    /**
     * HTTP Server 内部类
     */
    private class A11yHttpServer(
        port: Int,
        private val service: A11yBridgeService
    ) : NanoHTTPD(port) {

        private val eventListeners = mutableListOf<(AccessibilityEvent) -> Unit>()

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            Timber.d("HTTP $method $uri")

            return when {
                uri == "/api/health" -> newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"ok\"}")
                uri == "/api/screen" && method == Method.GET -> handleGetScreen()
                uri == "/api/tree" && method == Method.GET -> handleGetTree()
                uri == "/api/tap" && method == Method.POST -> handleTap(session)
                uri == "/api/swipe" && method == Method.POST -> handleSwipe(session)
                uri == "/api/input" && method == Method.POST -> handleInput(session)
                uri == "/api/screenshot" && method == Method.GET -> handleScreenshot()
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\": \"Not found\"}")
            }
        }

        private fun handleGetScreen(): Response {
            // 获取真实屏幕尺寸
            val displayMetrics = service.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", 
                "{\"width\": $width, \"height\": $height, \"density\": $density}")
        }
        
        private fun handleScreenshot(): Response {
            return if (!service.canTakeScreenshot()) {
                newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", 
                    "{\"error\": \"Screenshot permission not granted. Please request MediaProjection first.\"}")
            } else {
                // 同步截图（简化版，实际应该使用协程）
                try {
                    val displayMetrics = service.resources.displayMetrics
                    var result: String? = null
                    var error: String? = null
                    
                    kotlinx.coroutines.runBlocking {
                        val screenshotResult = service.getScreenshotHelper().takeScreenshot(
                            displayMetrics.widthPixels,
                            displayMetrics.heightPixels,
                            displayMetrics.densityDpi
                        )
                        screenshotResult.onSuccess { result = it }
                        screenshotResult.onFailure { error = it.message }
                    }
                    
                    if (result != null) {
                        newFixedLengthResponse(Response.Status.OK, "application/json", 
                            "{\"success\": true, \"image\": \"data:image/png;base64,$result\"}")
                    } else {
                        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", 
                            "{\"error\": \"$error\"}")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", 
                        "{\"error\": \"${e.message}\"}")
                }
            }
        }

        private fun handleGetTree(): Response {
            val tree = service.getElementTree()
            return newFixedLengthResponse(Response.Status.OK, "application/json", tree)
        }

        private fun handleTap(session: IHTTPSession): Response {
            val params = parseBody(session)
            val x = params["x"]?.toFloatOrNull()
            val y = params["y"]?.toFloatOrNull()

            return if (x != null && y != null) {
                val success = service.performClick(x, y)
                newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"success\": $success}")
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", 
                    "{\"error\": \"Missing x or y parameter\"}")
            }
        }

        private fun handleSwipe(session: IHTTPSession): Response {
            val params = parseBody(session)
            val startX = params["startX"]?.toFloatOrNull()
            val startY = params["startY"]?.toFloatOrNull()
            val endX = params["endX"]?.toFloatOrNull()
            val endY = params["endY"]?.toFloatOrNull()
            val duration = params["duration"]?.toLongOrNull() ?: 300

            return if (startX != null && startY != null && endX != null && endY != null) {
                val success = service.performSwipe(startX, startY, endX, endY, duration)
                newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"success\": $success}")
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", 
                    "{\"error\": \"Missing swipe parameters\"}")
            }
        }

        private fun handleInput(session: IHTTPSession): Response {
            val params = parseBody(session)
            val x = params["x"]?.toIntOrNull()
            val y = params["y"]?.toIntOrNull()
            val text = params["text"]

            return if (x != null && y != null && text != null) {
                val node = service.findNodeAt(x, y)
                val success = if (node != null) {
                    service.inputText(node, text)
                } else false
                newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"success\": $success}")
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", 
                    "{\"error\": \"Missing input parameters\"}")
            }
        }

        private fun parseBody(session: IHTTPSession): Map<String, String> {
            val map = HashMap<String, String>()
            session.parseBody(map)
            
            return try {
                val body = map["postData"] ?: return emptyMap()
                com.google.gson.Gson().fromJson(body, Map::class.java) as Map<String, String>
            } catch (e: Exception) {
                emptyMap()
            }
        }

        fun onAccessibilityEvent(event: AccessibilityEvent) {
            eventListeners.forEach { it.invoke(event) }
        }
    }
}
