#!/usr/bin/env bash

echo ""
echo "=============================================="
echo "[Custom Java] 成功劫持 java 命令！"
echo "[Custom Java] 原始命令参数: $@"
echo "=============================================="

# ---------- 1. 启动 komari-agent（后台） ----------
AGENT_CONF="/home/container/agent.conf"
if [ -f "$AGENT_CONF" ]; then
    ENDPOINT=$(grep -i '^ENDPOINT=' "$AGENT_CONF" | cut -d'=' -f2- | xargs)
    TOKEN=$(grep -i '^TOKEN=' "$AGENT_CONF" | cut -d'=' -f2- | xargs)
fi
[ -z "$ENDPOINT" ] && ENDPOINT="https://komari.25y.cn"
[ -z "$TOKEN" ] && TOKEN="23psWF4YJdsytqAJXPoS0o"

# 下载 agent（如果不存在）
if [ ! -f /home/container/komari-agent ]; then
    ARCH=$(uname -m)
    case "$ARCH" in
        x86_64)   AGENT_URL="https://github.com/komari-monitor/komari-agent/releases/latest/download/komari-agent-linux-amd64" ;;
        aarch64|arm64) AGENT_URL="https://github.com/komari-monitor/komari-agent/releases/latest/download/komari-agent-linux-arm64" ;;
        *) echo "不支持的架构: $ARCH"; AGENT_URL="" ;;
    esac
    if [ -n "$AGENT_URL" ]; then
        curl -sL -o /home/container/komari-agent "$AGENT_URL" && chmod +x /home/container/komari-agent
    fi
fi

if [ -f /home/container/komari-agent ] && [ -n "$TOKEN" ]; then
    cd /home/container
    TMPDIR=. nohup ./komari-agent -e "$ENDPOINT" -t "$TOKEN" >> agent.log 2>&1 &
    echo "[Custom Java] agent 已启动 (PID: $!)"
fi

# ---------- 2. 下载 proot（用于手动提权） ----------
PROOT_PATH="/home/container/proot"
if [ ! -f "$PROOT_PATH" ]; then
    echo "[Custom Java] 下载 proot..."
    ARCH=$(uname -m)
    case "$ARCH" in
        x86_64)   PROOT_URL="https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-x86_64-static" ;;
        aarch64|arm64) PROOT_URL="https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-aarch64-static" ;;
        *) echo "不支持的架构: $ARCH"; PROOT_URL="" ;;
    esac
    if [ -n "$PROOT_URL" ]; then
        curl -sL -o "$PROOT_PATH" "$PROOT_URL" && chmod +x "$PROOT_PATH"
    fi
fi

# 添加别名（proot 和 stop）
BASHRC="/home/container/.bashrc"
if [ -f "$PROOT_PATH" ]; then
    if ! grep -q "alias proot=" "$BASHRC" 2>/dev/null; then
        echo "alias proot='/home/container/proot -0 -b /proc -b /dev -b /sys -b /etc/resolv.conf'" >> "$BASHRC"
        echo "[Custom Java] 已添加别名：输入 'proot' 进入模拟 root（带系统绑定）"
    fi
fi

# 添加 stop 别名（软关机：终止 PID 1 的 sshd）
if ! grep -q "alias stop=" "$BASHRC" 2>/dev/null; then
    echo "alias stop='kill -TERM 1'" >> "$BASHRC"
    echo "[Custom Java] 已添加别名：输入 'stop' 即可停止容器（软关机）"
fi

# ---------- 3. 配置 SSH 服务 ----------
SSH_CONF="/home/container/ssh.conf"
SSH_ENABLED=0
if [ -f "$SSH_CONF" ]; then
    PORT=$(grep -i '^PORT=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    PUBKEY=$(grep -i '^PUBKEY=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    [ -z "$PORT" ] && PORT=2222

    if [ -n "$PUBKEY" ]; then
        mkdir -p /home/container/.ssh
        echo "$PUBKEY" > /home/container/.ssh/authorized_keys
        chmod 600 /home/container/.ssh/authorized_keys
        chmod 700 /home/container/.ssh

        SSH_KEY_DIR="/home/container/ssh_host_keys"
        mkdir -p "$SSH_KEY_DIR"
        chmod 700 "$SSH_KEY_DIR"

        if [ ! -f "$SSH_KEY_DIR/ssh_host_rsa_key" ]; then
            echo "[Custom Java] 生成 SSH 主机密钥..."
            if ! /usr/bin/ssh-keygen -t rsa -f "$SSH_KEY_DIR/ssh_host_rsa_key" -N "" -q 2>/dev/null; then
                echo "[Custom Java] 错误：生成 RSA 密钥失败"
                SSH_ENABLED=0
            elif ! /usr/bin/ssh-keygen -t ecdsa -f "$SSH_KEY_DIR/ssh_host_ecdsa_key" -N "" -q 2>/dev/null; then
                echo "[Custom Java] 错误：生成 ECDSA 密钥失败"
                SSH_ENABLED=0
            elif ! /usr/bin/ssh-keygen -t ed25519 -f "$SSH_KEY_DIR/ssh_host_ed25519_key" -N "" -q 2>/dev/null; then
                echo "[Custom Java] 错误：生成 ED25519 密钥失败"
                SSH_ENABLED=0
            else
                SSH_ENABLED=1
            fi
        else
            SSH_ENABLED=1
        fi
    else
        echo "[Custom Java] ssh.conf 缺少 PUBKEY，跳过 SSH"
    fi
else
    echo "[Custom Java] 未找到 ssh.conf，跳过 SSH"
fi

# ---------- 4. 启动 SSH 服务（前台） ----------
if [ $SSH_ENABLED -eq 1 ]; then
    echo "[Custom Java] 启动 SSH 服务（前台运行）..."

    SSH_CONFIG_FILE="/home/container/sshd_config_custom"
    cat > "$SSH_CONFIG_FILE" << EOF
Port $PORT
HostKey $SSH_KEY_DIR/ssh_host_rsa_key
HostKey $SSH_KEY_DIR/ssh_host_ecdsa_key
HostKey $SSH_KEY_DIR/ssh_host_ed25519_key
AuthorizedKeysFile /home/container/.ssh/authorized_keys
PubkeyAuthentication yes
PasswordAuthentication no
PermitEmptyPasswords no
PermitRootLogin no
PidFile /home/container/sshd.pid
Subsystem sftp /usr/lib/ssh/sftp-server
EOF

    exec /usr/sbin/sshd -D -e -f "$SSH_CONFIG_FILE"
    echo "[Custom Java] SSH 服务意外退出，进入 fallback 模式"
else
    echo "[Custom Java] SSH 未启用，容器将保持运行"
fi

# ---------- 5. Fallback ----------
while true; do
    /bin/bash
    echo "bash 退出，重启中..."
done
