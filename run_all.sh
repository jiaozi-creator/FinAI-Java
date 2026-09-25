#!/bin/bash

# FinAI 一键启动脚本 (Linux/Mac)

set -e

echo "================================"
echo "  FinAI 系统启动脚本"
echo "================================"
echo ""

# 检查Java环境
echo "检查Java环境..."
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: Java版本过低，需要JDK 17+，当前版本: $JAVA_VERSION"
    exit 1
fi
echo "✓ Java版本检查通过"

# 检查Node环境
echo "检查Node.js环境..."
if ! command -v node &> /dev/null; then
    echo "错误: 未找到Node.js环境，请先安装Node.js 18+"
    exit 1
fi
echo "✓ Node.js环境检查通过"

# 检查.env文件
if [ ! -f ".env" ]; then
    echo "警告: 未找到.env文件，正在复制.env.example..."
    cp .env.example .env
    echo "请编辑.env文件，填入API密钥后重新运行此脚本"
    exit 1
fi
echo "✓ .env文件检查通过"

# 创建必要的目录
echo "创建目录..."
mkdir -p data/uploads data/raw data/structured data/manifests
mkdir -p outputs/reports outputs/evidence
mkdir -p logs
echo "✓ 目录创建完成"

# 构建后端
echo ""
echo "================================"
echo "  构建后端项目"
echo "================================"
cd backend

if [ ! -f "mvnw" ]; then
    echo "使用系统Maven..."
    mvn clean package -DskipTests
else
    echo "使用项目Maven Wrapper..."
    chmod +x mvnw
    ./mvnw clean package -DskipTests
fi

echo "✓ 后端构建完成"
cd ..

# 安装前端依赖
echo ""
echo "================================"
echo "  安装前端依赖"
echo "================================"
cd frontend

if [ ! -d "node_modules" ]; then
    echo "安装依赖包..."
    npm install
fi

echo "✓ 前端依赖安装完成"
cd ..

# 启动服务
echo ""
echo "================================"
echo "  启动服务"
echo "================================"

# 启动后端
echo "启动后端服务..."
cd backend
nohup java -jar target/finai-backend-1.0.0.jar > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
echo "后端PID: $BACKEND_PID"
cd ..

# 等待后端启动
echo "等待后端启动(最多30秒)..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✓ 后端启动成功"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "错误: 后端启动超时"
        kill $BACKEND_PID 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

# 启动前端
echo "启动前端服务..."
cd frontend
nohup npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "前端PID: $FRONTEND_PID"
cd ..

# 保存PID
echo $BACKEND_PID > .backend.pid
echo $FRONTEND_PID > .frontend.pid

echo ""
echo "================================"
echo "  FinAI 启动完成！"
echo "================================"
echo ""
echo "前端地址: http://localhost:5173"
echo "后端API:  http://localhost:8080"
echo "API文档:  http://localhost:8080/swagger-ui.html"
echo "H2控制台: http://localhost:8080/h2-console"
echo ""
echo "日志文件:"
echo "  后端: logs/backend.log"
echo "  前端: logs/frontend.log"
echo ""
echo "停止服务:"
echo "  ./stop_all.sh"
echo ""
