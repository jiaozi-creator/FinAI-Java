# 当前实现

选题只覆盖财务报告分析（选题 2）和简化 DCF（选题 4）。

主链路在 `TaskExecutionWorker`：读文件并记 SHA-256 → 按 `config/field_dict.yaml` 抽主表 → 同比和勾稽 → 按 `config/rules.yaml` 做三条异常规则 → `ValuationEngine` 做五年 FCFF → `SkillNarrator` 在需要表述时调用已算好的工具。数字由 `com.finai.analysis` 计算。模型改不了数，对不上工具结果的段落会退回程序文本。

## 没有在算的东西

- 行业对比和行业排名：接口返回「未计算」，不生成随机或写死的 ROE、毛利率、名次。
- 预测：返回空结果，没有 ARIMA。
- 风险预警四类检测：没有接入指标。空结果不打低风险分，也不补通用建议。报告里的异常规则是另一条链路。
- OCR、图表识别、扫描件：未实现。`ocrTable` 返回「未实现」。
- 相对估值、市盈率：没有市价和可比公司，不计算。
- 环比：单份报告不计算。

## 运行记录

审计日志保存完整 Prompt 和回复，并写明模型、温度、技能和提示词的 SHA-256、文件 SHA-256，以及这次调用有没有离开本机。没配 `ALIYUN_API_KEY` 时用本地 mock，不发请求。

配置以仓库根目录 `config/` 为准。`backend/src/main/resources/config/` 是打包进 jar 的副本，两边要一起改。
