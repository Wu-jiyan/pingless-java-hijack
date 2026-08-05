FROM alpine:latest

# 基础软件包（bash、curl、openssh-server、sudo）
RUN apk add --no-cache bash curl openssh-server sudo

# 额外实用工具（根据需要可删减）
RUN apk add --no-cache \
    python3 \
    py3-pip \
    vim \
    htop \
    git \
    wget \
    unzip

# 清理 /etc/passwd 和 /etc/group 中 uid/gid=999 的条目（避免冲突）
RUN sed -i '/^[^:]*:[^:]*:999:/d' /etc/passwd && \
    sed -i '/^[^:]*:[^:]*:999:/d' /etc/group

# 创建 container 用户（uid=999）
RUN adduser -D -h /home/container -u 999 container && \
    addgroup container wheel && \
    echo '%wheel ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

# 复制劫持脚本
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 创建 SSH 运行时目录
RUN mkdir -p /var/run/sshd && chown -R 999:999 /var/run/sshd

WORKDIR /home/container

ENTRYPOINT ["/usr/local/bin/java"]
CMD []
