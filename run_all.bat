@echo off
REM FinAI 一键启动脚本 (Windows) - 简化版

echo ========================================
echo   FinAI 系统启动
echo ========================================
echo.

REM 检查Java
echo [1/6] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Java！
    echo.
    echo 请先安装JDK 17+
    echo 下载地址: https://adoptium.net/
    echo.
    pause
    exit /b 1
)
echo [完成] Java环境正常

REM 检查Maven
echo [2/6] 检查Maven环境...
call mvn -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Maven！
    echo.
    echo 请先安装Maven
    echo 方法1: choco install maven
    echo 方法2: https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)
echo [完成] Maven环境正常

REM 检查Node.js
echo [3/6] 检查Node.js环境...
node -v >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Node.js！
    echo.
    echo 请先安装Node.js 18+
    echo 下载地址: https://nodejs.org/
    echo.
    pause
    exit /b 1
)
echo [完成] Node.js环境正常

REM 检查.env
echo [4/6] 检查配置文件...
if not exist ".env" (
    echo [警告] 未找到.env文件
    if exist ".env.example" (
        echo 正在复制.env.example...
        copy .env.example .env >nul
        echo.
        echo [重要] 请编辑 .env 文件填入API密钥
        echo 然后重新运行此脚本
        echo.
        notepad .env
        pause
        exit /b 0
    ) else (
        echo [错误] 未找到.env.example
        pause
        exit /b 1
    )
)
echo [完成] 配置文件正常

REM 创建目录
echo [5/6] 创建必要目录...
if not exist "data\uploads" mkdir data\uploads
if not exist "logs" mkdir logs
if not exist "outputs\reports" mkdir outputs\reports
echo [完成] 目录创建完成

REM 构建后端
echo [6/6] 构建后端...
cd backend
echo 正在构建，请稍候...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [错误] 后端构建失败
    cd ..
    pause
    exit /b 1
)
echo [完成] 后端构建成功
cd ..

echo.
echo ========================================
echo   准备启动服务
echo ========================================
echo.

REM 启动后端
echo 启动后端服务 (端口 8080)...
cd backend
start "FinAI-Backend" java -jar target\finai-backend-1.0.0.jar
cd ..

echo 等待后端启动...
timeout /t 15 /nobreak >nul

REM 安装前端依赖
echo.
echo 检查前端依赖...
cd frontend
if not exist "node_modules" (
    echo 首次运行，安装前端依赖...
    call npm install
    if errorlevel 1 (
        echo [错误] 前端依赖安装失败
        cd ..
        pause
        exit /b 1
    )
)

REM 启动前端
echo 启动前端服务 (端口 5173)...
start "FinAI-Frontend" npm run dev
cd ..

echo.
echo ========================================
echo   启动完成！
echo ========================================
echo.
echo 前端地址: http://localhost:5173
echo 后端API:  http://localhost:8080
echo API文档:  http://localhost:8080/swagger-ui.html
echo.
echo 两个新窗口已打开：
echo - FinAI-Backend (后端服务)
echo - FinAI-Frontend (前端服务)
echo.
echo 请保持这两个窗口运行
echo 关闭窗口即可停止服务
echo.
echo 按任意键在浏览器中打开前端...
pause >nul

REM 打开浏览器
start http://localhost:5173

echo.
echo 正在运行中...
echo 按 Ctrl+C 或关闭窗口停止服务
echo.
