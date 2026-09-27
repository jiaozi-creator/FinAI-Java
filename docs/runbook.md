# 运行说明

选题 2 和选题 4 的主链路在后端任务里，不在对话记录里。

## 启动

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。接口文档 `http://localhost:8080/swagger-ui.html`。

## 跑一次分析

1. 创建任务，分析类型选「完整分析」。
2. 上传文本层可复制的财报 PDF。扫描件目前抽不到数字，对应字段会显示「未提取」。
3. 任务详情看三块：财务指标和同比、三条异常规则、DCF 区间和假设表。
4. 点「查看来源」看页码和原文。审计日志在 `audit_log` 表，操作名是 `pdf.parse`、`metric.calculate`、`articulation.check`、`anomaly.rules`、`valuation.dcf`。

环比需要多期序列，单份报告不计算。相对估值没有可比公司和市价，不计算。WACC 9% 和永续增长率 2% 是公式假设，不是财报事实。行业对比、预测、OCR 都不出数。风险预警接口在检测为空时不打分、不给套话。

字段和规则读 `config/field_dict.yaml`、`config/rules.yaml`。从 `backend` 目录启动时会读到上一级的 `config/`。打包进 jar 的副本在 `backend/src/main/resources/config/`，两边内容要一致。

表述由 `SkillNarrator` 完成。它把 `skills/financial-statement-analysis.md` 和 `prompts/report-narrative.md` 放进系统提示，然后只允许模型调用已经算好的 `statement.metrics`、`statement.evidence`、`articulation.check`、`anomaly.rules`、`valuation.dcf`。审计日志里的 `LLM_CALL` 保存完整提示和回复，details 里有模型、温度、两份文本的 SHA-256，以及 `leavesMachine`。`file.access` 记录路径、字节数、页数和文件 SHA-256。

没配 `ALIYUN_API_KEY` 时使用本地 mock，温度和模型名以 `application.yml` 的 `finai.llm.temperature`、`finai.llm.model` 为准，默认 `0.0` 和 `qwen-plus`。

## 第三方

| 名称 | 版本 | 许可证 | 使用范围 |
| --- | --- | --- | --- |
| Spring Boot | 3.2.0 | Apache-2.0 | Web、JPA、校验 |
| LangChain4j | 0.35.0 | Apache-2.0 | 调用聊天模型。模型权重不在仓库里 |
| 通义千问 qwen-plus | 以 DashScope 控制台为准 | 阿里云服务条款 | 只生成表述。不配密钥则不调用 |
| Apache PDFBox | 3.0.1 | Apache-2.0 | 抽取 PDF 文本层 |
| Apache POI | 5.2.5 | Apache-2.0 | 依赖已引入，主链路不用它解析财报 |
| H2 | Spring Boot 管理的版本 | MPL-2.0 / EPL-1.0 | 本地任务和审计库 |
| React | 18.2 | MIT | 页面 |
| Ant Design | 5.12 | MIT | 主界面组件 |

Claude、GPT 的依赖还在 `pom.xml` 里，默认不启用。OCR 没有第三方引擎。

