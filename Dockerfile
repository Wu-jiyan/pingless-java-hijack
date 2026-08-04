FROM alpine:latest

# 安装 bash、curl、openssh-server
RUN apk add --no-cache bash curl openssh-server

# 1. 检查 uid 999 是否被占用，如果占用则删除该用户（忽略错误）
RUN if getent passwd 999 > /dev/null 2>&1; then \
        deluser $(getent passwd 999 | cut -d: -f1) 2>/dev/null || true; \
    fi

# 2. 创建 container 用户，uid=999，家目录 /home/container
RUN adduser -D -h /home/container -u 999 container

# 3. 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 4. 创建 SSH 运行时目录，确保 uid 999 可写
RUN mkdir -p /var/run/sshd && chown -R 999:999 /var/run/sshd

# 5. 设置工作目录（确保 uid 999 有写权限）
WORKDIR /home/container
