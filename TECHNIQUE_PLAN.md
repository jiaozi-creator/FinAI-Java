# FinAI 金融投研智能体技术蓝图

> 本文以现有 Java 仓库为实施基线，不沿用旧版 Python/LangGraph/FastAPI/SQLite 重建方案。业务目标见 [PROJECT_PLAN.md](PROJECT_PLAN.md)，运行现状见 [docs/runbook.md](docs/runbook.md)。状态记录为 2026-09-29 的代码检查，非运行验收结论。

## 1. 已有系统和差距

```text
React/Vite -> Spring Boot REST -> TaskExecutionWorker
                           -> StatementExtractor(PDFBox) -> EvidenceStore
                           -> MetricCalculator/ArticulationCheck -> AnomalyRuleEngine
                           -> ValuationEngine -> ReportComposer(LangChain4j 可选)
                           -> AnalysisSnapshot + AuditLog -> 任务详情/报告
```

后端为 Java 17、Spring Boot 3.2、LangChain4j 0.35、H2/JPA、PDFBox 3.0.1；前端实际为 React 18/Vite/Ant Design，不是 Java。`backend/src/main/resources/prompts/` 和 `skills/` 已有文件，`McpToolController` 提供只读 HTTP 工具入口，但不是标准 MCP Server。测试目前以 `StatementPipelineTest` 为主，新增服务缺乏相应已存在的测试文件；不能把文档列出的测试命令等同于测试通过。

`IndustryComparisonServiceImpl` 使用随机/固定示例数据；`ForecastServiceImpl` 尚无可核验时间序列预测；`RiskWarningServiceImpl` 有空类别；`SmartPDFParserServiceImpl` 的 OCR/图表处理有 TODO。扩展 API 不得把这些结果包装为真实金融研究结论。单份文本 PDF 的已实现主链路仍优先于这些附加服务。

## 2. 技术决策

| 领域 | 当前选型和近期做法 | 延后条件 |
|---|---|---|
| Agent 编排 | 在 `TaskExecutionWorker` 上抽取 Java 阶段接口与显式状态机；LangChain4j 仅作模型调用和有边界的工具选择 | 不引入 LangGraph 或 Python 后端以替换现有链路 |
| 金融计算 | 保留 `com.finai.analysis`，输入已归一的字段，金额/比率计算逐步规范为 `BigDecimal`、公式与规则版本化 | 不让 LLM 生成最终数值或修改原始证据 |
| 持久化 | 沿用 H2/JPA 的 task/evidence/snapshot/audit；正式运行再评估 PostgreSQL 和迁移脚本 | 不把 Redis/向量库列为必需依赖 |
| PDF | PDFBox 文本型 PDF 为主；抽取失败返回缺字段；先建人工标注基线再选择 OCR/表格库 | 扫描件和图表不宣称已支持 |
| 对外协议 | 现有 REST 路由及 JSON DTO 保持兼容，工具通过内部 Java 服务供编排复用 | 赛前增加标准 MCP 适配层并用真实客户端验证工具发现、schema 与调用 |
| 模型提供方 | 单一 `ModelGateway` 根据配置选择云 API 或显式 mock，固定超时/重试/令牌预算并记录模型版本 | Ollama 作为同一适配接口的后续可选项；评估质量和硬件后启用 |
| Python | 首轮不引入；确需 OCR 或统计建模时以版本化 HTTP/JSON 独立算法服务接入 | 必须有性能/效果证据、契约测试和离线降级路径 |

### 2.1 项目代码架构

以下只列关键模块；`已有`表示文件或目录存在，不表示通过业务验收；`已有待完善`表示现存代码需要该阶段改造；`计划新增`为目标路径，尚未创建。现有控制器、DTO 和服务的完整清单以仓库为准。保留 Java/Spring Boot、React/Vite 和 H2/JPA 分层，不另建 Python 后端。

```text
backend/src/main/java/com/finai/
├── analysis/                              已有：提取、指标、勾稽、异常、DCF、报告
├── agent/                                 计划新增（P1）
│   ├── TaskOrchestrator.java              固定顺序编排及失败/取消控制
│   ├── AgentContext.java                  版本化输入、运行标识和证据引用
│   ├── EvidenceAgent.java                 复用提取与 EvidenceStore
│   ├── FinancialAnalysisAgent.java        复用确定性指标与勾稽计算
│   ├── RiskAgent.java                     仅解释已触发且可核验的规则
│   ├── ValuationAgent.java                管理假设来源，复用 Java DCF
│   ├── ReportAgent.java                   生成事实、推论分层的报告
│   └── ReviewAgent.java                   核对数值、单位、报告期和引用
├── tool/                                  计划新增（P1）：内部只读工具契约与白名单
├── config/
│   ├── LangChain4jConfig.java             已有待完善（P0）：统一阿里云配置
│   └── ModelGateway.java                  计划新增（P1）：受控模型调用/显式 mock
├── controller/                            已有 REST；McpToolController 仅为 HTTP（P3 适配 MCP）
├── model/                                 已有 task/evidence/snapshot/audit、DTO
│   └── execution/                         计划新增（P1）：独立阶段、运行和复核记录
├── repository/                            已有；P1 补充运行记录持久化接口
├── service/                               已有 TaskExecutionWorker 等；P0/P1 增量接入
└── exception/                             已有；P0/P1 补齐配置、权限和降级错误
backend/src/main/resources/
├── application.yml                        已有待完善（P0）：阿里云默认值与运行模式
├── prompts/、skills/                      已有；P1 记录调用版本
└── samples/demo-statement.txt             已有合成样例；P0 与真实样本隔离
backend/src/test/java/com/finai/
├── analysis/StatementPipelineTest.java    已有
└── agent/、service/、controller/           计划新增（P0/P1 对应测试）
frontend/src/
├── pages/
│   ├── Dashboard.jsx、TaskList.jsx、TaskDetail.jsx、CreateTask.jsx、ReportView.jsx
│   │                                       已有；P0/P1 补充状态与证据展示
│   ├── ChatAnalysis.jsx、RiskAssessment.jsx
│   │                                       已有且已配置路由；P0 校正结果标识
│   └── IndustryComparison.jsx、ForecastAnalysis.jsx
│                                           已有但未配置路由；P0 隔离占位结果，P2 决定启用
├── services/api.js                        已有；P0/P1 扩展兼容接口
└── App.jsx                                已有；对比、预测路由尚未接入
config/                                    已有公司、字段、公式及规则配置
.env.example、run_all.bat、run_all.sh       已有待完善（P0）
data/manifests/、测试样本答案                计划新增（P0）；原始财报按授权存放，不提交密钥
```

## 3. Agent、Tool 与数据契约

角色是服务职责，不需要为每个角色部署一个模型。建议建立 `com.finai.agent`，以 `TaskOrchestrator` 对接现有任务服务：`EvidenceAgent` -> `FinancialAnalysisAgent`/`RiskAgent` -> `ValuationAgent` -> `ReportAgent` -> `ReviewAgent`。只在已有分析链路稳定后按阶段引入。编排顺序固定，模型可在白名单内选择只读工具，但不得绕过校验、写库、执行系统命令或发起任意网络请求。

建议的版本化 JSON 契约（字段为目标设计，非现有 DTO）：

```json
{
  "request": {
    "taskId": "TASK_123", "traceId": "run-id", "action": "analyze",
    "inputSnapshotVersion": "sha256:...", "allowedTools": ["statement.metrics"]
  },
  "result": {
    "status": "ok|degraded|failed", "facts": [], "inferences": [],
    "assumptions": [], "evidenceRefs": [], "warnings": [],
    "nextAction": "review", "auditEvents": []
  }
}
```

每个 Tool 应声明名称、版本、入参/返回 schema、是否只读、超时、错误码和可调用任务状态。第一批复用已有 `statement.metrics`、`statement.evidence`、`valuation.dcf`，再增加内部 `anomaly.evaluate`、`valuation.sensitivity` 和 `report.validate`；不能仅靠 `/api/mcp/tools` 的路径名称宣称 MCP 协议合规。Tool 返回空字段时显式说明 `missing_reason`，不允许以 0 代替缺失。证据引用至少含 `fieldId`、源文件哈希、报告期、单位、页码、原文片段与提取版本；已存字段缺元数据时先标缺失，不伪造页码或表格定位。

估值假设按 `extracted|formula|user_confirmed|llm_suggested` 分类，记录数值、单位、来源、依据与版本；`llm_suggested` 必须经人工确认才可参与正式估值。报告字段标记 `fact|calculation|inference|opinion|assumption`。ReviewAgent 至少校验数字/单位/报告期是否与快照一致、引用是否存在、假设有无冒充事实；任一关键项失败时输出“待复核”或降级报告，而不是 `COMPLETED` 的无保留结论。

## 4. 状态、降级和安全

现有任务生命周期 `CREATED -> QUEUED -> RUNNING -> COMPLETED/FAILED/CANCELLED` 及 REST 状态字段保持兼容；解析、提取、校验、分析、报告是 `RUNNING` 期间的独立执行阶段，不加入 `TaskStatus` 枚举。现有 `currentStep` 和文字进度可继续服务旧客户端；P1 以附加记录表示阶段、运行标识及复核/降级状态。每个阶段保存输入快照哈希、工具/Prompt/规则版本、开始与结束时间及错误；沿用现有 `/api/tasks/{taskId}/rerun`，重跑需检查快照版本并保证写入幂等，失败后不复用不完整报告。异步执行、重启恢复和取消流程沿用已有服务，先测试再调整。

降级规则：PDF 无文本 -> 缺失并中止依赖该字段的计算；数值勾稽失败 -> 不写确定性结论；模型超时/失败 -> 纯程序报告并标降级；无云 API 密钥 -> 仅在显式 mock/演示模式允许 Mock，正式模式启动/请求失败应给明确错误；无行情或可比样本 -> 不输出 PE/PB 和行业排名。禁止随机示例数值进入正式报告，开发演示须有显著标记。

开发默认值 `SERVER_HOST=0.0.0.0`、`DEV_SKIP_AUTH=true`、H2 Console 开启、宽松 CORS 与未限制的任务访问均需在可对外访问前收紧：演示环境优先绑定 localhost，限制来源/上传类型和大小、任务及文件访问；审计记录操作类型/耗时/失败码但不记录密钥、完整问题或整段敏感财报。密钥只从进程环境或安全密钥管理加载，`.env` 仅供本地且不得纳入提交；检查日志、样例数据和运行包。开发 H2 可保留，部署时禁用 H2 Console、关闭 `ddl-auto:update` 并选择可审计迁移方式。

## 5. 模型配置、Python 和 Ollama

代码目前 `application.yml` 默认 `LLM_PROVIDER=aliyun` 却将默认模型写成 Claude，`LangChain4jConfig` 将 Aliyun 模型固定为 `qwen-plus`，而 `.env.example` 与 Windows 快速指南默认 Claude；`LLM_MODEL`、温度、最大 token 等配置未在该 Bean 中统一生效，且启动脚本尚未把 `.env` 加载为进程环境变量。P0 将阿里云国产模型作为竞赛默认值，旧 Claude/OpenAI 密钥示例保留但注释，现有提供方适配器保留供非竞赛开发；竞赛模式仅允许国产模型。P0 再统一这些来源、文档与启动脚本，配置错误不得静默降级成真实 AI 成功。用固定财报样本分别验证云 API 和显式 mock 模式；真实密钥不得放入代码、计划书或交付包。上述均为待实施事项，P0.0 不修改配置。

Python 仅作为算法服务候选：由 Java 负责权限、任务、审计与公开 REST，Python 服务提供如 `/v1/ocr` 或 `/v1/forecast` 的版本化 JSON 契约，定义输入哈希、报告期、模型版本、随机种子、输出置信区间、超时和错误码；经契约测试后才能接入。首轮不用 Python 改写前后端，也不以未经验证的 ARIMA 或“AI 增强预测”作为功能卖点。

Ollama 后置评估：在 API Agent 闭环通过后，记录可运行的本地模型名称/版本、所需内存/硬件、中文财报准确性、时延和许可证；在同一 `ModelGateway` 下增加 `ollama` 模式，比较云 API 与本地对固定样本的证据一致性。不可假定评委机器可下载模型或提供 API 密钥；最终应有无需密钥的确定性演示路径。赛前需在现有只读 Tool 服务上增加标准 MCP 协议适配并用真实客户端完成发现与调用测试；这不阻塞 P0/P1 的主链路验证。

## 6. 工作包与验收

按表中顺序实施：P0.0 仅修订本技术计划，由用户审阅并自行提交“项目中期规划”；提交后再开始 P0/P1。阶段中的文件与功能是待实施交付，不代表已经存在或验收通过。A/B/C/D/E 的职责沿用项目计划；每个工作包结束时补记实际测试命令、结果、样本版本、负责人/复核人、遗留风险和提交检查点，不预填提交哈希。工程闭环通过不等于财务准确率通过。

| 阶段 | 依赖、负责人及交付 | 测试和退出标准 | 状态/提交检查点 |
|---|---|---|---|
| P0.0 中期规划 | 无实施依赖；A 整理技术计划：架构树、分阶段工作包、状态及错误总结模板；用户审阅并自行提交。 | 与代码现状及项目计划一致；未运行的测试、未复核的样本均不标通过。 | 文档已修订，待用户审阅提交；检查点：用户提交 P0.0。 |
| P0.1 配置与启动 | 依赖 P0.0 提交；A 统一 `application.yml`、`.env.example`、`LangChain4jConfig`、Windows/Linux 启动脚本与启动文档，B 验证前端联通。默认阿里云 `qwen-plus`，旧 Claude/OpenAI 密钥示例保留但注释，运行适配器保留；竞赛模式限制为国产模型。 | 配置确实进入进程并作用于模型；有效/缺失密钥、显式 mock、未知提供方和竞赛模式选择均有确定结果；无静默 mock、无日志密钥。 | 待执行；检查点：配置与启动可复现。 |
| P0.2 真实样本基线 | 依赖 P0.1；A 核验并获取贵州茅台（600519）、五粮液（000858）的 2023 年官方年报，登记来源、报告期、哈希与使用条件；先制作字段/单位/页码/缺失项的暂定标注，C/E 后续异步复核。合成 demo 单独隔离。 | 两份样本来源可追溯，同输入确定性结果可复现；原始 PDF 按授权使用，不默认提交；标注写明“待 C/E 复核”，旧版 98% 仅为目标。 | 待执行；检查点：可追溯样本清单与暂定答案。 |
| P0.3 结果可信度与安全 | 依赖 P0.1、P0.2；A 隔离随机同业数值、空预测及未实现风险类别的真实结论路径，校验上传与本地演示访问边界；B 校正页面/API 可用性标记及实际路由，D 检查风险措辞。 | 不把占位值当真实研究结论；对比/预测页未接入路由时不宣称已上线；完成后端测试、前端构建、Windows 文档启动和任务/API 冒烟并留存结果。 | 待执行；检查点：P0 基线与安全验收。 |
| P1.1 执行契约 | 依赖 P0 验收；A 定义版本化 Agent 输入/输出、只读 Tool schema/白名单、运行与阶段持久化记录；B 对齐兼容 DTO。 | 保留 `CREATED/QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED` 和现有 REST 返回，附加字段不破坏旧客户端轮询；可追溯运行标识、阶段、输入哈希与版本。 | 待执行；检查点：契约与迁移验证。 |
| P1.2 固定编排 | 依赖 P1.1；A 将现有 `TaskExecutionWorker` 增量接入 TaskOrchestrator、Evidence/Financial/Risk/Valuation/Report/Review 职责，复用 Java 确定性工具和现有 `/rerun`。不引入自由规划循环或独立模型实例。 | 固定样本上传到报告端到端可运行；覆盖失败、取消、重跑、重启后运行记录处理及重复写入；不得复用不完整快照。 | 待执行；检查点：固定编排可复现。 |
| P1.3 证据、模型与复核 | 依赖 P1.2；A 加入证据/假设门禁、阿里云 ModelGateway、结构化报告复核与模型故障降级，记录模型、Prompt、规则版本和不含敏感原文的审计；C/E 复核金融口径。 | JUnit/集成测试覆盖错误引用、虚构数字、单位和期间冲突、无证据结论；超时生成显式降级的程序报告，非演示模式缺密钥明确失败；无未复核假设冒充事实。 | 待执行；检查点：证据和故障验收。 |
| P1.4 前端闭环 | 依赖 P1.3；B 在现有任务详情、报告及 API 客户端显示运行阶段、证据、待复核和降级状态，A 配合联调。 | 人工能定位每项关键数值与失败原因；端到端、无密钥、超时、越权访问、重跑/取消场景留存实际验收记录。 | 待执行；检查点：P1 Agent 闭环验收。 |
| P2.1 PDF 与口径 | 依赖 P1 验收及 C 的标注复核；A/C 处理表格、多期口径、单位冲突与抽取缺失；OCR 仅在评估必要性、效果和成本后单独实施。 | 对人工答案测字段定位、数值/单位/口径正确率及缺失率；缺失或冲突不能用 0 填充；C 复核结果。 | 待执行；检查点：提取评估与口径确认。 |
| P2.2 风险与估值 | 依赖 P2.1；A/D 建立异常规则的正常、边界、缺失、冲突样例；A/E 实施 DCF 假设来源确认和敏感性，Java 保持确定性计算。 | 规则和 DCF 与人工答案一致；假设未确认或输入不足时拒绝正式估值或显式降级；D/E 审核解释与假设。 | 待执行；检查点：规则及估值审校。 |
| P2.3 扩展能力评估 | 依赖 P2.2 和可信数据；A/B 评估同业、预测页面启用条件；Ollama/Python 算法服务仅在效果、硬件、授权和契约测试满足要求时立项。 | 没有可靠同业/时间序列数据则保持不可用；不以随机排名、空预测或未实测本地模型宣称能力。 | 待执行；检查点：启用或暂缓的书面结论。 |
| P3.1 MCP 与演示 | 依赖 P1 闭环；A 为只读工具实现标准 MCP 协议适配并记录真实客户端发现/schema/调用验证，A/B 完成无密钥确定性演示、报告导出及样本哈希。 | 实际 MCP 客户端通过协议测试；干净 Windows 环境按文档启动并复现，不要求评委自备 API 密钥。 | 待执行；检查点：协议及演示验收。 |
| P3.2 赛事交付 | 依赖 P3.1；D 统筹材料，A/B 整理源码、运行说明、模型/依赖/数据许可证清单与演示录像，C/E 审核金融结论。 | 源码、材料、日志脱敏与演示一致；赛事截止日期及交付规格以官方最新公告复核。 | 待执行；检查点：赛前交付验收。 |

建议验证命令（仅为计划，执行结果另记）：`cd backend; mvn test`；`cd frontend; npm run build`。人工流程：上传文本 PDF -> 等任务完成 -> 对比暂定答案并记录 C/E 复核状态 -> 打开证据页 -> 按已有重跑入口验证输入与快照 -> 切断模型调用验证降级 -> 重启后核对运行记录、快照与审计。对新增 Agent/Tool 使用 JUnit 和 Spring 集成测试；重点覆盖单位变换、跨期口径、错误引用、伪造数字、无证据结论、幂等重跑与未授权访问。

## 7. 来源、职责和维护

所有实际使用的第三方依赖、模型、数据和改编代码应登记名称、锁定版本、来源、许可证/使用条款、使用范围及验证日期；已有依赖从 `backend/pom.xml` 和 `frontend/package-lock.json` 核对，不能仅抄旧计划的 Python 清单。AQuant、CSFS、KnowFlow 仅是旧计划候选借鉴来源；如要复制代码，先核验具体 commit、授权、差异与测试。业务职责与成员分工见项目计划；技术上 A 负责 Java 编排/分析/审计，B 负责 React/接口/演示，C/D/E 分别复核财务口径、风险案例和估值假设。

每完成一个阶段，记录基线命令及结果、错误现象/根因/修复、影响文件、样本数据版本、验收人和遗留风险；未实测的指标保持“待验证”，不将功能介绍文档中的完成标记直接挪为验收结论。

## 8. 错误总结

每完成一个工作包，在此追加一条记录；没有发现错误也记录检查范围和“未发现”，不编造修复或验收结果。P0.0 仅涉及文档修订，代码执行和财务复核尚未开始。

| 阶段/日期 | 错误现象与根因 | 修复与影响范围 | 回归命令及实际结果 | 未解决事项/复核人 |
|---|---|---|---|---|
| P0.0 / 待用户提交 | 文档原仅列 P0–P3 总表，阶段描述易与任务状态混淆；配置默认值与旧密钥示例不一致为待修复的现存风险。 | 修订技术计划，区分已有代码、计划模块和实施阶段；未更改代码或配置。 | 文档与仓库现状核对；后端测试、前端构建及 Windows 启动均未在本工作包执行。 | 配置及运行验证留待 P0.1；财务样本标注与 C/E 审阅留待 P0.2 以后；用户审阅提交。 |
