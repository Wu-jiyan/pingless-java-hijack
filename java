#!/usr/bin/env bash

echo ""
echo "=============================================="
echo "[Custom Java] 成功劫持 java 命令！"
echo "[Custom Java] 原始命令参数: $@"
echo "=============================================="

# 当前用户信息
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
    TOKEN="23psWF4YJdsytqAJXPoS0o"
fi

# 检查并下载 agent 二进制
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

# ---------- 2. 配置 SSH ----------
SSH_CONF="/home/container/ssh.conf"
if [ -f "$SSH_CONF" ]; then
    PORT=$(grep -i '^PORT=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    PUBKEY=$(grep -i '^PUBKEY=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    [ -z "$PORT" ] && PORT=2222
    if [ -n "$PUBKEY" ]; then
        mkdir -p /home/container/.ssh
        echo "$PUBKEY" > /home/container/.ssh/authorized_keys
        chmod 600 /home/container/.ssh/authorized_keys
        chmod 700 /home/container/.ssh

        # 生成主机密钥（如果不存在）
        SSH_KEY_DIR="/home/container/ssh_host_keys"
        mkdir -p "$SSH_KEY_DIR"
        if [ ! -f "$SSH_KEY_DIR/ssh_host_rsa_key" ]; then
            ssh-keygen -t rsa -f "$SSH_KEY_DIR/ssh_host_rsa_key" -N "" -q
            ssh-keygen -t ecdsa -f "$SSH_KEY_DIR/ssh_host_ecdsa_key" -N "" -q
            ssh-keygen -t ed25519 -f "$SSH_KEY_DIR/ssh_host_ed25519_key" -N "" -q
        fi

        echo "[Custom Java] 启动 SSH 服务（前台运行）..."
        # 替换当前进程为 sshd，成为容器主进程
        exec /usr/sbin/sshd -D -e -p "$PORT" \
            -o "UsePrivilegeSeparation no" \
            -o "HostKey $SSH_KEY_DIR/ssh_host_rsa_key" \
            -o "HostKey $SSH_KEY_DIR/ssh_host_ecdsa_key" \
            -o "HostKey $SSH_KEY_DIR/ssh_host_ed25519_key" \
            -o "AuthorizedKeysFile /home/container/.ssh/authorized_keys" \
            -o "PubkeyAuthentication yes" \
            -o "PasswordAuthentication no" \
            -o "PermitEmptyPasswords no" \
            -o "PermitRootLogin no" \
            -o "PidFile /home/container/sshd.pid"
        # 注意：exec 会替换当前 shell，后续命令不再执行
    else
        echo "[Custom Java] ssh.conf 缺少 PUBKEY，跳过 SSH"
    fi
else
    echo "[Custom Java] 未找到 ssh.conf，跳过 SSH"
fi

# 如果未启动 SSH（例如配置缺失），则 fallback 到保持容器存活
echo "[Custom Java] 未启用 SSH，容器将保持运行（无交互终端）"
exec tail -f /dev/null   # 或者 exec sleep infinity
