# FinAI 项目部署指南

## 部署要求

### 系统要求
- 操作系统: Linux / macOS / Windows
- JDK: 17+
- Node.js: 18+
- 内存: 至少 4GB RAM
- 磁盘: 至少 10GB 可用空间

### 环境准备

1. **安装JDK 17**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# macOS
brew install openjdk@17

# 验证安装
java -version
```

2. **安装Node.js 18+**
```bash
# Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# macOS
brew install node@18

# 验证安装
node --version
npm --version
```

3. **安装Maven**
```bash
# Ubuntu/Debian
sudo apt install maven

# macOS
brew install maven

# 验证安装
mvn --version
```

## 配置说明

### 1. 环境变量配置

复制环境变量模板:
```bash
cp .env.example .env
```

编辑 `.env` 文件，填入必要的配置:

```bash
# LLM配置 (必填)
LLM_PROVIDER=claude
ANTHROPIC_API_KEY=your_anthropic_api_key_here

# 或使用OpenAI
# LLM_PROVIDER=openai
# OPENAI_API_KEY=your_openai_api_key_here

# 其他配置保持默认即可
```

### 2. 数据库配置

系统默认使用H2嵌入式数据库，无需额外配置。

如需使用MySQL/PostgreSQL，修改 `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/finai
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 3. 文件上传配置

默认文件上传大小限制为50MB，可在 `application.yml` 修改:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB
```

## 部署方式

### 方式一: 开发模式(推荐用于测试)

使用一键启动脚本:

**Linux/Mac:**
```bash
./run_all.sh
```

**Windows:**
```cmd
run_all.bat
```

停止服务:
```bash
./stop_all.sh
```

### 方式二: Docker部署(推荐用于生产)

1. **创建Docker镜像**

创建 `Dockerfile` (后端):
```dockerfile
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/finai-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

2. **创建docker-compose.yml**
```yaml
version: '3.8'

services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - LLM_PROVIDER=${LLM_PROVIDER}
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
    volumes:
      - ./data:/app/data
      - ./logs:/app/logs
      - ./outputs:/app/outputs

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
```

3. **启动服务**
```bash
docker-compose up -d
```

### 方式三: 手动部署

**后端部署:**

```bash
# 1. 构建
cd backend
mvn clean package -DskipTests

# 2. 启动
java -jar target/finai-backend-1.0.0.jar
```

**前端部署:**

```bash
# 1. 安装依赖
cd frontend
npm install

# 2. 构建生产版本
npm run build

# 3. 部署到Nginx
# 将 dist/ 目录内容复制到 nginx 的 html 目录
```

### Nginx配置示例

```nginx
server {
    listen 80;
    server_name your_domain.com;

    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 代理后端API
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 验证部署

### 1. 检查后端服务

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 预期响应:
# {"status":"UP"}
```

### 2. 检查前端服务

访问: http://localhost:5173 (开发模式) 或 http://localhost (生产模式)

### 3. 检查API文档

访问: http://localhost:8080/swagger-ui.html

### 4. 创建测试任务

```bash
curl -X POST http://localhost:8080/api/tasks \
  -F "companyCode=600519" \
  -F "companyName=贵州茅台" \
  -F "reportPeriod=2023A" \
  -F "analysisType=QUICK"
```

## 生产环境优化

### 1. JVM参数优化

```bash
java -jar \
  -Xms2g \
  -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  target/finai-backend-1.0.0.jar
```

### 2. 日志配置

修改 `application.yml`:
```yaml
logging:
  level:
    root: WARN
    com.finai: INFO
  file:
    name: /var/log/finai/application.log
    max-size: 100MB
    max-history: 30
```

### 3. 数据库连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 4. 性能监控

使用Spring Boot Actuator + Prometheus + Grafana:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## 备份与恢复

### 备份数据

```bash
# 备份数据库
cp data/finai.mv.db backup/finai_$(date +%Y%m%d).mv.db

# 备份上传文件
tar -czf backup/uploads_$(date +%Y%m%d).tar.gz data/uploads/

# 备份输出报告
tar -czf backup/outputs_$(date +%Y%m%d).tar.gz outputs/
```

### 恢复数据

```bash
# 恢复数据库
cp backup/finai_20240101.mv.db data/finai.mv.db

# 恢复文件
tar -xzf backup/uploads_20240101.tar.gz -C data/
tar -xzf backup/outputs_20240101.tar.gz -C ./
```

## 故障排查

### 后端无法启动

1. 检查JDK版本: `java -version`
2. 检查端口占用: `lsof -i:8080`
3. 查看日志: `tail -f logs/finai.log`

### 前端无法访问

1. 检查Node.js版本: `node --version`
2. 检查构建输出: `npm run build`
3. 检查Nginx配置: `nginx -t`

### 任务执行失败

1. 检查LLM API密钥是否正确
2. 查看审计日志: 访问 `/api/audit/tasks/{taskId}/logs`
3. 检查文件上传权限

## 安全建议

1. **密钥管理**: 不要将API密钥提交到Git仓库
2. **访问控制**: 在生产环境启用认证和授权
3. **HTTPS**: 使用SSL/TLS加密通信
4. **防火墙**: 限制不必要的端口访问
5. **定期更新**: 及时更新依赖包修复安全漏洞

## 支持与反馈

如遇问题，请查看:
- 项目文档: `docs/`
- API文档: http://localhost:8080/swagger-ui.html
- GitHub Issues: [项目仓库地址]
