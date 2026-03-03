#!/bin/bash

# 设置 OpenClaw 环境
# 自动下载 Node.js Mobile 和 OpenClaw npm 包

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"
BUILD_DIR="$SCRIPT_DIR/build"

echo "=== OpenClaw Mobile 环境设置脚本 ==="
echo ""

# 检查 Node.js (用于准备 npm 包)
if ! command -v node &> /dev/null; then
    echo "错误：需要先安装 Node.js 来准备 npm 包"
    echo "请访问 https://nodejs.org/ 安装 Node.js 22+"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 22 ]; then
    echo "警告：Node.js 版本过低 (当前: $(node -v)，推荐: 22+)"
fi

# 创建目录
mkdir -p "$ASSETS_DIR"
mkdir -p "$BUILD_DIR"

cd "$BUILD_DIR"

# === 步骤 1: 下载 Node.js Mobile ===
echo "[1/6] 检查 Node.js Mobile..."

NODEJS_MOBILE_VERSION="18.17.0"
NODEJS_MOBILE_FILE="nodejs-mobile-v${NODEJS_MOBILE_VERSION}-android.tar.gz"

if [ ! -f "$NODEJS_MOBILE_FILE" ]; then
    echo "      下载 Node.js Mobile for Android..."
    curl -L -o "$NODEJS_MOBILE_FILE" \
        "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v${NODEJS_MOBILE_VERSION}/${NODEJS_MOBILE_FILE}"
else
    echo "      文件已存在，跳过下载"
fi

if [ ! -d "nodejs-mobile" ]; then
    echo "      解压..."
    mkdir -p nodejs-mobile
    tar -xzf "$NODEJS_MOBILE_FILE" -C nodejs-mobile --strip-components=1
fi

# === 步骤 2: 准备环境目录结构 ===
echo "[2/6] 准备目录结构..."

rm -rf environment
mkdir -p environment/nodejs/bin
mkdir -p environment/openclaw

# === 步骤 3: 复制 Node.js 二进制文件 ===
echo "[3/6] 复制 ARM64 二进制文件..."

if [ -f "nodejs-mobile/libnode/bin/arm64-v8a/node" ]; then
    cp nodejs-mobile/libnode/bin/arm64-v8a/* environment/nodejs/bin/
elif [ -f "nodejs-mobile/android/libnode/bin/arm64-v8a/node" ]; then
    cp nodejs-mobile/android/libnode/bin/arm64-v8a/* environment/nodejs/bin/
else
    echo "查找 node 二进制文件..."
    find nodejs-mobile -name "node" -type f 2>/dev/null | head -5
    
    # 尝试其他路径
    if [ -d "nodejs-mobile/libnode/bin" ]; then
        cp -r nodejs-mobile/libnode/bin/* environment/nodejs/bin/ 2>/dev/null || true
    fi
fi

# 检查是否找到 node
if [ ! -f "environment/nodejs/bin/node" ]; then
    echo "错误：未找到 node 二进制文件"
    echo "目录内容:"
    find nodejs-mobile -type d | head -20
    exit 1
fi

chmod +x environment/nodejs/bin/*

# === 步骤 4: 安装 OpenClaw npm 包 ===
echo "[4/6] 安装 OpenClaw npm 包..."

cd environment/openclaw

# 初始化 package.json
cat > package.json << 'EOF'
{
  "name": "openclaw-mobile-env",
  "version": "1.0.0",
  "private": true,
  "dependencies": {}
}
EOF

# 安装 OpenClaw
# 使用 --production 跳过 devDependencies，减小体积
echo "      正在下载 openclaw 包（可能需要几分钟）..."
npm install openclaw@latest --production 2>&1 | tail -20

if [ ! -d "node_modules/openclaw" ]; then
    echo "错误：OpenClaw 安装失败"
    exit 1
fi

echo "      OpenClaw 版本: $(cat node_modules/openclaw/package.json | grep '"version"' | head -1)"

# === 步骤 5: 创建启动脚本 ===
echo "[5/6] 创建启动脚本..."

cd "$BUILD_DIR"

# 主启动脚本 - 匹配 OpenClaw 的启动方式
cat > environment/openclaw/start.sh << 'EOF'
#!/system/bin/sh

# OpenClaw 启动脚本 for Android

# 设置环境变量
export NODE_HOME="$(dirname "$0")/../nodejs"
export PATH="$NODE_HOME/bin:$PATH"
export HOME="$(dirname "$0")"
export NODE_ENV="production"

# 可选：从外部传入的端口配置
OPENCLAW_PORT="${PORT:-3000}"
A11Y_PORT="${A11Y_BRIDGE_PORT:-7333}"

echo "Starting OpenClaw on port $OPENCLAW_PORT..."
echo "A11y Bridge port: $A11Y_PORT"

# 切换到工作目录
cd "$(dirname "$0")"

# 启动 OpenClaw Gateway
# OpenClaw 的入口点通常是 bin/openclaw 或 dist/index.js
if [ -f "node_modules/openclaw/bin/openclaw" ]; then
    exec "$NODE_HOME/bin/node" node_modules/openclaw/bin/openclaw gateway \
        --port "$OPENCLAW_PORT" \
        --verbose
elif [ -f "node_modules/openclaw/dist/index.js" ]; then
    exec "$NODE_HOME/bin/node" node_modules/openclaw/dist/index.js gateway \
        --port "$OPENCLAW_PORT" \
        --verbose
else
    echo "Error: Cannot find OpenClaw entry point"
    echo "Contents of node_modules/openclaw:"
    ls -la node_modules/openclaw/
    exit 1
fi
EOF

chmod +x environment/openclaw/start.sh

# === 步骤 6: 打包 ===
echo "[6/6] 打包 environment.zip..."

cd environment

# 显示打包内容大小
echo "      打包内容:"
du -sh node_modules 2>/dev/null | head -1
du -sh nodejs 2>/dev/null | head -1

# 打包
zip -r "$ASSETS_DIR/environment.zip" . -x "*.cache" "*.tmp" "*.log" 2>&1 | tail -5

echo ""
echo "=== 完成 ==="
echo ""
echo "环境包信息:"
ls -lh "$ASSETS_DIR/environment.zip"
echo ""
echo "目录结构:"
find . -maxdepth 2 -type d | sort
