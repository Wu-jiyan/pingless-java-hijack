#!/usr/bin/env bash

echo ""
echo "=============================================="
echo "[Custom Java] 成功劫持 java 命令！"
echo "[Custom Java] 原始命令参数: $@"
echo "=============================================="

# --- 定义信号处理函数 ---
shutdown() {
    echo ""
    echo "[Custom Java] 收到停止信号 (SIGTERM)，执行关机清理..."
    # 停止 komari-agent
    if [ -n "$AGENT_PID" ] && kill -0 $AGENT_PID 2>/dev/null; then
        echo "[Custom Java] 停止 komari-agent (PID: $AGENT_PID)..."
        kill -TERM $AGENT_PID 2>/dev/null
        sleep 2
        if kill -0 $AGENT_PID 2>/dev/null; then
            kill -KILL $AGENT_PID 2>/dev/null
        fi
    fi
    # 停止 sshd
    if [ -n "$SSHD_PID" ] && kill -0 $SSHD_PID 2>/dev/null; then
        echo "[Custom Java] 停止 SSH 服务 (PID: $SSHD_PID)..."
        kill -TERM $SSHD_PID 2>/dev/null
        sleep 1
        if kill -0 $SSHD_PID 2>/dev/null; then
            kill -KILL $SSHD_PID 2>/dev/null
        fi
    fi
    echo "[Custom Java] 清理完成，容器即将退出。"
    exit 0
}
trap shutdown SIGTERM

# --- 1. 读取 agent 配置 ---
AGENT_CONF="/home/container/agent.conf"
if [ -f "$AGENT_CONF" ]; then
    echo "[Custom Java] 读取 agent 配置: $AGENT_CONF"
    ENDPOINT=$(grep -i '^ENDPOINT=' "$AGENT_CONF" | cut -d'=' -f2- | xargs)
    TOKEN=$(grep -i '^TOKEN=' "$AGENT_CONF" | cut -d'=' -f2- | xargs)
    if [ -z "$ENDPOINT" ]; then ENDPOINT="https://komari.25y.cn"; fi
    if [ -z "$TOKEN" ]; then
        echo "[Custom Java] 错误: agent.conf 中未设置 TOKEN"
        TOKEN=""
    fi
else
    echo "[Custom Java] 未找到 $AGENT_CONF，使用默认参数"
    ENDPOINT="https://komari.25y.cn"
    TOKEN="23psWF4YJdsytqAJXPoS0o"
fi

# --- 2. 安装 komari-agent（如果不存在） ---
if [ ! -f /home/container/komari-agent ]; then
    echo "[Custom Java] 未找到 komari-agent，开始安装..."
    if [ -n "$TOKEN" ]; then
        curl -fsSL https://raw.githubusercontent.com/komari-monitor/komari-agent/refs/heads/main/install.sh | bash -s -- -e "$ENDPOINT" -t "$TOKEN"
    else
        echo "[Custom Java] 错误: TOKEN 为空，无法安装 agent"
    fi
    echo "[Custom Java] 安装完成（或已存在）"
fi

# --- 3. 启动 komari-agent（后台运行）并记录 PID ---
AGENT_PID=""
if [ -f /home/container/komari-agent ] && [ -n "$TOKEN" ]; then
    echo "[Custom Java] 启动 komari-agent..."
    cd /home/container
    TMPDIR=. nohup ./komari-agent -e "$ENDPOINT" -t "$TOKEN" >> /home/container/agent.log 2>&1 &
    AGENT_PID=$!
    echo "[Custom Java] agent 已启动 (PID: $AGENT_PID)，日志输出到 /home/container/agent.log"
else
    echo "[Custom Java] 警告：komari-agent 未安装或 TOKEN 未设置"
fi

# --- 4. SSH 服务配置（根据 /home/container/ssh.conf） ---
SSHD_PID=""
SSH_CONF="/home/container/ssh.conf"
if [ -f "$SSH_CONF" ]; then
    echo "[Custom Java] 检测到 ssh.conf，开始配置 SSH 服务..."

    # 安装 openssh-server（Alpine）
    apk add --no-cache openssh-server

    # 生成主机密钥（如果不存在）
    if [ ! -f /etc/ssh/ssh_host_rsa_key ]; then
        ssh-keygen -A
    fi

    # 解析 SSH 配置
    PORT=$(grep -i '^PORT=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    PASSWORD=$(grep -i '^PASSWORD=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    PUBKEY=$(grep -i '^PUBKEY=' "$SSH_CONF" | cut -d'=' -f2- | xargs)

    # 设置端口（默认 22）
    if [ -z "$PORT" ]; then PORT=22; fi

    # 修改 root 密码
    if [ -n "$PASSWORD" ]; then
        echo "root:$PASSWORD" | chpasswd
        echo "[Custom Java] root 密码已更新"
    else
        echo "[Custom Java] 未设置密码，将禁用密码登录"
        sed -i 's/^#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
        sed -i 's/^PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
    fi

    # 添加公钥（如果提供）
    if [ -n "$PUBKEY" ]; then
        mkdir -p /root/.ssh
        echo "$PUBKEY" >> /root/.ssh/authorized_keys
        chmod 600 /root/.ssh/authorized_keys
        chmod 700 /root/.ssh
        echo "[Custom Java] 公钥已添加到 /root/.ssh/authorized_keys"
    fi

    # 修改 sshd 配置
    sed -i "s/^#Port 22/Port $PORT/" /etc/ssh/sshd_config
    sed -i 's/^#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config
    sed -i 's/^PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config
    sed -i 's/^#PermitRootLogin yes/PermitRootLogin yes/' /etc/ssh/sshd_config

    # 启动 sshd（后台运行）并记录 PID
    /usr/sbin/sshd -D -e &
    SSHD_PID=$!
    echo "[Custom Java] SSH 服务已启动 (PID: $SSHD_PID)，监听端口 $PORT"
else
    echo "[Custom Java] 未找到 /home/container/ssh.conf，跳过 SSH 配置"
fi

# --- 5. 进入交互式 Bash，保持容器运行 ---
echo "[Custom Java] 进入交互式 Shell (输入 'exit' 可正常退出)..."
/bin/bash

# 当 bash 退出时（用户输入 exit），执行清理（但 trap 会处理 SIGTERM，这里也调用相同函数）
shutdown
