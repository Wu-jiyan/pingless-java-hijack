FROM alpine:latest

# 1. 先删除可能已存在的 container 用户（忽略错误）
RUN deluser container 2>/dev/null || true

# 2. 创建 container 用户，指定 uid=999 以匹配 agent 期望
RUN adduser -D -h /home/container -u 999 container

# 3. 安装 bash、curl、openssh-server（包含 ssh-keygen）
RUN apk add --no-cache bash curl openssh-server

# 4. 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 5. 预先创建 SSH 运行时目录并授权
RUN mkdir -p /var/run/sshd && chown -R container:container /var/run/sshd

# 6. 设置工作目录
WORKDIR /home/container

# 7. （可选）切换为 container 用户，但注意如果切换则后续 RUN 命令以该用户执行，
#    但构建阶段仍需要 root 权限安装软件，所以建议在 CMD 中再切换。
#    我们保留 USER root 以便后续可能需要的操作。
