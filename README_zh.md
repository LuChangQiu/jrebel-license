# JRebel & JetBrains 许可证服务器

[English Readme](README.md)

这是一个用 Java 编写的，为 Jrebel & JetBrains 全家桶产品提供授权服务的非官方许可证服务器。同时它也支持 JRebel for Android 和 XRebel。

***
**免责声明: 本项目仅供学习和教育目的使用，请勿用于商业用途。请支持正版软件。**
***

## 🚀 项目简介

本项目通过模拟官方许可证服务器的验证流程，为 JetBrains IDE、JRebel 等开发工具提供本地激活服务。它基于一个轻量级的嵌入式 Jetty 服务器来处理激活请求。

### 主要功能

*   支持 JetBrains 全系列 IDE (如 IntelliJ IDEA, PyCharm, WebStorm 等)。
*   支持 JRebel, JRebel for Android, XRebel。
*   支持通过 Maven 或 Gradle 运行和打包。
*   提供 Docker 镜像，方便快速部署。

## 🛠️ 如何使用

### 环境要求

*   JDK 17 或更高版本
*   Maven 或 Gradle

### 1. 通过 Maven 运行

克隆项目到本地后，在项目根目录执行以下命令：

```bash
# 编译项目
mvn compile 

# 运行服务器 (默认端口 8081)
mvn exec:java -Dexec.mainClass="com.vvvtimes.server.MainServer" -Dexec.args="-p 8081"
```

### 2. 打包为可执行 JAR

你可以将项目打包成一个包含所有依赖的可执行 JAR 文件。

```bash
# 使用 Maven 打包
mvn package
```

打包完成后，`target` 目录下会生成 `jrebel-license-1.0-SNAPSHOT-jar-with-dependencies.jar` 文件。通过以下命令运行：

```bash
java -jar target/jrebel-license-1.0-SNAPSHOT-jar-with-dependencies.jar -p 8081
```

### 3. 通过 Docker 部署

项目中已包含 `Dockerfile`，可以轻松构建和运行 Docker 容器。

```bash
# 1. (如果需要) 使用 Maven 打包项目
mvn package 

# 2. 构建 Docker 镜像
docker build -t jrebel-ls .

# 3. 以后台模式运行容器
# 这里将容器的 9001 端口映射到宿主机的 9001 端口
docker run -d --name jrebel-ls --restart always -p 9001:9001 -e PORT=9001 jrebel-ls
```

## ⚙️ 激活配置

服务器成功运行后，请使用以下地址在你的工具中进行激活：

*   **JetBrains IDE**:
    *   在激活窗口选择 "License server"。
    *   输入地址: `http://localhost:8081` (请根据你的实际IP和端口修改)。

*   **JRebel**:
    *   **JRebel 2018.1 及之后版本**:
        *   选择 "Connect to online licensing service"。
        *   在第一个输入框中输入 `http://localhost:8081/{GUID}`，其中 `{GUID}` 可以是任意合法的 GUID (例如: `http://localhost:8081/f33f6de8-4a43-479c-8af1-3c224673c64c`)。你可以使用在线 GUID 生成器创建一个。
        *   第二个输入框填入任意格式正确的邮箱地址。
    *   **JRebel 7.1 及更早版本**:
        *   激活地址格式为: `http://localhost:8081/{tokenname}`。

## 📂 代码简述

项目的核心逻辑非常简单，主要代码位于 `src/main/java/com/vvvtimes/server/MainServer.java`。

```java
// src/main/java/com/vvvtimes/server/MainServer.java

public class MainServer extends AbstractHandler {

    public static void main(String[] args) throws Exception {
        // ... 解析命令行参数，获取端口号 ...

        // 创建并启动 Jetty 服务器
        Server server = new Server(Integer.parseInt(port));
        server.setHandler(new MainServer());
        server.start();
        
        System.out.println("License Server started at http://localhost:" + port);
        // ... 打印激活地址 ...
        
        server.join();
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        System.out.println("Request target: " + target);
        
        // 根据不同的请求路径(target)，分发到不同的处理方法
        if (target.equals("/rpc/ping.action")) {
            pingHandler(target, baseRequest, request, response);
        } else if (target.equals("/rpc/obtainTicket.action")) {
            obtainTicketHandler(target, baseRequest, request, response);
        } else if (target.equals("/jrebel/leases")) {
            jrebelLeasesHandler(target, baseRequest, request, response);
        } 
        // ... 其他请求处理 ...
    }
    
    // ... 具体请求的处理方法，例如 pingHandler, obtainTicketHandler 等 ...
}
```

`MainServer` 类继承了 Jetty 的 `AbstractHandler`，并重写 `handle` 方法来处理所有传入的 HTTP 请求。它通过判断请求的 URL 路径，调用相应的方法来伪造(mock)返回官方服务器的响应数据（JSON 或 XML 格式），从而实现激活。

