package com.openclaw.mobile.config

import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    private val secureStorage: SecureStorage
) {

    companion object {
        private const val OPENCLAW_DIR = "openclaw"
        private const val CONFIG_DIR = ".config"
    }

    /**
     * 配置 OpenClaw 的 API Key
     */
    fun configureApiKey(baseDir: File, apiKey: String): Result<Unit> {
        return try {
            // 1. 保存到安全存储
            secureStorage.saveApiKey(apiKey)
            
            // 2. 写入 OpenClaw 配置文件
            val configDir = File(baseDir, "$OPENCLAW_DIR/$CONFIG_DIR")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            
            // TODO: 根据 OpenClaw 的实际配置格式写入
            // 目前先保存到安全存储，后续通过 CLI 命令注入
            
            Timber.d("API Key configured successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to configure API Key")
            Result.failure(e)
        }
    }

    /**
     * 获取当前 API Key
     */
    fun getApiKey(): String? {
        return secureStorage.getApiKey()
    }

    /**
     * 生成 OpenClaw 启动环境变量
     */
    fun generateEnvironmentVariables(baseDir: File): Map<String, String> {
        return mapOf(
            "OPENCLAW_HOME" to File(baseDir, OPENCLAW_DIR).absolutePath,
            "OPENCLAW_PORT" to secureStorage.getOpenClawPort().toString(),
            "A11Y_BRIDGE_PORT" to secureStorage.getA11yPort().toString(),
            "NODE_ENV" to "production"
        ).apply {
            Timber.d("Environment variables generated: $this")
        }
    }

    /**
     * 清除所有配置
     */
    fun clearConfig() {
        secureStorage.clearApiKey()
        Timber.d("Config cleared")
    }
}
