FROM alpine:latest

RUN apk add --no-cache bash curl openssh-server

RUN sed -i '/^[^:]*:[^:]*:999:/d' /etc/passwd && \
    sed -i '/^[^:]*:[^:]*:999:/d' /etc/group

RUN adduser -D -h /home/container -u 999 container

# ----- 安装 sudo，并将 container 加入 wheel 组（用 addgroup）-----
RUN apk add --no-cache sudo && \
    addgroup container wheel && \
    echo '%wheel ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers

COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

RUN mkdir -p /var/run/sshd && chown -R 999:999 /var/run/sshd

WORKDIR /home/container

ENTRYPOINT ["/usr/local/bin/java"]
CMD []
