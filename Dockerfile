FROM alpine:latest

# 安装 bash、curl、openssh-server（提供 sshd 和 ssh-keygen）
RUN apk add --no-cache bash curl openssh-server

# 从 /etc/passwd 和 /etc/group 中删除所有 uid=999 或 gid=999 的行
# 注意：这可能会删除系统用户（如 nobody），但 Alpine 的 999 通常未被使用或无关紧要
RUN sed -i '/^[^:]*:[^:]*:999:/d' /etc/passwd && \
    sed -i '/^[^:]*:[^:]*:999:/d' /etc/group

# 创建 container 用户，uid=999，家目录 /home/container
RUN adduser -D -h /home/container -u 999 container

# 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 创建 SSH 运行时目录，并授权给 uid 999
RUN mkdir -p /var/run/sshd && chown -R 999:999 /var/run/sshd

# 设置工作目录（确保 uid 999 有写权限）
WORKDIR /home/container
