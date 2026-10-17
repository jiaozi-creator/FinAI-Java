# FinAI

北京市大学生金融人工智能竞赛作品。选题是上市公司财务报告分析，以及在此基础上的简化现金流折现。

数字由 Java 程序从财报文本中抽取并计算。大模型只负责最后的表述，而且只能读取已经算好的结果。缺数据就留空，不补数。任务步骤目前是固定顺序，不是模型现场规划。

计划书见 [docs/项目计划书.md](docs/项目计划书.md)。运行细节和第三方清单见 [docs/runbook.md](docs/runbook.md)。

## 现在能做什么

- ✅ **财务报告分析**: 自动提取和分析上市公司财务数据
- ✅ **异常信号检测**: 基于规则引擎检测财务异常
- ✅ **自动化估值**: 基于财报输入和显式假设的 DCF；相对估值在有可靠可比数据后再启用
- ✅ **证据溯源管理**: 完整记录所有数据来源和计算过程
- 🚧 **智能体编排**: 当前为固定 Java 执行链；受控多角色编排列入 P1
- ✅ **审计日志**: 所有操作可追溯、可复现

1. 抽取营业收入、利润、现金流、资产、负债、权益等主表科目，记下页码和原文。
2. 同一科目在摘要、合并表、母公司表、附注里都出现时，保留合并主表那一行。带「归属于」的净利润优先于短行「净利润」。
3. 计算同比和比率：毛利率、净利率、ROE、ROA、资产负债率、流动比率、经营现金流 / 归母净利润。只有一份报告时不算环比。
4. 检查资产与负债加权益是否勾稽。
5. 检查三类信号：扣非差额、利润和经营现金流背离、会计政策变更附注。
6. 只有标准 FCFF、折现参数、预测期、估值范围和桥接政策经确认后才做正式 DCF；简式自由现金流仅作经营现金流代理展示。参数不完整时可展示带证据的净债务诊断，但不给正式每股价值。
7. 把文件哈希、工具输出和模型调用写入审计日志。

- 🚀 **现代化架构**: Spring Boot 3 + React 18
- 🤖 **AI 驱动**: 竞赛默认使用阿里云 Qwen；Claude/OpenAI 仅保留为非竞赛开发适配器
- 📊 **数据处理**: 当前主链路为 PDFBox 文本抽取；扫描件 OCR 和复杂表格仍待评估
- 🔍 **规则引擎**: YAML 配置化的异常检测规则
- 💾 **灵活存储**: 支持 H2、MySQL、PostgreSQL
- 📈 **可视化**: Ant Design + Recharts 图表展示
扫描件、OCR、行业排名、预测、相对估值目前都不出数。对应接口会写明「未计算」。

## 环境

- JDK 17 或以上（当前机器是 21，可以用）
- Maven 3.6+
- Node.js 18+

不需要 Docker。

安装包清单见 [requirements.txt](requirements.txt)，必需工具为 JDK 17+、Maven 3.6+、Node.js 18+（含 npm）。本项目没有 Python 包依赖；安装工具后，在项目根目录执行以下命令安装 Java/npm 依赖并打包：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-dependencies.ps1
```

脚本依据 `backend/pom.xml` 和 `frontend/package-lock.json` 安装实际依赖；仅检查环境可追加 `-CheckOnly`。首次安装需要联网；脚本不修改密钥配置、不启动服务，也不代替测试和财务验收。

## 启动

先起后端，再起前端。H2 使用文件库 `backend/data/finai.mv.db`，同时只能有一个后端进程。第二个 `mvn spring-boot:run` 会报 `Database may be already in use`。

2. **配置环境变量**

```bash
cp .env.example .env
# 编辑 .env 文件，填入 API 密钥
```

至少需要配置以下内容:
```bash
# 竞赛默认使用阿里云 Qwen
LLM_PROVIDER=aliyun
ALIYUN_API_KEY=your_aliyun_api_key_here

# 无密钥离线演示（必须显式开启）
# LLM_PROVIDER=mock
# DEV_MOCK_LLM=true
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
- H2 控制台默认关闭；仅在本地显式设置 `H2_CONSOLE_ENABLED=true` 时开放

### 手动启动

如果一键脚本无法运行，可以手动启动:

**后端:**
```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

- 页面：http://localhost:5173
- 接口：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html

可选环境变量：

```bash
LLM_PROVIDER=aliyun
LLM_MODEL=qwen-plus
LLM_TEMPERATURE=0
ALIYUN_API_KEY=
```

不填密钥时仅在显式开启 `LLM_PROVIDER=mock`、`DEV_MOCK_LLM=true` 后使用本地 mock；否则模型调用失败会记录并按确定性降级返回。财报指标仍可计算，正式估值仍受确认门禁约束。

代码更新后要重启后端。库里已经跑完的任务不会自动重算，需要重新上传并新建任务。

## 用一次

1. 打开「新建任务」。
2. 填写公司代码、名称、报告期，分析类型选「完整分析(含估值)」。
3. 上传文本可选中的 PDF。不上传会失败，因为流水线要求读到文件。
4. 在任务详情看指标、异常、估值假设，点来源看页码。
5. 报告页是结构化结果。对话页只能追问已有快照里的数字。

样例：

| 文件 | 用途 |
| --- | --- |
| `backend/src/main/resources/samples/demo-statement.txt` | 人造短表。公司代码 `DEMO01` 时，报告会标明这不是真实年报 |
| `test-data/maotai_2023_annual_report.txt` | 摘录，勾稽不成立，不能当标准答案 |
| `test-data/filings/603313_2025_annual.pdf` | 梦百合 2025 年报文本层。`FilingProbeTest` 用它核对合并口径 |

资产负债表年末余额的同比、简式自由现金流和 EPS 会在同口径上期可用时展示，并同时保留披露值、复算值及来源。ROE 同时区分年报披露加权 ROE 与平均归母权益 ROE；页面会标明口径。WACC、永续增长率、预测期和 FCFF 均须用户确认，示例值不自动进入正式估值。

## 代码怎么读

建议按一次任务的执行顺序读，不要从 Controller 列表开始。

```
新建任务 AnalysisTaskController
  -> AnalysisServiceImpl 保存上传文件
  -> TaskExecutionWorker.runPipeline
       1. StatementExtractor     读 PDF/txt，按表头和 config/field_dict.yaml 匹配行
       2. EvidenceStore          保存页码和原文
       3. MetricCalculator       同比、比率、勾稽
       4. AnomalyRuleEngine      读 config/rules.yaml 的阈值
       5. ValuationEngine        简化 DCF
       6. ReportComposer         组装报告
            SkillNarrator        加载技能和提示词，只调用已算好的工具
```

这些类都在 `backend/src/main/java/com/finai/analysis`，除了编排在 `service/TaskExecutionWorker.java`，证据在 `service/EvidenceStore.java`。

页面路由在 `frontend/src/App.jsx`：

| 路径 | 作用 |
| --- | --- |
| `/dashboard` | 工作台 |
| `/tasks` | 任务列表 |
| `/tasks/create` | 新建任务 |
| `/tasks/:taskId` | 指标、异常、估值、证据 |
| `/tasks/:taskId/report` | 结构化报告 |
| `/tasks/:taskId/chat` | 基于快照追问 |
| `/tasks/:taskId/risks` | 风险接口。检测未接入时显示未评估，不给分数 |

配置：

- `config/field_dict.yaml`：抽哪些科目、行首别名是什么。口径优先级不在这个文件里，在 `StatementExtractor`。
- `config/rules.yaml`：三条规则的阈值。
- `config/formulas.yaml`：公式说明，和 `MetricCalculator` 一致。改表达式不会自动改变计算，测试会核对关键公式。
- `backend/src/main/resources/skills/financial-statement-analysis.md`：模型看到的技能。
- `backend/src/main/resources/prompts/report-narrative.md`：表述约束。

从 `backend` 目录启动时，程序优先读上一级的 `config/`。`backend/src/main/resources/config/` 是打进 jar 的副本，两边要一起改。

审计在表 `audit_log`。关注四种操作名：`file.access`、工具名（`pdf.parse`、`metric.calculate`、`articulation.check`、`anomaly.rules`、`valuation.dcf`）、`agent.*`、`LLM_CALL`。`/api/mcp/tools` 只读已经算完的指标、证据和估值。

## 测试

```bash
cd backend
mvn test
```

| 测试 | 检查什么 |
| --- | --- |
| `StatementPipelineTest` | 人造报表的抽取、同比、勾稽、三条异常、DCF，以及合并行压过母公司行和附注 |
| `GoldSetTest` | `eval/gold/demo-statement.yaml`。这是计分自检，不是上市公司年报 |
| `AnalysisConfigTest` | YAML 阈值和 ROE 公式被程序读到 |
| `FilingProbeTest` | 梦百合 2025 年报 PDF 上的合并科目 |

## 口径提醒

- ROE 区分年报披露加权 ROE 和平均归母权益 ROE；杜邦分解使用平均归母权益口径，不将两者混用。
- 简式自由现金流是经营现金流减资本开支的代理指标，不替代标准 FCFF。
- WACC、永续增长率、预测期、估值范围和桥接政策是待确认参数，不是财报事实；未确认时正式 DCF 保持阻断。
- 现金流补充资料必须按本期列勾稽至披露 CFO；缺项、错列或重复加总会阻断财报自动检查。
- 没有可比公司和市价，所以没有相对估值。
- 扣非可能来自「主要会计数据」，口径字段是 `summary`。
- 模型段落需要人工复核。报告结论会把事实、推论、观点分开写。
## 当前验收边界（2026-10-16）

冻结 PDF 的调整列、现金流补充资料折行、状态元数据、REVIEW 数据质量门禁、逐年 FCFF 模型和问答数字校验已有后端回归；前端构建已通过。第八版新任务、浏览器/手机布局、API/详情/报告/HTML 一致性和财务人工复核仍待用户重跑确认。净债务诊断可以独立于正式 DCF 展示，但正式 EV、股权价值和每股价值仍要求完整确认；问答历史目前仅存内存，重启恢复未实现。

手动重跑：启动后端与前端，上传两份冻结 PDF，分别创建全新 FULL 任务；保存任务 ID、运行 ID、PDF 哈希和 HTML，待财务复核后再确认估值参数。本轮不提交 commit。
