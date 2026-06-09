# JRebel License Server - GraalVM Native Image版本

本项目已添加GraalVM Native Image支持，可以编译为Windows .exe可执行程序。

## 快速开始

### 前置要求

1. **GraalVM** - 版本21或更高（包含native-image工具）
2. **Visual Studio Build Tools** - 用于Windows上的本地编译
3. **Maven** - 版本3.6+

详细的环境准备说明请参考：[构建说明文档](./docs/build-instructions.md)

### 构建Native Image

```powershell
# 克隆或进入项目目录
cd c:\Users\zc\Downloads\jrebel-license-master

# 使用Maven构建Native Image
mvn clean package -Pnative
```

构建完成后，可执行文件位于：`target\jrebel-license.exe`

### 运行

```powershell
# 使用默认端口18081
.\target\jrebel-license.exe

# 指定自定义端口
.\target\jrebel-license.exe -p 9001
```

### 测试

```powershell
# 访问主页
curl http://localhost:18081/

# 测试JetBrains激活端点
curl "http://localhost:18081/rpc/ping.action?salt=test123"

# 测试JRebel验证端点
curl http://localhost:18081/jrebel/validate-connection
```

## 项目结构

```
jrebel-license-master/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/vvvtimes/
│       │       ├── server/
│       │       │   └── MainServer.java          # 主服务器类
│       │       ├── JrebelUtil/                   # JRebel工具类
│       │       └── util/                         # 通用工具类
│       └── resources/
│           └── META-INF/
│               └── native-image/                 # GraalVM配置文件
│                   ├── reflect-config.json       # 反射配置
│                   ├── resource-config.json      # 资源配置
│                   ├── jni-config.json          # JNI配置
│                   ├── proxy-config.json        # 动态代理配置
│                   └── native-image.properties  # 构建属性
├── pom.xml                                       # Maven配置（含Native Image插件）
└── README_NATIVE.md                              # 本文件
```

## GraalVM Native Image配置说明

### Maven插件配置

在`pom.xml`中已配置`native-maven-plugin`，主要参数：

- **主类**: `com.vvvtimes.server.MainServer`
- **输出名称**: `jrebel-license`
- **构建选项**:
  - `--no-fallback`: 禁用JVM回退模式
  - `--enable-http/https`: 启用HTTP/HTTPS支持
  - `--initialize-at-build-time`: 构建时初始化Jetty日志类
  - `--initialize-at-run-time`: 运行时初始化BouncyCastle DRBG

### 反射配置

由于项目使用了以下需要反射的组件，已在`reflect-config.json`中配置：

- **Jetty服务器**: Server, Request, AbstractHandler等
- **JSON处理**: net.sf.json.JSONObject, JSONArray
- **加密库**: BouncyCastle Provider和RSA相关类
- **Servlet API**: HttpServletRequest, HttpServletResponse

### 故障排除

如果遇到反射或资源加载错误，可以使用GraalVM的tracing agent生成更完整的配置：

```powershell
# 使用agent运行JAR包
java -agentlib:native-image-agent=config-output-dir=src\main\resources\META-INF\native-image ^
  -jar target\jrebel-license-1.0-SNAPSHOT-jar-with-dependencies.jar -p 18081

# 测试所有功能后停止，配置文件会自动更新
# 然后重新构建Native Image
```

更多故障排除信息请参考：[构建说明文档](./docs/build-instructions.md)

## 部署

生成的`jrebel-license.exe`是完全独立的可执行文件：

✅ **无需安装Java环境**  
✅ **启动速度快**  
✅ **内存占用低**  
✅ **可直接部署到其他Windows机器**

### 部署步骤

1. 复制`target\jrebel-license.exe`到目标服务器
2. 运行：`jrebel-license.exe -p 端口号`
3. （可选）配置为Windows服务或使用任务计划程序自动启动

## 性能对比

| 指标 | JAR包运行 | Native Image |
|------|----------|--------------|
| 启动时间 | ~3-5秒 | ~0.1-0.5秒 |
| 内存占用 | ~150-200MB | ~50-80MB |
| 部署大小 | ~15MB + JRE | ~30-50MB（独立） |
| 需要Java | ✅ 是 | ❌ 否 |

## 许可证和免责声明

本项目仅供学习和教育目的使用，请勿用于商业用途。请支持正版软件。

## 参考文档

- [GraalVM官方文档](https://www.graalvm.org/latest/docs/)
- [Native Image构建说明](./docs/build-instructions.md)
- [原项目README](./README_ZH.md)
