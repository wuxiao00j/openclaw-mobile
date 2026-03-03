package com.openclaw.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationCompat
import com.openclaw.mobile.R
import com.openclaw.mobile.config.ConfigManager
import com.openclaw.mobile.config.SecureStorage
import com.openclaw.mobile.ui.main.MainActivity
import com.openclaw.mobile.utils.PortManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class OpenClawService : Service() {

    @Inject
    lateinit var secureStorage: SecureStorage

    @Inject
    lateinit var configManager: ConfigManager

    @Inject
    lateinit var portManager: PortManager

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private var openClawProcess: Process? = null
    private var healthCheckJob: Job? = null
    private var serviceState = ServiceState.STOPPED

    private val binder = LocalBinder()

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val ACTION_RESTART = "action_restart"
        
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "openclaw_service_channel"
        
        private const val HEALTH_CHECK_INTERVAL = 5000L
        private const val STARTUP_TIMEOUT = 60000L
        
        // OpenClaw 默认端口是 18789
        const val DEFAULT_OPENCLAW_PORT = 18789
    }

    enum class ServiceState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    interface ServiceCallback {
        fun onStateChanged(state: ServiceState)
        fun onError(message: String)
    }

    private var callback: ServiceCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): OpenClawService = this@OpenClawService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.i("OpenClawService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startOpenClaw()
            ACTION_STOP -> stopOpenClaw()
            ACTION_RESTART -> restartOpenClaw()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOpenClaw()
        serviceScope.cancel()
    }

    fun setCallback(callback: ServiceCallback?) {
        this.callback = callback
    }

    fun getState(): ServiceState = serviceState

    private fun startOpenClaw() {
        if (serviceState == ServiceState.RUNNING || serviceState == ServiceState.STARTING) {
            Timber.w("OpenClaw is already running or starting")
            return
        }

        updateState(ServiceState.STARTING)
        startForeground(NOTIFICATION_ID, createServiceNotification())

        serviceScope.launch {
            try {
                // 1. 查找可用端口
                val openClawPort = portManager.findOpenClawPort()
                val a11yPort = secureStorage.getA11yPort()
                
                secureStorage.saveOpenClawPort(openClawPort)
                Timber.i("Using ports - OpenClaw: $openClawPort, A11y: $a11yPort")

                // 2. 准备环境变量
                val baseDir = filesDir
                val envVars = configManager.generateEnvironmentVariables(baseDir)
                
                // 3. 启动 OpenClaw 进程（Node.js）
                openClawProcess = startNodeProcess(baseDir, openClawPort, envVars)

                // 4. 等待服务就绪
                val isReady = waitForServiceReady(openClawPort, STARTUP_TIMEOUT)
                
                if (isReady) {
                    updateState(ServiceState.RUNNING)
                    startHealthCheck(openClawPort)
                    openInBrowser(openClawPort)
                } else {
                    throw RuntimeException("OpenClaw failed to start within timeout")
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to start OpenClaw")
                updateState(ServiceState.ERROR)
                callback?.onError(e.message ?: "Unknown error")
                stopOpenClaw()
            }
        }
    }

    private fun startNodeProcess(baseDir: File, port: Int, envVars: Map<String, String>): Process {
        val nodeExecutable = File(baseDir, "nodejs/bin/node")
        val openClawDir = File(baseDir, "openclaw")
        
        // OpenClaw 可能的入口点
        val openClawBin = File(openClawDir, "node_modules/openclaw/bin/openclaw")
        val openClawDist = File(openClawDir, "node_modules/openclaw/dist/index.js")
        
        if (!nodeExecutable.exists()) {
            throw RuntimeException("Node.js not found. Please complete extraction first.")
        }

        val cmdList = mutableListOf(nodeExecutable.absolutePath)
        
        // 确定入口点
        when {
            openClawBin.exists() -> {
                cmdList.add(openClawBin.absolutePath)
                cmdList.add("gateway")
            }
            openClawDist.exists() -> {
                cmdList.add(openClawDist.absolutePath)
                cmdList.add("gateway")
            }
            else -> throw RuntimeException("OpenClaw entry point not found")
        }
        
        // OpenClaw 参数
        cmdList.add("--port")
        cmdList.add(port.toString())
        cmdList.add("--verbose")

        val processBuilder = ProcessBuilder(cmdList).apply {
            directory(openClawDir)
            environment().apply {
                putAll(envVars)
                put("PORT", port.toString())
                put("HOME", openClawDir.absolutePath)
            }
            redirectErrorStream(true)
        }

        return processBuilder.start().also {
            Timber.i("OpenClaw process started with PID: ${it.pid()}")
            Timber.i("Command: ${cmdList.joinToString(" ")}")
            
            // 启动日志收集
            serviceScope.launch {
                it.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Timber.tag("OpenClaw").d(line)
                    }
                }
            }
        }
    }

    private suspend fun waitForServiceReady(port: Int, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isServiceHealthy(port)) {
                Timber.i("OpenClaw service is ready on port $port")
                return true
            }
            delay(500)
        }
        
        return false
    }

    private fun isServiceHealthy(port: Int): Boolean {
        return try {
            // OpenClaw Gateway 健康检查端点
            val request = Request.Builder()
                .url("http://localhost:$port/health")
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            // 备用：尝试根路径
            try {
                val fallbackRequest = Request.Builder()
                    .url("http://localhost:$port/")
                    .build()
                okHttpClient.newCall(fallbackRequest).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun startHealthCheck(port: Int) {
        healthCheckJob?.cancel()
        healthCheckJob = serviceScope.launch {
            while (serviceState == ServiceState.RUNNING) {
                delay(HEALTH_CHECK_INTERVAL)
                
                if (!isServiceHealthy(port)) {
                    Timber.w("Health check failed, service may be down")
                    updateState(ServiceState.ERROR)
                    callback?.onError("Service health check failed")
                    // 尝试自动重启
                    restartOpenClaw()
                    break
                }
            }
        }
    }

    private fun stopOpenClaw() {
        healthCheckJob?.cancel()
        
        openClawProcess?.let { process ->
            try {
                process.destroy()
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
                Timber.i("OpenClaw process stopped")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping OpenClaw process")
            }
            openClawProcess = null
        }

        updateState(ServiceState.STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun restartOpenClaw() {
        stopOpenClaw()
        serviceScope.launch {
            delay(1000)
            startOpenClaw()
        }
    }

    private fun openInBrowser(port: Int) {
        val url = "http://localhost:$port"
        
        try {
            // 优先使用 Chrome Custom Tabs
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
            
            Timber.i("Opened OpenClaw UI in Chrome Custom Tabs: $url")
        } catch (e: Exception) {
            // 降级到系统浏览器
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            
            Timber.i("Opened OpenClaw UI in system browser: $url")
        }
    }

    private fun updateState(newState: ServiceState) {
        serviceState = newState
        callback?.onStateChanged(newState)
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 OpenClaw 运行状态"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OpenClawService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(getString(R.string.notification_service_content))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createServiceNotification())
    }
}
