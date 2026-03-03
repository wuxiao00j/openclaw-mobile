package com.openclaw.mobile.utils

import timber.log.Timber
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortManager @Inject constructor() {

    companion object {
        // OpenClaw Gateway 默认端口 18789
        const val DEFAULT_OPENCLAW_PORT = 18789
        const val DEFAULT_A11Y_PORT = 7333
        const val MAX_PORT_RETRY = 10
    }

    private val reservedPorts = mutableSetOf<Int>()

    /**
     * 查找可用的 OpenClaw 端口
     */
    fun findOpenClawPort(): Int {
        return findAvailablePort(DEFAULT_OPENCLAW_PORT)
    }

    /**
     * 查找可用的 A11y Bridge 端口
     */
    fun findA11yPort(): Int {
        return findAvailablePort(DEFAULT_A11Y_PORT)
    }

    /**
     * 查找从 basePort 开始的第一个可用端口
     */
    fun findAvailablePort(basePort: Int): Int {
        for (port in basePort until basePort + MAX_PORT_RETRY) {
            if (port !in reservedPorts && isPortAvailable(port)) {
                reservedPorts.add(port)
                Timber.d("Found available port: $port")
                return port
            }
        }
        throw NoAvailablePortException("No available port found between $basePort and ${basePort + MAX_PORT_RETRY}")
    }

    /**
     * 释放已占用的端口记录
     */
    fun releasePort(port: Int) {
        reservedPorts.remove(port)
        Timber.d("Released port: $port")
    }

    /**
     * 检查端口是否可用
     */
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { 
                it.reuseAddress = true
                true 
            }
        } catch (e: Exception) {
            Timber.w("Port $port is not available: ${e.message}")
            false
        }
    }

    class NoAvailablePortException(message: String) : Exception(message)
}
