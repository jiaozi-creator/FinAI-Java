# FinAI

北京市大学生金融人工智能竞赛作品。选题是上市公司财务报告分析，以及在此基础上的简化现金流折现。

数字由 Java 程序从财报文本中抽取并计算。大模型只负责最后的表述，而且只能读取已经算好的结果。缺数据就留空，不补数。任务步骤目前是固定顺序，不是模型现场规划。

计划书见 [docs/项目计划书.md](docs/项目计划书.md)。运行细节和第三方清单见 [docs/runbook.md](docs/runbook.md)。

## 现在能做什么

上传一份文本层 PDF 或 txt 之后，系统会：

1. 抽取营业收入、利润、现金流、资产、负债、权益等主表科目，记下页码和原文。
2. 同一科目在摘要、合并表、母公司表、附注里都出现时，保留合并主表那一行。带「归属于」的净利润优先于短行「净利润」。
3. 计算同比和比率：毛利率、净利率、ROE、ROA、资产负债率、流动比率、经营现金流 / 归母净利润。只有一份报告时不算环比。
4. 检查资产与负债加权益是否勾稽。
5. 检查三类信号：扣非差额、利润和经营现金流背离、会计政策变更附注。
6. 条件够时做五年 FCFF，给出区间、假设和敏感性。条件不够就不给估值数字。
7. 把文件哈希、工具输出和模型调用写入审计日志。

扫描件、OCR、行业排名、预测、相对估值目前都不出数。对应接口会写明「未计算」。

## 环境

- JDK 17 或以上（当前机器是 21，可以用）
- Maven 3.6+
- Node.js 18+

不需要 Docker。

## 启动

先起后端，再起前端。H2 使用文件库 `backend/data/finai.mv.db`，同时只能有一个后端进程。第二个 `mvn spring-boot:run` 会报 `Database may be already in use`。

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

不填密钥时，表述使用本地 mock，不访问阿里云。指标和估值照常计算。

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

资产负债表科目、自由现金流、每股收益在页面上的同比列固定为空，不代表上期没抽到。ROE 用的是期末权益。WACC 9% 和永续增长 2% 是假设。

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

- ROE 分母是期末归母权益，不是平均权益。
- WACC 9% 和永续增长 2% 是假设，不是财报事实。
- 没有可比公司和市价，所以没有相对估值。
- 扣非可能来自「主要会计数据」，口径字段是 `summary`。
- 模型段落需要人工复核。报告结论会把事实、推论、观点分开写。
