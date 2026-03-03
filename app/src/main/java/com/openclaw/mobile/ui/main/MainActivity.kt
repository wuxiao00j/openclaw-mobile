package com.openclaw.mobile.ui.main

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.openclaw.mobile.R
import com.openclaw.mobile.databinding.ActivityMainBinding
import com.openclaw.mobile.service.ExtractionService
import com.openclaw.mobile.service.OpenClawService
import com.openclaw.mobile.ui.config.ConfigDialogFragment
import com.openclaw.mobile.utils.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var permissionManager: PermissionManager

    private var openClawService: OpenClawService? = null
    private var serviceBound = false
    
    private lateinit var extractionReceiver: BroadcastReceiver

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OpenClawService.LocalBinder
            openClawService = binder.getService()
            openClawService?.setCallback(serviceCallback)
            serviceBound = true
            updateUIForServiceState(openClawService?.getState())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            openClawService = null
            serviceBound = false
        }
    }

    private val serviceCallback = object : OpenClawService.ServiceCallback {
        override fun onStateChanged(state: OpenClawService.ServiceState) {
            runOnUiThread {
                updateUIForServiceState(state)
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 解压进度广播接收器
     */
    private fun createExtractionReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ExtractionService.ACTION_EXTRACTION_PROGRESS -> {
                        val progress = intent.getIntExtra(ExtractionService.EXTRA_PROGRESS, 0)
                        val estimatedSeconds = intent.getIntExtra(ExtractionService.EXTRA_ESTIMATED_SECONDS, 0)
                        updateExtractionProgress(progress, estimatedSeconds)
                    }
                    ExtractionService.ACTION_EXTRACTION_COMPLETE -> {
                        onExtractionComplete()
                    }
                    ExtractionService.ACTION_EXTRACTION_ERROR -> {
                        val errorMessage = intent.getStringExtra(ExtractionService.EXTRA_ERROR_MESSAGE) ?: "Unknown error"
                        onExtractionError(errorMessage)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractionReceiver = createExtractionReceiver()
        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // 注册解压进度广播
        val filter = IntentFilter().apply {
            addAction(ExtractionService.ACTION_EXTRACTION_PROGRESS)
            addAction(ExtractionService.ACTION_EXTRACTION_COMPLETE)
            addAction(ExtractionService.ACTION_EXTRACTION_ERROR)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(extractionReceiver, filter)
        
        checkPermissions()
        bindService()
    }

    override fun onPause() {
        super.onPause()
        // 注销广播
        LocalBroadcastManager.getInstance(this).unregisterReceiver(extractionReceiver)
        
        if (serviceBound) {
            openClawService?.setCallback(null)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun setupUI() {
        binding.apply {
            btnOpenPanel.setOnClickListener {
                openClawService?.let { service ->
                    if (service.getState() == OpenClawService.ServiceState.RUNNING) {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                            "http://localhost:${viewModel.openClawPort.value}"
                        ))
                        startActivity(intent)
                    }
                }
            }

            btnViewLogs.setOnClickListener {
                startActivity(Intent(this@MainActivity, com.openclaw.mobile.ui.logs.LogsActivity::class.java))
            }

            btnRestartService.setOnClickListener {
                startService(Intent(this@MainActivity, OpenClawService::class.java).apply {
                    action = OpenClawService.ACTION_RESTART
                })
            }

            btnEditApiKey.setOnClickListener {
                ConfigDialogFragment().show(supportFragmentManager, "config")
            }
            
            // 添加设置入口（长按状态栏或添加按钮）
            tvStatus.setOnLongClickListener {
                startActivity(Intent(this@MainActivity, com.openclaw.mobile.ui.settings.SettingsActivity::class.java))
                true
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.extractionState.collect { state ->
                        updateUIForExtractionState(state)
                    }
                }

                launch {
                    viewModel.openClawPort.collect { port ->
                        // 端口更新
                    }
                }

                launch {
                    viewModel.apiKey.collect { apiKey ->
                        binding.tvApiKeyStatus.text = if (apiKey != null) {
                            "已配置 (${apiKey.take(8)}...${apiKey.takeLast(4)})"
                        } else {
                            "未配置"
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        when {
            !permissionManager.isAccessibilityServiceEnabled() -> {
                showA11yPermissionDialog()
            }
            !permissionManager.isIgnoringBatteryOptimizations() -> {
                showBatteryOptimizationDialog()
            }
            !viewModel.isEnvironmentReady() -> {
                startExtraction()
            }
            viewModel.getApiKey() == null -> {
                showConfigDialog()
            }
            else -> {
                startOpenClawService()
            }
        }
    }

    private fun showA11yPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_a11y_title)
            .setMessage(R.string.permission_a11y_message)
            .setCancelable(false)
            .setPositiveButton(R.string.permission_a11y_go_settings) { _, _ ->
                permissionManager.openAccessibilitySettings(this)
            }
            .show()
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_battery_title)
            .setMessage(R.string.permission_battery_message)
            .setCancelable(false)
            .setPositiveButton(R.string.permission_battery_go_settings) { _, _ ->
                permissionManager.requestIgnoreBatteryOptimizations(this)
            }
            .setNegativeButton("跳过") { _, _ ->
                checkPermissions()
            }
            .show()
    }

    private fun startExtraction() {
        binding.apply {
            layoutExtraction.visibility = View.VISIBLE
            layoutMain.visibility = View.GONE
            progressExtraction.progress = 0
            tvExtractionStatus.text = getString(R.string.extraction_message)
        }

        // 启动解压服务
        val intent = Intent(this, ExtractionService::class.java).apply {
            action = ExtractionService.ACTION_START_EXTRACTION
            putExtra(ExtractionService.EXTRA_ASSET_NAME, "environment.zip")
            putExtra(ExtractionService.EXTRA_TARGET_DIR, filesDir.absolutePath)
        }
        startService(intent)
    }
    
    /**
     * 更新解压进度（来自广播）
     */
    private fun updateExtractionProgress(progress: Int, estimatedSeconds: Int) {
        runOnUiThread {
            binding.progressExtraction.progress = progress
            binding.tvExtractionStatus.text = getString(
                R.string.extraction_time_remaining,
                estimatedSeconds
            )
        }
    }
    
    /**
     * 解压完成
     */
    private fun onExtractionComplete() {
        runOnUiThread {
            binding.layoutExtraction.visibility = View.GONE
            binding.layoutMain.visibility = View.VISIBLE
            checkPermissions()
        }
    }
    
    /**
     * 解压错误
     */
    private fun onExtractionError(errorMessage: String) {
        runOnUiThread {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun showConfigDialog() {
        ConfigDialogFragment().show(supportFragmentManager, "config")
    }

    private fun startOpenClawService() {
        val intent = Intent(this, OpenClawService::class.java).apply {
            action = OpenClawService.ACTION_START
        }
        startService(intent)
    }

    private fun bindService() {
        val intent = Intent(this, OpenClawService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateUIForExtractionState(state: MainViewModel.ExtractionState) {
        // 现在使用广播接收进度，此方法保留给 ViewModel 的其他用途
        when (state) {
            is MainViewModel.ExtractionState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun updateUIForServiceState(state: OpenClawService.ServiceState?) {
        val statusText = when (state) {
            OpenClawService.ServiceState.RUNNING -> getString(R.string.status_running)
            OpenClawService.ServiceState.STARTING -> getString(R.string.status_initializing)
            OpenClawService.ServiceState.ERROR -> getString(R.string.status_error)
            else -> getString(R.string.status_stopped)
        }

        val statusColor = when (state) {
            OpenClawService.ServiceState.RUNNING -> R.color.green_500
            OpenClawService.ServiceState.STARTING -> R.color.teal_200
            OpenClawService.ServiceState.ERROR -> R.color.red_500
            else -> R.color.gray_500
        }

        binding.apply {
            tvStatus.text = "状态: $statusText"
            tvStatus.setTextColor(getColor(statusColor))
            
            btnOpenPanel.isEnabled = state == OpenClawService.ServiceState.RUNNING
            btnRestartService.isEnabled = state != OpenClawService.ServiceState.STARTING
        }
    }
}
