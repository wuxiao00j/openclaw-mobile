#!/bin/bash

# 下载 OpenClaw npm 包
# 注意：这只是一个示例脚本，需要根据 OpenClaw 实际的发布方式调整

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
OPENCLAW_DIR="$BUILD_DIR/environment/openclaw"

echo "=== 下载 OpenClaw ==="
echo ""

mkdir -p "$OPENCLAW_DIR"
cd "$OPENCLAW_DIR"

# 方法 1: 如果 OpenClaw 发布在 npm
# npm install openclaw --production

# 方法 2: 如果 OpenClaw 是 GitHub 仓库
echo "请指定 OpenClaw 的下载方式:"
echo ""
echo "选项 1: 从 npm 安装 (如果已发布)"
echo "  npm install openclaw"
echo ""
echo "选项 2: 从 GitHub 下载"
echo "  需要手动下载并解压到: $OPENCLAW_DIR"
echo ""
echo "选项 3: 本地路径"
echo "  如果你有本地构建好的 OpenClaw，复制到: $OPENCLAW_DIR"
echo ""

# 暂时创建一个占位文件
cat > README.txt << EOF
请将 OpenClaw 文件放置在此目录：

目录结构：
.
├── node_modules/
│   └── openclaw/
│       ├── dist/
│       │   └── index.js    # 入口文件
│       └── package.json
└── start.sh                # 启动脚本（已由 prepare-nodejs.sh 创建）

OpenClaw 需要是一个标准的 Node.js 应用，可以通过 node index.js 启动。
EOF

echo "已创建 README.txt，请按照说明放置 OpenClaw 文件"
echo "目录: $OPENCLAW_DIR"
