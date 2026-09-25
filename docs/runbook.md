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

环比需要多期序列，单份报告不计算。相对估值没有可比公司和市价，不计算。WACC 9% 和永续增长率 2% 是公式假设，不是财报事实。

## 依赖

- PDFBox 3.0.1，Apache License 2.0，只做文本抽取。
- LangChain4j 0.35.0，Apache License 2.0，只写报告表述。模型名和密钥用环境变量 `ALIYUN_API_KEY`，不要把密钥写进仓库。
- 数字计算在 `com.finai.analysis`，不依赖模型。

提示词：`backend/src/main/resources/prompts/report-narrative.md`。
技能说明：`backend/src/main/resources/skills/financial-statement-analysis.md`。
