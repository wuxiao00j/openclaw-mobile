# Termux 替代方案

如果无法获取 Node.js Mobile，可以使用 Termux 方案。

## 方案概述

**不再预打包 Node.js**，而是在应用首次启动时：
1. 检测是否已安装 Termux
2. 如果没有，引导用户安装 Termux APK
3. 通过 Termux 安装 Node.js 和 OpenClaw

## 优点

- ✅ APK 体积小很多（< 10MB）
- ✅ 不需要下载 large 文件
- ✅ Node.js 版本由 Termux 维护
- ✅ 可以正常运行 OpenClaw

## 缺点

- ⚠️ 用户需要先安装 Termux
- ⚠️ 首次启动需要联网
- ⚠️ 安装过程较慢（需要下载 Node.js + npm 包）

## 实现代码

### 1. Termux 检测与安装引导

```kotlin
// TermuxHelper.kt
object TermuxHelper {
    
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun openTermuxDownload(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://github.com/termux/termux-app/releases"
        ))
        context.startActivity(intent)
    }
}
```

### 2. 通过 Termux 运行命令

```kotlin
// TermuxService.kt
class TermuxService {
    
    fun installNodejsAndOpenclaw(context: Context) {
        // 通过 Termux API 执行命令
        val intent = Intent("com.termux.RUN_COMMAND_ACTION").apply {
            setPackage("com.termux")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(
                "-c",
                "pkg install -y nodejs && npm install -g openclaw"
            ))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        }
        context.startService(intent)
    }
    
    fun startOpenclaw(context: Context) {
        val intent = Intent("com.termux.RUN_COMMAND_ACTION").apply {
            setPackage("com.termux")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/openclaw")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("gateway", "--port", "18789"))
        }
        context.startService(intent)
    }
}
```

### 3. 修改后的启动流程

```kotlin
// MainActivity.kt
override fun onResume() {
    super.onResume()
    
    when {
        !permissionManager.isAccessibilityServiceEnabled() -> {
            showA11yPermissionDialog()
        }
        !TermuxHelper.isTermuxInstalled(this) -> {
            showInstallTermuxDialog()
        }
        !isOpenclawInstalled() -> {
            startInstallOpenclaw()
        }
        else -> {
            startOpenclawService()
        }
    }
}
```

## 用户流程

```
安装 APK → 打开应用 → 申请权限 → 安装 Termux → 安装 Node.js/OpenClaw → 启动
```

### 详细流程

1. **安装 APK**（~5MB）
2. **打开应用** - 申请无障碍权限
3. **检测 Termux** - 如果未安装，弹出引导
4. **用户手动安装 Termux**（通过链接下载 APK）
5. **返回应用** - 自动安装 Node.js 和 OpenClaw
6. **等待安装完成**（约 2-5 分钟）
7. **启动 OpenClaw**

## 修改项目以支持 Termux 方案

### 修改 1：移除环境解压逻辑

```kotlin
// 不再需要 ExtractionService
// 删除或禁用以下代码：
// - ExtractionService.kt
// - 环境包检查
// - 解压进度 UI
```

### 修改 2：添加 Termux 检测

创建 `TermuxInstaller.kt`：

```kotlin
package com.openclaw.mobile.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object TermuxInstaller {
    
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_API_PACKAGE = "com.termux.api"
    
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun isApiInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun openDownloadPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://github.com/termux/termux-app/releases/latest"
        ))
        context.startActivity(intent)
    }
    
    fun installNodeAndOpenclaw(context: Context) {
        // 使用 Termux:API 执行安装命令
        val intent = Intent("com.termux.RUN_COMMAND_ACTION").apply {
            setPackage(TERMUX_PACKAGE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(
                "-c",
                """
                echo '正在更新包列表...' && 
                pkg update -y && 
                echo '正在安装 Node.js...' && 
                pkg install -y nodejs && 
                echo '正在安装 OpenClaw...' && 
                npm install -g openclaw && 
                echo '安装完成！'
                """.trimIndent()
            ))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        }
        context.startForegroundService(intent)
    }
}
```

### 修改 3：更新主界面

添加 Termux 状态检测：

```xml
<!-- activity_main.xml 添加 -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTermuxStatus"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp">
        
        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Termux 环境" />
            
        <TextView
            android:id="@+id/tvTermuxStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="未安装" />
            
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

## 对比两种方案

| 维度 | Node.js Mobile (原方案) | Termux (备选方案) |
|------|-------------------------|-------------------|
| APK 大小 | ~50-80 MB | ~5 MB |
| 首次启动 | 慢（解压） | 慢（下载安装） |
| 依赖 | 无 | 需要 Termux |
| 离线可用 | ✅ 是 | ❌ 否（首次） |
| 更新 Node.js | 需重新打包 APK | 随时更新 |
| 实现复杂度 | 中等 | 简单 |

## 推荐

**如果**：能下载到 Node.js Mobile → 使用原方案（更好的用户体验）

**如果**：无法下载 Node.js Mobile → 使用 Termux 方案（更简单可行）

## 下一步

需要我帮你实现 Termux 方案吗？需要修改：
1. 移除环境解压相关代码
2. 添加 Termux 检测和安装引导
3. 修改启动流程

大约需要 30 分钟完成修改。
