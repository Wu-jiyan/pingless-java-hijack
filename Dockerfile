FROM alpine:latest

# 安装 bash、curl、openssh-server（提供 sshd 和 ssh-keygen）
RUN apk add --no-cache bash curl openssh-server

# 创建 container 用户，不指定 uid，让系统自动分配（通常为 1000）
RUN adduser -D -h /home/container container

# 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 创建 SSH 运行时目录并授权
RUN mkdir -p /var/run/sshd && chown -R container:container /var/run/sshd

WORKDIR /home/container
