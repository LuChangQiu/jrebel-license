[English Version](README.md)

# 🚀 JRebel & JetBrains 许可证服务器完整部署指南

## 📝 项目概述

本项目是一个用Java编写的JRebel和JetBrains产品的许可证服务器，通过模拟官方验证服务器响应来实现激活功能。支持JRebel、JRebel for Android、XRebel以及JetBrains全系列IDE。

**免责声明**: 本项目仅供学习和教育目的使用，请勿用于商业用途。请支持正版软件。

## 🛠️ 环境准备

### 必要条件
- JDK 17或更高版本
- Maven 3.6+
- Docker 20+
- 一个公网域名
- SSL证书（通过Let's Encrypt免费获取）

## 🔍 源码分析

该项目基于嵌入式Jetty服务器实现，主要工作原理如下：

1. **MainServer类**：处理所有HTTP请求，根据不同的URL路径分发到不同处理方法
2. **激活端点**：
   - `/rpc/ping.action`、`/rpc/obtainTicket.action` - JetBrains产品使用
   - `/jrebel/leases`、`/jrebel/validate-connection` - JRebel新版本使用
   - `/{guid}` - JRebel 2018.1以上版本的激活入口

**关键注意点**：
- JetBrains产品直接访问根路径 `/` 激活
- JRebel 2018.1及更高版本需要使用 `/{guid}` 格式的URL，但这个GUID路径并不会被服务器特殊处理

## 📦 打包部署

### 1. 项目打包

```bash
# 克隆项目
git clone https://github.com/LuChangQiu/jrebel-license.git
cd jrebel-license

# 打包项目
mvn clean package
```

打包后在`target`目录生成`jrebel-license-1.0-SNAPSHOT-jar-with-dependencies.jar`文件。

### 2. 修改Dockerfile

确保Dockerfile内容如下，修正环境变量替换问题：

```dockerfile
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
```

### 3. 构建Docker镜像

```bash
# 构建镜像
docker build -t jrebel-ls .
```

### 4. 运行容器

```bash
# 以后台模式启动容器
docker run -d --name jrebel-ls --restart always -p 9001:9001 -e PORT=9001 jrebel-ls
```

### 5. 验证容器运行状态

```bash
# 检查容器是否正常运行
docker ps

# 查看容器日志确认服务启动
docker logs jrebel-ls
```

确认日志中显示正确的启动端口为9001：
```bash
License Server started at http://localhost:9001
JetBrains Activation address was: http://localhost:9001/
JRebel 7.1 and earlier version Activation address was: http://localhost:9001/{tokenname}, with any email.
JRebel 2018.1 and later version Activation address was: http://localhost:9001/{guid}(eg:http://localhost:9001/d3efee46-90ba-4b6e-b95c-731d09d5fa3b), with any email.
```


### 6. 本地连接测试

```bash
# 测试基本连接
curl http://localhost:9001/

# 测试JetBrains激活路径
curl http://localhost:9001/rpc/ping.action?salt=1234

# 测试JRebel激活路径
curl "http://localhost:9001/jrebel/leases?randomness=123&username=test@test.com&guid=abc"
```

## 🔒 Nginx配置

### 1. 申请SSL证书

```bash
# 安装certbot（如果尚未安装）
apt-get update
apt-get install certbot python3-certbot-nginx

# 申请证书
certbot --nginx -d jrebel.example.com
```

### 2. 创建Nginx配置文件

创建`/etc/nginx/conf.d/jrebel.conf`文件：

```nginx
# /etc/nginx/conf.d/jrebel.conf

# HTTP 服务器块 - 将HTTP重定向到HTTPS
server {
    listen 80;
    server_name jrebel.example.com;

    location /.well-known/acme-challenge/ {
        root /usr/share/nginx/html/share/;
        allow all;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS 服务器块
server {
    listen 443 ssl http2;
    server_name jrebel.example.com;

    ssl_certificate /etc/letsencrypt/live/jrebel.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/jrebel.example.com/privkey.pem;
    ssl_session_timeout 5m;
    ssl_ciphers ECDHE-RSA-AES128-GCM-SHA256:ECDHE:ECDH:AES:HIGH:!NULL:!aNULL:!MD5:!ADH:!RC4;
    ssl_protocols TLSv1.1 TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;

    location / {
        proxy_pass http://127.0.0.1:9001;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # 超时和缓冲设置
        proxy_connect_timeout 300;
        proxy_send_timeout 300;
        proxy_read_timeout 300;
        proxy_buffer_size 128k;
        proxy_buffers 4 256k;
        proxy_busy_buffers_size 256k;
        proxy_http_version 1.1;
        
        # 防止连接重置问题
        proxy_set_header Connection "";
        keepalive_timeout 65;
    }
}
```

### 3. 验证并重载Nginx配置

```bash
# 检查Nginx配置是否正确
nginx -t

# 重载Nginx配置
systemctl reload nginx
```

## 🌐 DNS配置

### 1. 添加域名A记录

登录您的域名DNS管理平台，添加以下A记录：
jrebel.example.com A记录 指向您服务器的IP地址


### 2. 等待DNS生效

DNS记录通常需要一段时间才能全球生效，一般为几分钟到48小时不等。您可以使用以下命令检查：

```bash
nslookup jrebel.example.com
```

## ☁️ CloudFlare设置

如果您使用CloudFlare作为CDN和DNS服务：

### 1. 域名代理设置

1. 登录CloudFlare控制面板
2. 找到域名`example.com`
3. 在DNS记录中找到`jrebel.example.com`条目
4. 将云图标设置为灰色（仅DNS模式），暂时关闭代理功能

### 2. SSL/TLS设置

1. 进入"SSL/TLS"选项卡
2. 将加密模式设置为"完全"或"完全（严格）"

### 3. Page Rules设置

1. 创建规则指向`jrebel.example.com/*`
2. 设置"Cache Level"为"Bypass"

## 🔧 问题排查

### 1. 检查容器状态和日志

```bash
# 检查容器运行状态
docker ps

# 查看JRebel服务日志
docker logs -f jrebel-ls

# 查看Nginx错误日志
tail -f /var/log/nginx/error.log

# 查看Nginx访问日志
tail -f /var/log/nginx/access.log
```

### 2. 常见问题解决方案

#### 502 Bad Gateway错误

原因：
- Nginx无法连接到后端JRebel服务
- 端口配置不匹配
- Docker容器未正常运行

解决：
1. 确认Docker容器正常运行：`docker ps`
2. 验证容器内服务监听正确端口：`docker logs jrebel-ls`
3. 修正Nginx配置中的端口号
4. 重启容器和Nginx：
   ```bash
   docker restart jrebel-ls
   systemctl restart nginx
   ```

#### 404错误或无法激活

原因：
- 客户端激活URL格式错误
- 配置文件未正确加载

解决：
1. 确认激活URL格式（不要在JRebel客户端添加GUID）
2. 再次检查Nginx配置
3. 测试API端点：
   ```bash
   curl https://jrebel.example.com/rpc/ping.action?salt=1234
   ```

## ✅ 激活验证

### JetBrains产品激活 （新版废弃）

1. 打开JetBrains产品（如IntelliJ IDEA）
2. 选择"License Server"选项
3. 输入地址：`https://jrebel.example.com`
4. 点击激活

### JRebel产品激活

1. 输入URL：`https://jrebel.example.com/{tokenname}`（任意token名）
2. 输入任意电子邮件地址
3. 点击激活


## 📝 总结

通过以上步骤，您应该已经成功部署了JRebel和JetBrains许可证服务器，并且可以通过您的自定义域名访问。如果在任何步骤中遇到问题，请参考问题排查章节或查看对应服务的日志。

请记住，这个项目仅供学习和教育目的使用，请支持正版软件。
