# OpenClaw Mobile 环境准备指南

## 概述

为了让 OpenClaw Mobile 运行，你需要准备以下内容：

1. **Node.js 运行时**（ARM64 Android 版本）
2. **OpenClaw npm 包**（核心应用）

两者将打包成 `environment.zip`，放入 `app/src/main/assets/`，首次启动时自动解压。

---

## 方案选择

### 方案 A：全自动设置（推荐）

运行一键设置脚本：

```bash
cd scripts
./setup-openclaw.sh
```

这个脚本会自动：
1. 下载 Node.js Mobile 预编译库（ARM64 Android）
2. 提取 node 二进制文件
3. 通过 npm 安装 OpenClaw 最新版
4. 创建启动脚本
5. 打包成 `environment.zip`

**要求：**
- 本机安装 Node.js 22+（用于下载 npm 包）
- 网络连接（下载约 100MB+ 文件）

#### 手动步骤（如果脚本失败）

**步骤 1: 下载 Node.js Mobile**
```bash
cd scripts/build

# 下载 Node.js Mobile for Android
curl -L -o nodejs-mobile-v18.17.0-android.tar.gz \
    "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v18.17.0/nodejs-mobile-v18.17.0-android.tar.gz"

# 解压
tar -xzf nodejs-mobile-v18.17.0-android.tar.gz
```

**步骤 2: 安装 OpenClaw**
```bash
mkdir -p environment/openclaw
cd environment/openclaw

npm init -y
npm install openclaw@latest --production
```

**步骤 3: 复制 Node.js 并打包**
```bash
cd ../..
mkdir -p environment/nodejs/bin
cp nodejs-mobile/libnode/bin/arm64-v8a/* environment/nodejs/bin/

# 创建启动脚本
cat > environment/openclaw/start.sh << 'EOF'
#!/system/bin/sh
export NODE_HOME="$(dirname "$0")/../nodejs"
export PATH="$NODE_HOME/bin:$PATH"
cd "$(dirname "$0")"
exec "$NODE_HOME/bin/node" node_modules/openclaw/bin/openclaw gateway --port "${PORT:-3000}" --verbose
EOF
chmod +x environment/openclaw/start.sh

# 打包
cd environment
zip -r ../../app/src/main/assets/environment.zip .
```

---

## OpenClaw 要求

OpenClaw 官方信息：
- **官网**: https://openclaw.ai
- **GitHub**: https://github.com/openclaw/openclaw
- **npm**: `openclaw`

### 1. 启动方式

OpenClaw 通过 CLI 启动：

```bash
openclaw gateway --port 18789 --verbose
```

在 Android 中通过 Node.js 运行：

```bash
node node_modules/openclaw/bin/openclaw gateway --port 18789 --verbose
```

### 2. 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `PORT` | Gateway 端口 | 18789 |
| `A11Y_BRIDGE_PORT` | A11y Bridge 端口 | 7333 |
| `HOME` | 工作目录 | /data/data/... |

### 3. 配置文件

OpenClaw 配置位于 `~/.openclaw/openclaw.json`，API Key 可以通过以下方式设置：

```bash
# 通过引导向导
openclaw onboard

# 或直接编辑配置文件
```

### 4. 健康检查端点

OpenClaw Gateway 健康检查：

```
GET http://localhost:18789/health
```

或访问根路径确认服务运行。

---

## 验证环境包

打包完成后，验证 `environment.zip`：

```bash
# 查看压缩包内容
unzip -l app/src/main/assets/environment.zip

# 应该包含：
# nodejs/node
# openclaw/node_modules/openclaw/dist/index.js
# openclaw/start.sh
```

---

## 构建 APK

环境包准备好后，构建 APK：

```bash
./gradlew assembleDebug
```

安装测试：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 常见问题

### Q: 我不知道 OpenClaw 在哪里获取
A: OpenClaw 可能是一个内部项目。请确认：
- 你是否在开发 OpenClaw？
- 是否有 GitHub 仓库地址？
- 是否有现成的 npm 包？

### Q: 可以先用一个假的 Node.js 环境测试吗？
A: 可以。创建一个简单的 `index.js`：

```javascript
// scripts/build/environment/openclaw/node_modules/openclaw/dist/index.js
const http = require('http');
const port = process.env.PORT || 3000;

const server = http.createServer((req, res) => {
  if (req.url === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok' }));
  } else {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end('<h1>OpenClaw Test Server</h1>');
  }
});

server.listen(port, () => {
  console.log(`Test server running on port ${port}`);
});
```

### Q: Node.js Mobile 下载失败怎么办？
A: 可以手动下载：
1. 访问 https://github.com/nodejs-mobile/nodejs-mobile/releases
2. 下载 `nodejs-mobile-v18.x.x-android.zip`
3. 解压后将 `libnode/bin/arm64-v8a` 复制到 `scripts/build/environment/nodejs/`

### Q: APK 体积超过 100MB 怎么办？
A: 
- 只保留 ARM64 版本（已配置）
- 使用 ProGuard 压缩（已配置）
- 如果 Node.js 太大，可以考虑：
  - 使用 Termux 方案（动态下载 Node.js）
  - 分包上传（Google Play 支持）

---

## 下一步

1. 确认 OpenClaw 的来源和获取方式
2. 运行 `./scripts/prepare-nodejs.sh` 准备 Node.js
3. 将 OpenClaw 放入正确位置
4. 打包并构建 APK
5. 测试运行

如有问题，请检查日志：`adb logcat -s OpenClaw:*`