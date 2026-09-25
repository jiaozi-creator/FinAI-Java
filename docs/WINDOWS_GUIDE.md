# Windows 运行指南

## 前提条件

在Windows上运行FinAI项目，需要先安装以下软件：

### 1. 安装 JDK 17+

**方法一：使用 Oracle JDK**
1. 访问 https://www.oracle.com/java/technologies/downloads/#java17
2. 下载 Windows x64 Installer
3. 运行安装程序，按默认选项安装
4. 验证安装：
```cmd
java -version
```

**方法二：使用 OpenJDK（推荐）**
1. 访问 https://adoptium.net/
2. 下载 Temurin 17 (LTS)
3. 安装后验证：
```cmd
java -version
```

### 2. 安装 Maven

**方法一：使用 Chocolatey（推荐）**
```cmd
# 先安装 Chocolatey (以管理员身份运行PowerShell)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# 安装 Maven
choco install maven
```

**方法二：手动安装**
1. 访问 https://maven.apache.org/download.cgi
2. 下载 Binary zip archive (apache-maven-3.9.x-bin.zip)
3. 解压到 C:\Program Files\Apache\maven
4. 添加到系统环境变量：
   - 新建 `MAVEN_HOME` = `C:\Program Files\Apache\maven`
   - 编辑 `Path`，添加 `%MAVEN_HOME%\bin`
5. 验证安装：
```cmd
mvn -version
```

### 3. 安装 Node.js

1. 访问 https://nodejs.org/
2. 下载 Windows Installer (.msi) - 选择 LTS 版本 (18.x)
3. 运行安装程序
4. 验证安装：
```cmd
node --version
npm --version
```

## 快速启动步骤

### 第一步：配置环境变量

1. 在项目根目录，复制 `.env.example` 为 `.env`
2. 编辑 `.env` 文件，填入你的 API 密钥：

```bash
# 选择 LLM 提供商
LLM_PROVIDER=claude

# 填入你的 API 密钥
ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxxx

# 或者使用 OpenAI
# LLM_PROVIDER=openai
# OPENAI_API_KEY=sk-xxxxxxxxxxxxx
```

### 第二步：一键启动

**双击运行：**
```
run_all.bat
```

或者**在命令行中运行：**

```cmd
# 打开命令提示符(cmd)或PowerShell
cd FinAI-Java
run_all.bat
```

脚本会自动完成：
- ✅ 检查 Java 环境
- ✅ 检查 Node.js 环境
- ✅ 检查 .env 配置
- ✅ 构建后端项目
- ✅ 安装前端依赖
- ✅ 启动后端服务
- ✅ 启动前端服务

### 第三步：访问应用

启动成功后，浏览器访问：

- **前端界面**: http://localhost:5173
- **后端API**: http://localhost:8080
- **API文档**: http://localhost:8080/swagger-ui.html
- **H2数据库控制台**: http://localhost:8080/h2-console

## 手动启动（如果一键脚本失败）

### 启动后端

```cmd
# 1. 进入后端目录
cd backend

# 2. 构建项目（首次运行）
mvn clean package -DskipTests

# 3. 启动服务
java -jar target\finai-backend-1.0.0.jar

# 后端将在 8080 端口运行
```

### 启动前端（新开一个命令窗口）

```cmd
# 1. 进入前端目录
cd frontend

# 2. 安装依赖（首次运行）
npm install

# 3. 启动开发服务器
npm run dev

# 前端将在 5173 端口运行
```

## 常见问题解决

### 问题1：端口被占用

**8080端口被占用：**
```cmd
# 查找占用进程
netstat -ano | findstr :8080

# 结束进程
taskkill /PID <进程ID> /F
```

**5173端口被占用：**
```cmd
# 查找并结束
netstat -ano | findstr :5173
taskkill /PID <进程ID> /F
```

### 问题2：Maven构建失败

```cmd
# 清理并重新构建
cd backend
mvn clean
mvn install -DskipTests
```

### 问题3：前端依赖安装失败

```cmd
# 清理缓存重新安装
cd frontend
rmdir /s /q node_modules
del package-lock.json
npm install
```

### 问题4：Java版本不对

```cmd
# 检查Java版本
java -version

# 如果版本低于17，需要重新安装JDK 17+
```

### 问题5：JAVA_HOME未设置

```cmd
# 设置环境变量（以管理员身份运行）
setx JAVA_HOME "C:\Program Files\Java\jdk-17"
setx PATH "%PATH%;%JAVA_HOME%\bin"

# 重启命令提示符后生效
```

## 停止服务

### 方法一：关闭命令窗口
直接关闭运行服务的命令窗口

### 方法二：使用Ctrl+C
在运行服务的窗口按 `Ctrl+C` 停止

### 方法三：通过任务管理器
1. 打开任务管理器 (Ctrl+Shift+Esc)
2. 找到 `java.exe` 和 `node.exe` 进程
3. 结束任务

## 开发模式

### 后端热重载

使用 Spring Boot DevTools：
```cmd
cd backend
mvn spring-boot:run
```

### 前端热重载

前端默认支持热重载：
```cmd
cd frontend
npm run dev
```

修改代码后会自动刷新浏览器。

## 生产环境部署

### 构建生产版本

**后端：**
```cmd
cd backend
mvn clean package -DskipTests
# JAR文件在 target\finai-backend-1.0.0.jar
```

**前端：**
```cmd
cd frontend
npm run build
# 构建结果在 dist\ 目录
```

### 部署到服务器

**使用IIS部署前端：**
1. 将 `dist\` 目录内容复制到 IIS 网站根目录
2. 配置URL重写规则（需要安装URL Rewrite模块）

**使用Nginx部署前端：**
1. 下载 Nginx for Windows
2. 将 `dist\` 目录内容复制到 nginx\html
3. 配置 nginx.conf 代理后端API

**后端作为Windows服务：**
使用 [WinSW](https://github.com/winsw/winsw) 将Java应用注册为Windows服务

## IDE 推荐

### 后端开发
- **IntelliJ IDEA** (推荐) - https://www.jetbrains.com/idea/
- Eclipse
- Visual Studio Code + Java Extension Pack

### 前端开发
- **Visual Studio Code** (推荐) - https://code.visualstudio.com/
- WebStorm

## 性能优化建议

### JVM参数优化
```cmd
java -Xms2g -Xmx4g -XX:+UseG1GC -jar target\finai-backend-1.0.0.jar
```

### Maven构建加速
在 `backend\pom.xml` 同级目录创建 `.mvn\maven.config`：
```
-T 4
--batch-mode
```

## 技术支持

如遇到问题：
1. 检查日志文件：`logs\finai.log`
2. 查看 `docs\DEPLOYMENT.md` 详细文档
3. 访问API文档：http://localhost:8080/swagger-ui.html
4. 查看GitHub Issues

## 下一步

启动成功后，你可以：
1. 访问前端界面创建第一个分析任务
2. 上传财报PDF进行测试
3. 查看API文档了解接口调用
4. 修改配置文件自定义规则

祝使用愉快！🚀
