FROM ubuntu:22.04

# 设置非交互式安装，避免 tzdata 等包卡住
ENV DEBIAN_FRONTEND=noninteractive

# 更新软件源并安装必要软件
RUN apt-get update && apt-get install -y \
    bash curl openssh-server sudo \
    python3 python3-pip vim htop git wget unzip \
    openssh-sftp-server \
    && rm -rf /var/lib/apt/lists/*

# 清理可能存在的 uid=999 冲突（Ubuntu 默认没有，但安全起见）
RUN if getent passwd 999 > /dev/null 2>&1; then \
        userdel -r $(getent passwd 999 | cut -d: -f1); \
    fi && \
    if getent group 999 > /dev/null 2>&1; then \
        groupdel $(getent group 999 | cut -d: -f1); \
    fi

# 创建 container 用户（uid=999），家目录 /home/container
RUN useradd -m -u 999 -s /bin/bash container && \
    usermod -aG sudo container && \
    echo '%sudo ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

# 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 创建 SSH 运行时目录并授权
RUN mkdir -p /var/run/sshd && chown -R 999:999 /var/run/sshd

WORKDIR /home/container

ENTRYPOINT ["/usr/local/bin/java"]
CMD []
