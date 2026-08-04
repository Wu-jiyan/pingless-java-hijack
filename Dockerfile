FROM alpine:latest

# 安装 bash, curl, openssh-server（提供 sshd 和 ssh-keygen）
RUN apk add --no-cache bash curl openssh-server

# 创建 container 用户，并指定 uid=999（与 agent 期望一致）
RUN adduser -D -h /home/container -u 999 container

# 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 预先创建 SSH 运行时目录并授权
RUN mkdir -p /var/run/sshd && chown -R container:container /var/run/sshd

WORKDIR /home/container

# 不切换用户，构建时仍为 root 以便安装软件
