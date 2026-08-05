FROM alpine:latest

# 安装所有需要的软件包（包括 SFTP 子系统）
RUN apk add --no-cache \
    bash curl openssh-server sudo \
    python3 py3-pip vim htop git wget unzip \
    openssh-sftp-server

# 清理 uid/gid=999 冲突
RUN sed -i '/^[^:]*:[^:]*:999:/d' /etc/passwd && \
    sed -i '/^[^:]*:[^:]*:999:/d' /etc/group

# 创建 container 用户并加入 wheel 组
RUN adduser -D -h /home/container -u 999 container && \
    addgroup container wheel && \
    echo '%wheel ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

RUN mkdir -p /var/run/sshd && chown -R 999:999 /var/run/sshd

WORKDIR /home/container

ENTRYPOINT ["/usr/local/bin/java"]
CMD []
