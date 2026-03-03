package com.openclaw.mobile.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_FILE = "openclaw_secure_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_ENVIRONMENT_READY = "environment_ready"
        private const val KEY_OPENCLAW_PORT = "openclaw_port"
        private const val KEY_A11Y_PORT = "a11y_port"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 保存 API Key
     */
    fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, apiKey).apply()
        Timber.d("API Key saved")
    }

    /**
     * 获取 API Key
     */
    fun getApiKey(): String? {
        return encryptedPrefs.getString(KEY_API_KEY, null)
    }

    /**
     * 清除 API Key
     */
    fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
        Timber.d("API Key cleared")
    }

    /**
     * 检查是否为首次启动
     */
    fun isFirstLaunch(): Boolean {
        return encryptedPrefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 标记已完成首次启动
     */
    fun markFirstLaunchComplete() {
        encryptedPrefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    /**
     * 检查环境是否已准备好
     */
    fun isEnvironmentReady(): Boolean {
        return encryptedPrefs.getBoolean(KEY_ENVIRONMENT_READY, false)
    }

    /**
     * 标记环境已准备好
     */
    fun markEnvironmentReady() {
        encryptedPrefs.edit().putBoolean(KEY_ENVIRONMENT_READY, true).apply()
    }

    /**
     * 保存 OpenClaw 端口
     */
    fun saveOpenClawPort(port: Int) {
        encryptedPrefs.edit().putInt(KEY_OPENCLAW_PORT, port).apply()
    }

    /**
     * 获取 OpenClaw 端口
     * OpenClaw Gateway 默认端口 18789
     */
    fun getOpenClawPort(): Int {
        return encryptedPrefs.getInt(KEY_OPENCLAW_PORT, 18789)
    }

    /**
     * 保存 A11y Bridge 端口
     */
    fun saveA11yPort(port: Int) {
        encryptedPrefs.edit().putInt(KEY_A11Y_PORT, port).apply()
    }

    /**
     * 获取 A11y Bridge 端口
     */
    fun getA11yPort(): Int {
        return encryptedPrefs.getInt(KEY_A11Y_PORT, 7333)
    }

    /**
     * 清除所有配置
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        Timber.d("All secure storage cleared")
    }
}
