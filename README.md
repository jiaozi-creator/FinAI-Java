# FinAI - 金融投研智能体系统

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.2.0-blue.svg)](https://reactjs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 项目简介

FinAI 是一个基于大语言模型的金融投研智能体系统，旨在为北京市大学生金融人工智能竞赛提供完整的解决方案。系统能够自动完成财务报告分析、异常检测、估值建模等任务，并提供完整的证据溯源和审计功能。

### 核心功能

- ✅ **财务报告分析**: 自动提取和分析上市公司财务数据
- ✅ **异常信号检测**: 基于规则引擎检测财务异常
- ✅ **自动化估值**: DCF、相对估值、历史分位等多维度估值
- ✅ **证据溯源管理**: 完整记录所有数据来源和计算过程
- ✅ **智能体编排**: 支持多智能体协同工作
- ✅ **审计日志**: 所有操作可追溯、可复现

### 技术特点

- 🚀 **现代化架构**: Spring Boot 3 + React 18
- 🤖 **AI 驱动**: 集成 LangChain4j，支持 Claude、GPT 等多种模型
- 📊 **数据处理**: PDF 解析、表格提取、OCR 识别
- 🔍 **规则引擎**: YAML 配置化的异常检测规则
- 💾 **灵活存储**: 支持 H2、MySQL、PostgreSQL
- 📈 **可视化**: Ant Design + Recharts 图表展示

## 项目结构

```
FinAI-Java/
├── backend/                    # 后端服务 (Spring Boot)
│   ├── src/main/java/com/finai/
│   │   ├── model/             # 数据模型
│   │   ├── repository/        # 数据访问层
│   │   ├── service/           # 业务逻辑层
│   │   ├── controller/        # API 控制器
│   │   ├── config/            # 配置类
│   │   └── exception/         # 异常处理
│   ├── src/main/resources/
│   │   └── application.yml    # 应用配置
│   └── pom.xml               # Maven 配置
├── frontend/                  # 前端应用 (React)
│   ├── src/
│   │   ├── components/       # React 组件
│   │   ├── pages/            # 页面组件
│   │   ├── services/         # API 服务
│   │   └── App.jsx           # 主应用
│   ├── package.json
│   └── vite.config.js
├── config/                    # 配置文件
│   ├── field_dict.yaml       # 字段字典
│   ├── formulas.yaml         # 计算公式
│   ├── rules.yaml            # 异常检测规则
│   └── companies.yaml        # 样本公司
├── data/                      # 数据目录
│   ├── uploads/              # 上传文件
│   ├── raw/                  # 原始数据
│   ├── structured/           # 结构化数据
│   └── manifests/            # 数据清单
├── outputs/                   # 输出目录
│   ├── reports/              # 分析报告
│   └── evidence/             # 证据文件
├── docs/                      # 文档
│   └── DEPLOYMENT.md         # 部署文档
├── .env.example              # 环境变量模板
├── run_all.sh                # 一键启动脚本 (Linux/Mac)
├── run_all.bat               # 一键启动脚本 (Windows)
└── README.md                 # 本文件
```

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.6+
- (可选) Docker

### 安装步骤

1. **克隆项目**

```bash
git clone <repository-url>
cd FinAI-Java
```

2. **配置环境变量**

```bash
cp .env.example .env
# 编辑 .env 文件，填入 API 密钥
```

至少需要配置以下内容:
```bash
# 选择 LLM 提供商
LLM_PROVIDER=claude  # 或 openai

# API 密钥
ANTHROPIC_API_KEY=your_anthropic_api_key_here
# 或
OPENAI_API_KEY=your_openai_api_key_here
```

3. **一键启动**

**Linux/Mac:**
```bash
chmod +x run_all.sh
./run_all.sh
```

**Windows:**
```cmd
run_all.bat
```

4. **访问应用**

- 前端: http://localhost:5173
- 后端API: http://localhost:8080
- API文档: http://localhost:8080/swagger-ui.html
- H2控制台: http://localhost:8080/h2-console

### 手动启动

如果一键脚本无法运行，可以手动启动:

**后端:**
```bash
cd backend
mvn clean package -DskipTests
java -jar target/finai-backend-1.0.0.jar
```

**前端:**
```bash
cd frontend
npm install
npm run dev
```

## 使用指南

### 1. 创建分析任务

访问前端应用，点击"创建任务"，填写:
- 公司代码 (例如: 600519)
- 公司名称 (例如: 贵州茅台)
- 报告期 (例如: 2023A)
- 分析类型 (快速/标准/完整/仅估值)
- (可选) 上传财报PDF

### 2. 查看任务进度

在"任务列表"页面可以实时查看任务状态和进度。

### 3. 查看分析报告

任务完成后，点击"查看报告"可以查看完整的分析结果，包括:
- 财务指标分析
- 异常信号检测
- 财务质量评估
- 估值结果(如果选择)
- 证据溯源

### 4. API 调用

也可以通过 API 直接调用:

```bash
# 创建任务
curl -X POST http://localhost:8080/api/tasks \
  -F "companyCode=600519" \
  -F "companyName=贵州茅台" \
  -F "reportPeriod=2023A" \
  -F "analysisType=FULL" \
  -F "file=@report.pdf"

# 查询任务状态
curl http://localhost:8080/api/tasks/{taskId}

# 获取分析报告
curl http://localhost:8080/api/tasks/{taskId}/report
```

## 配置说明

### 异常检测规则

编辑 `config/rules.yaml` 可以自定义异常检测规则:

```yaml
rules:
  - id: "revenue_profit_mismatch"
    name: "增收不增利"
    severity: "medium"
    conditions:
      - field: "revenue_growth_yoy"
        operator: ">"
        threshold: 0
      - field: "net_profit_growth_yoy"
        operator: "<"
        threshold: 0
```

### 字段字典

`config/field_dict.yaml` 定义了所有需要提取的财务字段及其别名。

### 计算公式

`config/formulas.yaml` 定义了所有财务指标的计算公式。

## 开发指南

### 后端开发

```bash
cd backend

# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 清理
mvn clean
```

### 前端开发

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build

# 代码检查
npm run lint
```

### 添加新的分析功能

1. 在 `backend/src/main/java/com/finai/service/` 创建服务类
2. 实现业务逻辑
3. 在 `controller` 中添加 API 端点
4. 在前端 `services/api.js` 中添加 API 调用
5. 创建对应的 React 组件展示结果

## 技术架构

### 后端技术栈

- **框架**: Spring Boot 3.2.0
- **AI**: LangChain4j (支持 Claude、GPT)
- **数据库**: H2 (开发)、MySQL/PostgreSQL (生产)
- **PDF处理**: Apache PDFBox
- **Excel处理**: Apache POI
- **API文档**: SpringDoc OpenAPI (Swagger)
- **监控**: Spring Boot Actuator + Prometheus

### 前端技术栈

- **框架**: React 18.2
- **UI库**: Ant Design 5
- **路由**: React Router 6
- **HTTP**: Axios
- **图表**: Recharts
- **构建**: Vite 5

### 数据流

```
用户上传PDF → PDF解析 → 字段提取 → 
→ LLM智能解析 → 数据验证 → 
→ 财务指标计算 → 异常检测 → 
→ 估值建模 → 报告生成 → 用户查看
```

## 部署

详细部署文档请查看 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

### Docker 部署 (推荐)

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

## 测试

### 单元测试

```bash
# 后端测试
cd backend
mvn test

# 前端测试
cd frontend
npm test
```

### 集成测试

参考 `docs/TESTING.md`

## 常见问题

### 1. 后端启动失败

- 检查 Java 版本: `java -version` (需要 17+)
- 检查端口占用: `lsof -i:8080`
- 查看日志: `logs/finai.log`

### 2. 前端无法访问后端

- 检查 CORS 配置
- 确认后端已启动
- 检查代理配置 `vite.config.js`

### 3. LLM 调用失败

- 确认 API 密钥正确
- 检查网络连接
- 查看审计日志

### 4. PDF 解析失败

- 确认 PDF 格式正确
- 检查文件大小限制
- 查看错误日志

## 贡献指南

欢迎提交 Issue 和 Pull Request!

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- 项目主页: [GitHub Repository]
- 问题反馈: [GitHub Issues]
- 邮箱: [your-email@example.com]

## 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [React](https://reactjs.org/)
- [Ant Design](https://ant.design/)
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [Apache PDFBox](https://pdfbox.apache.org/)

## 更新日志

### v1.0.0 (2024-01)

- ✨ 初始版本发布
- ✅ 完整的后端API
- ✅ React前端界面
- ✅ 财务分析功能
- ✅ 异常检测功能
- ✅ 估值建模功能
- ✅ 证据溯源功能
- ✅ 审计日志功能

---

**北京市大学生金融人工智能竞赛 - FinAI团队**
