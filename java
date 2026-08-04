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

# ---------- 2. 配置 SSH 服务 ----------
SSH_CONF="/home/container/ssh.conf"
SSH_ENABLED=0
if [ -f "$SSH_CONF" ]; then
    PORT=$(grep -i '^PORT=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    PUBKEY=$(grep -i '^PUBKEY=' "$SSH_CONF" | cut -d'=' -f2- | xargs)
    [ -z "$PORT" ] && PORT=2222

    if [ -n "$PUBKEY" ]; then
        # 准备 authorized_keys
        mkdir -p /home/container/.ssh
        echo "$PUBKEY" > /home/container/.ssh/authorized_keys
        chmod 600 /home/container/.ssh/authorized_keys
        chmod 700 /home/container/.ssh

        # 生成主机密钥（如果不存在）
        SSH_KEY_DIR="/home/container/ssh_host_keys"
        mkdir -p "$SSH_KEY_DIR"
        if [ ! -f "$SSH_KEY_DIR/ssh_host_rsa_key" ]; then
            echo "[Custom Java] 生成 SSH 主机密钥..."
            ssh-keygen -t rsa -f "$SSH_KEY_DIR/ssh_host_rsa_key" -N "" -q
            ssh-keygen -t ecdsa -f "$SSH_KEY_DIR/ssh_host_ecdsa_key" -N "" -q
            ssh-keygen -t ed25519 -f "$SSH_KEY_DIR/ssh_host_ed25519_key" -N "" -q
            # 检查生成是否成功
            if [ ! -f "$SSH_KEY_DIR/ssh_host_rsa_key" ]; then
                echo "[Custom Java] 错误：主机密钥生成失败！"
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

# ---------- 3. 启动前台进程 ----------
if [ $SSH_ENABLED -eq 1 ]; then
    echo "[Custom Java] 启动 SSH 服务（前台运行）..."
    # 使用 exec 替换当前进程为 sshd，移除废弃选项
    exec /usr/sbin/sshd -D -e -p "$PORT" \
        -o "HostKey $SSH_KEY_DIR/ssh_host_rsa_key" \
        -o "HostKey $SSH_KEY_DIR/ssh_host_ecdsa_key" \
        -o "HostKey $SSH_KEY_DIR/ssh_host_ed25519_key" \
        -o "AuthorizedKeysFile /home/container/.ssh/authorized_keys" \
        -o "PubkeyAuthentication yes" \
        -o "PasswordAuthentication no" \
        -o "PermitEmptyPasswords no" \
        -o "PermitRootLogin no" \
        -o "PidFile /home/container/sshd.pid"
    # 如果 exec 失败（例如 sshd 退出），则继续执行下面的 fallback
    echo "[Custom Java] SSH 服务意外退出，进入 fallback 模式"
else
    echo "[Custom Java] SSH 未启用，容器将保持运行"
fi

# ---------- 4. Fallback：保持容器存活 ----------
# 这里你可以选择启动一个交互式 bash，但请注意输入输出不是 TTY
# 或者直接 sleep 无限，用 SSH 登录后获得交互式 shell
# 由于我们已尝试 SSH 失败，这里用 sleep 保持容器运行
while true; do
    /bin/bash
    echo "bash 退出，重启中..."
done
