package com.openclaw.mobile.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.openclaw.mobile.BuildConfig
import com.openclaw.mobile.databinding.ActivitySettingsBinding
import com.openclaw.mobile.utils.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    @Inject
    lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSettings()
        loadCurrentSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "设置"
        }
    }

    private fun setupSettings() {
        // 无障碍服务
        binding.cardAccessibility.setOnClickListener {
            permissionManager.openAccessibilitySettings(this)
        }

        // 电池优化
        binding.cardBattery.setOnClickListener {
            permissionManager.requestIgnoreBatteryOptimizations(this)
        }

        // 悬浮窗权限
        binding.cardOverlay.setOnClickListener {
            if (!permissionManager.canDrawOverlays()) {
                permissionManager.requestOverlayPermission(this)
            }
        }

        // 清除缓存
        binding.cardClearCache.setOnClickListener {
            clearCache()
        }

        // 重新解压环境
        binding.cardReextract.setOnClickListener {
            reextractEnvironment()
        }

        // 关于
        binding.cardAbout.setOnClickListener {
            openAbout()
        }

        // 帮助文档
        binding.cardHelp.setOnClickListener {
            openHelp()
        }
    }

    private fun loadCurrentSettings() {
        // 加载版本信息
        binding.tvVersion.text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        // 加载权限状态
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        // 无障碍服务状态
        val a11yEnabled = permissionManager.isAccessibilityServiceEnabled()
        binding.tvAccessibilityStatus.text = if (a11yEnabled) "已开启" else "未开启"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (a11yEnabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )

        // 电池优化状态
        val batteryIgnored = permissionManager.isIgnoringBatteryOptimizations()
        binding.tvBatteryStatus.text = if (batteryIgnored) "已忽略" else "未忽略"
        binding.tvBatteryStatus.setTextColor(
            getColor(if (batteryIgnored) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
        )

        // 悬浮窗状态
        val overlayEnabled = permissionManager.canDrawOverlays()
        binding.tvOverlayStatus.text = if (overlayEnabled) "已开启" else "未开启"
        binding.tvOverlayStatus.setTextColor(
            getColor(if (overlayEnabled) android.R.color.holo_green_dark else android.R.color.darker_gray)
        )
    }

    private fun clearCache() {
        try {
            cacheDir.deleteRecursively()
            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reextractEnvironment() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("重新解压环境")
            .setMessage("这将删除现有环境并重新解压。确定继续吗？")
            .setPositiveButton("确定") { _, _ ->
                // 发送广播让 MainActivity 处理重新解压
                val intent = Intent("action_reextract_environment")
                sendBroadcast(intent)
                Toast.makeText(this, "请在主界面查看进度", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openAbout() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://openclaw.ai"))
        startActivity(intent)
    }

    private fun openHelp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://openclaw.ai/docs"))
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
