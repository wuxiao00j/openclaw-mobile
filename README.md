# OpenClaw Mobile

OpenClaw (https://openclaw.ai) 的 Android 原生封装，让用户无需安装 Termux 即可在手机上运行 OpenClaw AI 助手。

## 功能特性

- ✅ 一键安装，无需手动配置 Termux
- ✅ 内置 A11y Bridge 无障碍服务（AI 控制手机）
- ✅ 自动权限引导（无障碍、电池优化、截图）
- ✅ 首次启动自动解压 Node.js + OpenClaw 环境
- ✅ API Key 加密存储
- ✅ Chrome Custom Tabs 打开 OpenClaw Web UI
- ✅ 进程保活与自动重启
- ✅ 端口占用自动切换

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **网络**: OkHttp + Retrofit
- **后台服务**: ForegroundService + WorkManager
- **HTTP 服务器**: NanoHTTPD (A11y Bridge)

## 项目结构

```
app/src/main/java/com/openclaw/mobile/
├── a11y/               # A11y Bridge 无障碍服务
├── config/             # 配置管理（加密存储）
├── di/                 # Hilt 依赖注入
├── receiver/           # 广播接收器
├── service/            # 核心服务
│   ├── OpenClawService.kt      # OpenClaw Gateway 服务
│   ├── ExtractionService.kt    # 环境解压服务
│   ├── A11yBridgeService.kt    # 无障碍服务 + HTTP API
│   └── KeepAliveJobService.kt  # 保活服务
├── ui/                 # UI 层
│   ├── main/           # 主界面
│   ├── config/         # 配置对话框
│   └── permission/     # 权限申请
└── utils/              # 工具类
```

## 快速开始

### 1. 准备环境

你需要准备 Node.js Mobile 运行时和 OpenClaw npm 包：

```bash
cd scripts
./setup-openclaw.sh
```

这会自动：
- 下载 Node.js Mobile for Android (ARM64)
- 安装 OpenClaw npm 包
- 打包成 `app/src/main/assets/environment.zip`

**要求**: 本机已安装 Node.js 22+ (用于下载 npm 包)

### 2. 构建 APK

```bash
./gradlew assembleDebug
```

### 3. 安装测试

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## OpenClaw 集成

OpenClaw 官方信息:
- **官网**: https://openclaw.ai
- **GitHub**: https://github.com/openclaw/openclaw
- **npm**: `openclaw`

OpenClaw 在 Android 中的启动命令:
```bash
node node_modules/openclaw/bin/openclaw gateway --port 18789 --verbose
```

## 权限说明

| 权限 | 用途 | 是否必须 |
|------|------|---------|
| 无障碍服务 | AI 控制手机操作 | ✅ 是 |
| 电池优化白名单 | 防止系统杀后台 | ⚠️ 推荐 |
| 悬浮窗 | 快捷操作入口 | ❌ 可选 |
| 截图 | 屏幕识别 | ✅ 是 |

## 开发文档

- [PRD.md](./PRD.md) - 产品需求文档
- [SETUP.md](./SETUP.md) - 环境准备详细指南
- [PROGRESS.md](./PROGRESS.md) - 开发进度追踪

## 许可证

MIT License
