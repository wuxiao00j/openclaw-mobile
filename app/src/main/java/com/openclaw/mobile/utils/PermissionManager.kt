package com.openclaw.mobile.utils

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityService.FEEDBACK_ALL_MASK)
        
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == "com.openclaw.mobile.a11y.A11yBridgeService"
        }
    }

    /**
     * 打开无障碍服务设置页面
     */
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }

    /**
     * 检查是否已忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    /**
     * 打开电池优化设置
     */
    fun openBatteryOptimizationSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        activity.startActivity(intent)
    }

    /**
     * 检查悬浮窗权限
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    /**
     * 检查所有必需权限是否已授予
     */
    fun checkRequiredPermissions(): PermissionStatus {
        return PermissionStatus(
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations()
        )
    }

    data class PermissionStatus(
        val accessibilityEnabled: Boolean,
        val batteryOptimizationIgnored: Boolean
    ) {
        val allGranted: Boolean
            get() = accessibilityEnabled && batteryOptimizationIgnored
    }
}
