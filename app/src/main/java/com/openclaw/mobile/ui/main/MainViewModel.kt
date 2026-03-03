package com.openclaw.mobile.ui.main

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
nimport androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.mobile.config.ConfigManager
import com.openclaw.mobile.config.SecureStorage
import com.openclaw.mobile.service.ExtractionService
import com.openclaw.mobile.utils.PortManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val configManager: ConfigManager,
    private val portManager: PortManager
) : ViewModel() {

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState

    // OpenClaw Gateway 默认端口 18789
    private val _openClawPort = MutableStateFlow(18789)
    val openClawPort: StateFlow<Int> = _openClawPort

    private val _a11yPort = MutableStateFlow(7333)
    val a11yPort: StateFlow<Int> = _a11yPort

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String?> = _apiKey

    init {
        viewModelScope.launch {
            _openClawPort.value = secureStorage.getOpenClawPort()
            _a11yPort.value = secureStorage.getA11yPort()
            _apiKey.value = secureStorage.getApiKey()
        }
    }

    sealed class ExtractionState {
        object Idle : ExtractionState()
        data class Progress(val percent: Int, val estimatedSeconds: Int) : ExtractionState()
        object Complete : ExtractionState()
        data class Error(val message: String) : ExtractionState()
    }

    fun isEnvironmentReady(): Boolean {
        return secureStorage.isEnvironmentReady()
    }

    fun getApiKey(): String? {
        return secureStorage.getApiKey()
    }

    fun startExtraction(context: Context) {
        _extractionState.value = ExtractionState.Progress(0, 30)

        // 启动解压服务
        val intent = Intent(context, ExtractionService::class.java).apply {
            action = ExtractionService.ACTION_START_EXTRACTION
            putExtra(ExtractionService.EXTRA_ASSET_NAME, "environment.zip")
            putExtra(ExtractionService.EXTRA_TARGET_DIR, context.filesDir.absolutePath)
        }
        context.startService(intent)

        // 模拟进度更新（实际应从服务接收）
        viewModelScope.launch {
            var progress = 0
            while (progress < 100) {
                kotlinx.coroutines.delay(300)
                progress += 5
                _extractionState.value = ExtractionState.Progress(
                    percent = progress,
                    estimatedSeconds = ((100 - progress) / 5)
                )
            }
            _extractionState.value = ExtractionState.Complete
        }
    }

    fun configureApiKey(apiKey: String, context: Context) {
        viewModelScope.launch {
            val result = configManager.configureApiKey(context.filesDir, apiKey)
            if (result.isSuccess) {
                _apiKey.value = apiKey
            }
        }
    }

    fun refreshPorts() {
        viewModelScope.launch {
            _openClawPort.value = secureStorage.getOpenClawPort()
            _a11yPort.value = secureStorage.getA11yPort()
        }
    }
}
