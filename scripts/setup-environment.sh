#!/bin/bash

# OpenClaw Mobile 环境准备脚本
# 由于 Node.js Mobile 下载可能受限，此脚本提供多种备选方案

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"
BUILD_DIR="$SCRIPT_DIR/build"

echo "=== OpenClaw Mobile 环境准备 ==="
echo ""

# 创建目录
mkdir -p "$ASSETS_DIR"
mkdir -p "$BUILD_DIR"

# 检查是否已有环境包
if [ -f "$ASSETS_DIR/environment.zip" ]; then
    echo "✓ 环境包已存在: $ASSETS_DIR/environment.zip"
    ls -lh "$ASSETS_DIR/environment.zip"
    echo ""
    echo "如需重新生成，请先删除该文件"
    exit 0
fi

echo "请选择环境准备方式:"
echo ""
echo "1) 下载预编译的 Node.js Mobile (推荐，但可能受网络影响)"
echo "2) 使用本地 Node.js 和占位 OpenClaw (仅测试 UI)"
echo "3) 手动放置文件"
echo ""

read -p "请输入选项 (1-3): " choice

case $choice in
    1)
        download_nodejs_mobile
        ;;
    2)
        create_test_environment
        ;;
    3)
        show_manual_instructions
        ;;
    *)
        echo "无效选项"
        exit 1
        ;;
esac

function download_nodejs_mobile() {
    echo "[*] 尝试下载 Node.js Mobile..."
    
    cd "$BUILD_DIR"
    
    # 尝试多个镜像源
    NODE_VERSION="18.17.0"
    DOWNLOAD_URLS=(
        "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v${NODE_VERSION}/nodejs-mobile-v${NODE_VERSION}-android.tar.gz"
        "https://ghproxy.com/https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v${NODE_VERSION}/nodejs-mobile-v${NODE_VERSION}-android.tar.gz"
        "https://mirror.ghproxy.com/https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v${NODE_VERSION}/nodejs-mobile-v${NODE_VERSION}-android.tar.gz"
    )
    
    DOWNLOADED=false
    for url in "${DOWNLOAD_URLS[@]}"; do
        echo "尝试: $url"
        if curl -L --connect-timeout 30 -o "nodejs-mobile-android.tar.gz" "$url" 2>/dev/null; then
            if [ -s "nodejs-mobile-android.tar.gz" ] && [ "$(stat -f%z nodejs-mobile-android.tar.gz)" -gt 1000 ]; then
                echo "✓ 下载成功"
                DOWNLOADED=true
                break
            fi
        fi
        rm -f nodejs-mobile-android.tar.gz
    done
    
    if [ "$DOWNLOADED" = false ]; then
        echo "✗ 下载失败，切换到测试环境模式"
        create_test_environment
        return
    fi
    
    # 解压
    echo "[*] 解压..."
    mkdir -p nodejs-mobile
    tar -xzf nodejs-mobile-android.tar.gz -C nodejs-mobile --strip-components=1
    
    # 安装 OpenClaw
    install_openclaw
}

function install_openclaw() {
    echo "[*] 安装 OpenClaw npm 包..."
    
    mkdir -p "$BUILD_DIR/environment/openclaw"
    cd "$BUILD_DIR/environment/openclaw"
    
    npm init -y
    npm install openclaw@latest --production 2>&1 | tail -20
    
    if [ ! -d "node_modules/openclaw" ]; then
        echo "✗ OpenClaw 安装失败"
        exit 1
    fi
    
    # 准备完整环境
    prepare_full_environment
}

function prepare_full_environment() {
    echo "[*] 准备完整环境..."
    
    cd "$BUILD_DIR"
    
    # 复制 Node.js
    mkdir -p environment/nodejs/bin
    if [ -d "nodejs-mobile/libnode/bin/arm64-v8a" ]; then
        cp nodejs-mobile/libnode/bin/arm64-v8a/* environment/nodejs/bin/
    else
        echo "✗ 未找到 Node.js 二进制文件"
        exit 1
    fi
    
    # 创建启动脚本
    cat > environment/openclaw/start.sh << 'EOF'
#!/system/bin/sh
export NODE_HOME="$(dirname "$0")/../nodejs"
export PATH="$NODE_HOME/bin:$PATH"
export HOME="$(dirname "$0")"
export PORT="${PORT:-18789}"
cd "$(dirname "$0")"
exec "$NODE_HOME/bin/node" node_modules/openclaw/bin/openclaw gateway --port "$PORT" --verbose
EOF
    chmod +x environment/openclaw/start.sh
    
    # 打包
    echo "[*] 打包 environment.zip..."
    cd environment
    zip -r "$ASSETS_DIR/environment.zip" . -x "*.cache" "*.tmp"
    
    echo ""
    echo "✓ 环境包创建成功:"
    ls -lh "$ASSETS_DIR/environment.zip"
}

function create_test_environment() {
    echo "[*] 创建测试环境..."
    
    cd "$BUILD_DIR"
    rm -rf environment
    mkdir -p environment/nodejs/bin
    mkdir -p environment/openclaw
    
    # 创建占位 Node.js 脚本（模拟）
    cat > environment/nodejs/bin/node << 'EOF'
#!/system/bin/sh
echo "Node.js Test Environment"
echo "This is a placeholder. Replace with real Node.js binary."
exit 1
EOF
    chmod +x environment/nodejs/bin/node
    
    # 创建测试 OpenClaw 入口
    mkdir -p environment/openclaw/node_modules/openclaw/dist
    cat > environment/openclaw/node_modules/openclaw/dist/index.js << 'EOF'
// OpenClaw Test Stub
const http = require('http');
const port = process.env.PORT || 18789;

const server = http.createServer((req, res) => {
    if (req.url === '/health') {
        res.writeHead(200, {'Content-Type': 'application/json'});
        res.end(JSON.stringify({status: 'ok', mode: 'test'}));
    } else {
        res.writeHead(200, {'Content-Type': 'text/html'});
        res.end('<h1>OpenClaw Test Server</h1><p>This is a placeholder environment.</p>');
    }
});

server.listen(port, () => {
    console.log(`Test server running on port ${port}`);
});
EOF
    
    # 创建启动脚本
    cat > environment/openclaw/start.sh << 'EOF'
#!/system/bin/sh
cd "$(dirname "$0")"
exec node node_modules/openclaw/dist/index.js
EOF
    chmod +x environment/openclaw/start.sh
    
    # 打包
    cd environment
    zip -r "$ASSETS_DIR/environment.zip" .
    
    echo ""
    echo "✓ 测试环境包创建成功"
    echo "⚠ 注意: 这是测试环境，不包含真实的 Node.js 和 OpenClaw"
    echo "  请后续替换为真实环境包"
}

function show_manual_instructions() {
    echo ""
    echo "=== 手动准备说明 ==="
    echo ""
    echo "1. 下载 Node.js Mobile:"
    echo "   https://github.com/nodejs-mobile/nodejs-mobile/releases"
    echo "   下载: nodejs-mobile-v18.x.x-android.tar.gz"
    echo ""
    echo "2. 解压并准备目录结构:"
    echo "   $BUILD_DIR/environment/"
    echo "   ├── nodejs/bin/         (放置 node 二进制文件)"
    echo "   └── openclaw/           (放置 OpenClaw npm 包)"
    echo ""
    echo "3. 打包:"
    echo "   cd $BUILD_DIR/environment"
    echo "   zip -r $ASSETS_DIR/environment.zip ."
    echo ""
}

# 执行主逻辑（需要 source 脚本后才能使用函数）
# 如果直接运行，默认创建测试环境
create_test_environment
