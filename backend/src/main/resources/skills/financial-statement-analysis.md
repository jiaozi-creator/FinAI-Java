# 财报分析与估值

适用选题：上市公司财务报告分析、自动化估值建模。

## 工具

1. `pdf.parse`：PDF 只打开一次，按页抽取主表行项目，保留页码、原文、单位、合并/母公司口径。
2. `metric.calculate`：用 BigDecimal 算同比和比率。缺列就是空，不补零。环比在只有一份报告时不计算。
3. `articulation.check`：资产与负债加权益，相对差额 1% 以内算通过。
4. `anomaly.rules`：非经常性损益差额、利润与现金流背离、会计政策变更附注。规则版本 2026.1。
5. `valuation.dcf`：有经营现金流和历史增速才做五年 FCFF。缺资本开支会标明 FCFF 用经营现金流代替。没有可比公司就不做相对估值。

## 调用

运行时会把本文件放进模型的系统提示。模型看不到财报原件。需要数据时只输出一行 JSON：

- `{"tool":"statement.metrics"}`
- `{"tool":"statement.evidence","fieldId":"all"}`
- `{"tool":"articulation.check"}`
- `{"tool":"anomaly.rules"}`
- `{"tool":"valuation.dcf"}`

至少调用一个工具后，再按提示词输出表述 JSON。工具结果里没有的金额、比率、倍数不要写。

## 表述

- 提取值和勾稽结果是事实。
- 规则命中和折现结果是推论。
- 模型建议和投资含义是观点。
- 模型只写推论段落，不能改数字。

