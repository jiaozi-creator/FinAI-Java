# 快速开始 - Windows 用户指南

> 如果你在Windows系统上运行FinAI，请按照以下步骤操作。

## 📋 准备工作

### 必需软件

1. **Java 17+** 
   - 下载地址: https://adoptium.net/
   - 安装后验证: `java -version`

2. **Maven 3.6+**
   - 使用Chocolatey安装: `choco install maven`
   - 或手动下载: https://maven.apache.org/download.cgi
   - 验证: `mvn -version`

3. **Node.js 18+**
   - 下载地址: https://nodejs.org/
   - 验证: `node --version` 和 `npm --version`

## 🚀 启动步骤

### 1. 配置环境

```cmd
# 复制环境变量模板
copy .env.example .env

# 用记事本编辑 .env 文件
notepad .env
```

填入你的API密钥：
```
LLM_PROVIDER=claude
ANTHROPIC_API_KEY=你的密钥
```

### 2. 一键启动

双击运行：
```
run_all.bat
```

或在命令提示符中：
```cmd
cd FinAI-Java
run_all.bat
```

### 3. 访问应用

- 前端: http://localhost:5173
- 后端: http://localhost:8080
- API文档: http://localhost:8080/swagger-ui.html

## 🔧 手动启动

如果一键脚本失败，按以下步骤手动启动：

### 启动后端
```cmd
cd backend
mvn clean package -DskipTests
java -jar target\finai-backend-1.0.0.jar
```

### 启动前端（新开命令窗口）
```cmd
cd frontend
npm install
npm run dev
```

## ❓ 常见问题

### 端口被占用
```cmd
# 查看端口占用
netstat -ano | findstr :8080

# 结束进程
taskkill /PID 进程ID /F
```

### Java版本不对
确保安装了JDK 17或更高版本，并设置了JAVA_HOME环境变量。

### Maven构建失败
```cmd
cd backend
mvn clean install -U -DskipTests
```

## 📚 详细文档

完整的Windows运行指南请查看：[WINDOWS_GUIDE.md](WINDOWS_GUIDE.md)

## 🎯 创建第一个任务

1. 访问 http://localhost:5173
2. 点击"创建任务"
3. 填写公司信息
4. 上传财报PDF（可选）
5. 点击"创建任务"
6. 查看实时进度
7. 任务完成后查看报告

## 💡 提示

- 首次启动需要下载依赖，可能需要几分钟
- 确保有稳定的网络连接
- 建议使用Chrome或Edge浏览器
- 开发模式支持热重载，修改代码后自动刷新

---

**需要帮助？** 查看 [docs/WINDOWS_GUIDE.md](docs/WINDOWS_GUIDE.md) 获取完整指南
