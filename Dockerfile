FROM alpine:latest

# 安装 bash 和 curl（其他工具按需加）
RUN apk add --no-cache bash curl

# 创建 container 用户（Pterodactyl 要求）
RUN adduser -D -h /home/container container

# 核心：把 java 脚本复制到 /usr/local/bin/，覆盖原 java 命令
COPY java /usr/local/bin/java
RUN chmod +x /usr/local/bin/java

# 默认工作目录
WORKDIR /home/container

# 暂时不切换到 container 用户
# USER container

# 不设 CMD/ENTRYPOINT，因为面板会传 java -Xms... 来执行
