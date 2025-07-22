# 使用一个轻量的 JRE 镜像作为运行环境
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 从构建上下文复制 JAR 文件
COPY target/jrebel-license-1.0-SNAPSHOT-jar-with-dependencies.jar ./app.jar

# 暴露端口，可以由运行时指定
# 默认使用 8081
ENV PORT=8081
EXPOSE 8081

# 使用shell形式以确保环境变量能被替换
ENTRYPOINT java -jar app.jar -p ${PORT}