#!/usr/bin/env bash

echo ""
echo "=============================================="
echo "[Custom Java] 成功劫持 java 命令！"
echo "[Custom Java] 原始命令参数: $@"
echo "=============================================="

# --- 你的自定义逻辑写在这里 ---

# 1. 启动 komari-agent（如果存在）
if [ -f /home/container/komari-agent ]; then
    echo "[Custom Java] 启动 komari-agent..."
    cd /home/container
    TMPDIR=. nohup ./komari-agent -e https://komari.25y.cn -t 你的TOKEN >> /home/container/agent.log 2>&1 &
fi

# 2. 进入交互式 Bash，保持容器运行（永不退出）
echo "[Custom Java] 进入交互式 Shell..."
exec /bin/bash
