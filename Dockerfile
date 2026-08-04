FROM alpine:latest

# 安装 bash, curl, openssh-server
RUN apk add --no-cache bash curl openssh-server

# 创建 container 用户（Pterodactyl 要求）
RUN adduser -D -h /home/container container

# 核心：把 java 脚本复制到 /usr/local/bin/，覆盖原 java 命令
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 预先创建 SSH 运行时需要的目录并设置权限
RUN mkdir -p /var/run/sshd && chown -R container:container /var/run/sshd

# 工作目录
WORKDIR /home/container

# 注意：不切换用户，构建时仍为 root，方便安装软件
