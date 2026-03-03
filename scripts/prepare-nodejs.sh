#!/bin/bash

# 准备 Node.js Mobile 运行时环境
# 这个脚本下载 Node.js Mobile 并打包成 environment.zip

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"
BUILD_DIR="$SCRIPT_DIR/build"

echo "=== OpenClaw Mobile Node.js 环境准备脚本 ==="
echo ""

# 创建目录
mkdir -p "$ASSETS_DIR"
mkdir -p "$BUILD_DIR"

cd "$BUILD_DIR"

# Node.js Mobile 版本
NODE_VERSION="18.17.0"
NODEJS_MOBILE_VERSION="18.17.0"

# 下载 Node.js Mobile 预编译库
echo "[1/5] 下载 Node.js Mobile for Android..."
if [ ! -f "nodejs-mobile-v${NODEJS_MOBILE_VERSION}-android.zip" ]; then
    curl -L -o "nodejs-mobile-v${NODEJS_MOBILE_VERSION}-android.zip" \
        "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v${NODEJS_MOBILE_VERSION}/nodejs-mobile-v${NODEJS_MOBILE_VERSION}-android.zip"
else
    echo "      文件已存在，跳过下载"
fi

# 解压
echo "[2/5] 解压 Node.js Mobile..."
if [ ! -d "nodejs-mobile" ]; then
    unzip -q "nodejs-mobile-v${NODEJS_MOBILE_VERSION}-android.zip" -d "nodejs-mobile"
fi

# 准备目录结构
echo "[3/5] 准备目录结构..."
mkdir -p "environment/nodejs"
mkdir -p "environment/openclaw"

# 复制 ARM64 版本的 Node.js
echo "[4/5] 复制 ARM64 二进制文件..."
if [ -d "nodejs-mobile/libnode/bin/arm64-v8a" ]; then
    cp -r "nodejs-mobile/libnode/bin/arm64-v8a"/* "environment/nodejs/"
elif [ -d "nodejs-mobile/android/libnode/bin/arm64-v8a" ]; then
    cp -r "nodejs-mobile/android/libnode/bin/arm64-v8a"/* "environment/nodejs/"
else
    echo "错误：找不到 ARM64 二进制文件"
    find "nodejs-mobile" -name "node" -type f
    exit 1
fi

# 创建启动脚本
cat > "environment/openclaw/start.sh" << 'EOF'
#!/system/bin/sh

# OpenClaw 启动脚本
export NODE_HOME="$(dirname "$0")/../nodejs"
export PATH="$NODE_HOME/bin:$PATH"
export HOME="$(dirname "$0")"

# 启动 OpenClaw
cd "$(dirname "$0")"
exec "$NODE_HOME/bin/node" node_modules/openclaw/dist/index.js "$@"
EOF

chmod +x "environment/openclaw/start.sh"

# 注意：这里需要手动放置 OpenClaw npm 包
echo ""
echo "[5/5] 打包 environment.zip..."
echo "      注意：你需要手动将 OpenClaw npm 包放入:"
echo "      $BUILD_DIR/environment/openclaw/node_modules/"
echo ""

# 打包
cd "environment"
zip -r "$ASSETS_DIR/environment.zip" .

echo ""
echo "=== 完成 ==="
echo "environment.zip 已生成: $ASSETS_DIR/environment.zip"
echo ""
echo "下一步:"
echo "1. 确保 OpenClaw npm 包已放入 $BUILD_DIR/environment/openclaw/node_modules/"
echo "2. 重新运行此脚本打包"
echo "3. 构建 APK"
