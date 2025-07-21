# 使用一个轻量的 JRE 镜像作为运行环境
# 请确保你的服务器可以拉取这个镜像
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 从构建上下文 (你执行 docker build 命令的目录) 复制 JAR 文件
# 请确保 Dockerfile 和 .jar 文件在同一个目录下
COPY jrebel-license-1.0-SNAPSHOT-jar-with-dependencies.jar ./app.jar

# 暴露端口，可以由运行时指定
# 默认使用 8081
ENV PORT=8081
EXPOSE 8081

# 设置容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "app.jar", "-p", "${PORT}"] 