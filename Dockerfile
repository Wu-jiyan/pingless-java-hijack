FROM alpine:latest

# 安装必要软件（openssh-server 提供 sshd 和 ssh-keygen）
RUN apk add --no-cache bash curl openssh-server

# 创建或修改 container 用户，使其 uid 为 999（消除 agent 警告）
RUN if getent passwd container > /dev/null 2>&1; then \
        usermod -u 999 container; \
    else \
        adduser -D -h /home/container -u 999 container; \
    fi

# 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 预先创建 SSH 运行时目录并授权给 container 用户
RUN mkdir -p /var/run/sshd && chown -R container:container /var/run/sshd

# 设置工作目录
WORKDIR /home/container

# 保留 root 权限以便后续可能在容器内安装其他软件（或按需切换用户）
