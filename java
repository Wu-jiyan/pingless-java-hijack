#!/usr/bin/env bash

echo ""
echo "=============================================="
echo "[Custom Java] 成功劫持 java 命令！"
echo "[Custom Java] 原始命令参数: $@"
echo "=============================================="

# 当前用户信息（用于调试）
CURRENT_USER=$(whoami 2>/dev/null || echo 'unknown')
CURRENT_UID=$(id -u 2>/dev/null || echo 'unknown')
echo "[Custom Java] 当前用户: $CURRENT_USER (UID: $CURRENT_UID)"
echo "[Custom Java] 工作目录: $(pwd)"

# ---------- 1. 下载并启动 komari-agent ----------
AGENT_CONF="/home/container/agent.conf"
if [ -f "$AGENT_CONF" ]; then
    echo "[Custom Java] 读取 agent 配置: $AGENT_CONF"
    ENDPOINT=$(grep -i '^ENDPOINT=' "$AGENT_CONF" | cut -d'=' -f2- | xargs)
    TOKEN=$(grep -i '^TOKEN=' "$AGENT_CONF" | cut -d'=' -f2- | xargs)
    if [ -z "$ENDPOINT" ]; then ENDPOINT="https://komari.25y.cn"; fi
    if [ -z "$TOKEN" ]; then
        echo "[Custom Java] 警告: agent.conf 中未设置 TOKEN"
        TOKEN=""
    fi
else
    echo "[Custom Java] 未找到 $AGENT_CONF，使用默认参数"
    ENDPOINT="https://komari.25y.cn"
    TOKEN="23psWF4YJdsytqAJXPoS0o"   # 请替换为你的实际 token
fi

# 检查并下载 agent 二进制（直接下载，不依赖安装脚本）
if [ ! -f /home/container/komari-agent ]; then
    echo "[Custom Java] 未找到 komari-agent，下载二进制文件..."
    ARCH=$(uname -m)
    if [ "$ARCH" = "x86_64" ]; then
        AGENT_URL="https://github.com/komari-monitor/komari-agent/releases/latest/download/komari-agent-linux-amd64"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        AGENT_URL="https://github.com/komari-monitor/komari-agent/releases/latest/download/komari-agent-linux-arm64"
    else
        echo "[Custom Java] 不支持的架构: $ARCH"
        AGENT_URL=""
    fi
    if [ -n "$AGENT_URL" ]; then
        curl -sL -o /home/container/komari-agent "$AGENT_URL" && chmod +x /home/container/komari-agent
        echo "[Custom Java] 下载完成。"
    else
        echo "[Custom Java] 无法下载 agent，跳过。"
    fi
fi

# 启动 agent（后台运行）
if [ -f /home/container/komari-agent ] && [ -n "$TOKEN" ]; then
    echo "[Custom Java] 启动 komari-agent..."
    cd /home/container
    TMPDIR=. nohup ./komari-agent -e "$ENDPOINT" -t "$TOKEN" >> /home/container/agent.log 2>&1 &
    AGENT_PID=$!
    echo "[Custom Java] agent 已启动 (PID: $AGENT_PID)，日志输出到 /home/container/agent.log"
else
    echo "[Custom Java] 警告：agent 未安装或 TOKEN 未设置"
fi

# ---------- 2. 下载 proot 并创建 alpine.sh 入口 ----------
if [ ! -f /home/container/proot ]; then
    echo "[Custom Java] 下载 proot 静态二进制..."
    curl -sL -o /home/container/proot https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-x86_64-static
    chmod +x /home/container/proot
fi

# 创建 alpine.sh 脚本（进入模拟 root 环境）
if [ ! -f /home/container/alpine.sh ]; then
    echo '#!/bin/sh' > /home/container/alpine.sh
    echo '/home/container/proot -r /home/container -b /proc -0 /bin/sh -c "ln -sf /proc/self/fd /dev/fd && exec /bin/sh"' >> /home/container/alpine.sh
    chmod +x /home/container/alpine.sh
fi

# ---------- 3. SSH 服务（使用静态 Dropbear，无需 root） ----------
SSH_CONF="/home/container/ssh.conf"
if [ -f "$SSH_CONF" ]; then
    echo "[Custom Java] 检测到 ssh.conf，配置 SSH (Dropbear)..."

    # 解析配置
    PORT=$(grep -i '^PORT=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    PUBKEY=$(grep -i '^PUBKEY=' "$SSH_CONF" | cut -d'=' -f2- | xargs)

    # 默认端口 2222（非 root 无法使用 1024 以下端口）
    if [ -z "$PORT" ]; then PORT=2222; fi

    # 如果未提供公钥，则提示并跳过
    if [ -z "$PUBKEY" ]; then
        echo "[Custom Java] ssh.conf 中未设置 PUBKEY，无法启用 SSH（非 root 仅支持公钥认证）"
    else
        # 下载 dropbear 静态二进制（简化验证，仅检查文件大小）
        if [ ! -f /home/container/dropbear ]; then
            echo "[Custom Java] 下载 Dropbear 静态二进制..."
            ARCH=$(uname -m)
            case "$ARCH" in
                x86_64|amd64)
                    DROPBEAR_URL="https://github.com/mkj/dropbear/releases/latest/download/dropbear-static-linux-amd64"
                    ;;
                aarch64|arm64)
                    DROPBEAR_URL="https://github.com/mkj/dropbear/releases/latest/download/dropbear-static-linux-aarch64"
                    ;;
                *)
                    echo "[Custom Java] 不支持的架构: $ARCH"
                    DROPBEAR_URL=""
                    ;;
            esac
            if [ -n "$DROPBEAR_URL" ]; then
                curl -sL -o /home/container/dropbear "$DROPBEAR_URL"
                chmod +x /home/container/dropbear
                # 简单验证：检查文件是否大于 500KB（有效二进制至少几百KB）
                if [ -f /home/container/dropbear ] && [ $(stat -c %s /home/container/dropbear 2>/dev/null || echo 0) -gt 500000 ]; then
                    echo "[Custom Java] Dropbear 下载成功。"
                else
                    echo "[Custom Java] 警告：下载的文件过小或无效，删除并跳过"
                    rm -f /home/container/dropbear
                fi
            fi
        fi

        if [ -f /home/container/dropbear ]; then
            # 准备公钥文件
            mkdir -p /home/container/.ssh
            echo "$PUBKEY" > /home/container/.ssh/authorized_keys
            chmod 600 /home/container/.ssh/authorized_keys
            chmod 700 /home/container/.ssh
            echo "[Custom Java] 公钥已添加到 /home/container/.ssh/authorized_keys"

            # 生成主机密钥（如果不存在）
            if [ ! -f /home/container/dropbear_host_key ]; then
                /home/container/dropbear -R -f /home/container/dropbear_host_key -p $PORT -r /dev/null 2>/dev/null &
                sleep 1
                killall dropbear 2>/dev/null
            fi

            # 启动 dropbear（后台运行）
            nohup /home/container/dropbear -p $PORT -r /home/container/dropbear_host_key -a /home/container/.ssh/authorized_keys -F -E >> /home/container/dropbear.log 2>&1 &
            SSHD_PID=$!
            echo "[Custom Java] Dropbear SSH 服务已启动 (PID: $SSHD_PID)，监听端口 $PORT"
            echo "[Custom Java] 日志输出到 /home/container/dropbear.log"
        else
            echo "[Custom Java] Dropbear 不可用，跳过 SSH。"
        fi
    fi
else
    echo "[Custom Java] 未找到 $SSH_CONF，跳过 SSH 配置"
fi

# ---------- 4. 进入交互式 Bash ----------
echo "[Custom Java] 进入交互式 Shell (输入 'exit' 可正常退出)..."
exec /bin/bash
