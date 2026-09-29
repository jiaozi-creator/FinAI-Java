@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo FinAI startup
echo ========================================

java -version >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java was not found.
  exit /b 1
)
call mvn -version >nul 2>&1
if errorlevel 1 (
  echo ERROR: Maven was not found.
  exit /b 1
)
node -v >nul 2>&1
if errorlevel 1 (
  echo ERROR: Node.js was not found.
  exit /b 1
)

if not exist ".env" (
  if not exist ".env.example" (
    echo ERROR: .env.example was not found.
    exit /b 1
  )
  copy /y .env.example .env >nul
  echo Created .env from .env.example.
  echo Fill ALIYUN_API_KEY, or explicitly use mock mode for local development:
  echo LLM_PROVIDER=mock and DEV_MOCK_LLM=true and COMPETITION_MODE=false
  exit /b 0
)

if not exist "data\uploads" mkdir data\uploads
if not exist "logs" mkdir logs
if not exist "outputs\reports" mkdir outputs\reports

pushd backend
call mvn clean package -DskipTests -q
if errorlevel 1 (
  echo ERROR: Backend build failed.
  popd
  exit /b 1
)
start "FinAI-Backend" powershell -NoProfile -ExecutionPolicy Bypass -File "..\scripts\start-backend.ps1"
popd

powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\wait-backend.ps1"
if errorlevel 1 (
  echo ERROR: Backend did not become healthy. Check backend\logs\finai.log.
  exit /b 1
)

pushd frontend
if not exist "node_modules" call npm install
if errorlevel 1 (
  echo ERROR: Frontend dependency installation failed.
  popd
  exit /b 1
)
start "FinAI-Frontend" npm run dev
popd

echo Frontend: http://localhost:5173
echo Backend:  http://localhost:8080
start http://localhost:5173
endlocal
