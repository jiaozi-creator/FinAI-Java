# FinAI 使用案例文档

## 案例1：贵州茅台（600519）完整财务分析

### 背景
贵州茅台作为A股白酒行业的龙头企业，其财务表现一直是投资者关注的焦点。本案例将展示如何使用FinAI进行全方位的财务分析。

### 第一步：创建分析任务

```bash
curl -X POST http://localhost:8080/api/tasks \
  -F "companyCode=600519" \
  -F "companyName=贵州茅台" \
  -F "reportPeriod=2023A" \
  -F "analysisType=FULL" \
  -F "file=@maotai_2023_annual_report.pdf"
```

**响应示例：**
```json
{
  "taskId": "TASK_abc123def456",
  "companyCode": "600519",
  "companyName": "贵州茅台",
  "reportPeriod": "2023A",
  "status": "RUNNING",
  "progress": 10,
  "currentStep": "解析PDF文档"
}
```

### 第二步：使用智能对话分析助手

等待任务完成后，可以通过自然语言询问财务问题：

**问题1：公司的盈利能力如何？**
```bash
curl -X POST http://localhost:8080/api/chat/TASK_abc123def456/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "这家公司的盈利能力如何？"}'
```

**AI回答示例：**
```
贵州茅台2023年盈利能力表现优异：

1. 净资产收益率(ROE)：32.5%，显著高于行业平均水平（16.2%）
2. 毛利率：91.3%，保持在极高水平，体现了强大的品牌溢价能力
3. 净利润率：52.8%，每赚1元钱就有0.53元转化为净利润

核心竞争优势：
- 品牌护城河深厚，定价权强
- 产能利用率高，规模效应显著
- 成本控制能力出色

总体评价：盈利能力位居行业第一梯队，投资回报率极高。
```

**问题2：现金流状况如何？**
```bash
curl -X POST http://localhost:8080/api/chat/TASK_abc123def456/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "现金流状况是否健康？"}'
```

**AI回答示例：**
```
贵州茅台的现金流状况非常健康：

1. 经营活动现金流：685亿元，连续多年保持强劲
2. 现金流/净利润比：1.12，经营现金流超过净利润，利润质量高
3. 自由现金流：620亿元，充裕的自由现金流支持分红和再投资

现金流特点：
- 预收款模式带来充沛的现金流入
- 应收账款占比极低，回款能力强
- 资本支出较小，不需要大额投资

结论：现金流状况优秀，为行业标杆。
```

### 第三步：风险预警分析

```bash
curl http://localhost:8080/api/risks/TASK_abc123def456/assessment
```

**风险评估结果：**
```json
{
  "overallRiskScore": 25.5,
  "overallRiskLevel": "LOW",
  "riskScores": {
    "FRAUD": {"score": 15.0, "level": "LOW"},
    "LIQUIDITY": {"score": 18.0, "level": "LOW"},
    "OPERATIONAL": {"score": 35.0, "level": "MEDIUM"},
    "MARKET": {"score": 28.0, "level": "MEDIUM"}
  },
  "alerts": [
    {
      "riskType": "OPERATIONAL",
      "riskLevel": "MEDIUM",
      "title": "行业政策风险",
      "description": "白酒行业受消费政策影响较大",
      "impact": "政策变化可能影响消费需求",
      "recommendation": "关注政策动态，优化产品结构"
    }
  ],
  "summary": "公司整体风险较低，财务状况相对健康，建议持续监控。"
}
```

**解读：**
- 综合风险评分25.5分（满分100），属于低风险水平
- 财务造假风险极低（15分），财务数据真实可信
- 流动性风险低（18分），现金流充裕
- 经营风险和市场风险处于中等水平，主要受行业政策影响

### 第四步：行业对比分析

与其他白酒企业对比：

```bash
curl -X POST http://localhost:8080/api/comparison/TASK_abc123def456/peers \
  -H "Content-Type: application/json" \
  -d '["600036", "000858", "000568"]'
```

**对比结果：**
```json
{
  "targetCompany": {
    "companyCode": "600519",
    "companyName": "贵州茅台"
  },
  "peerCompanies": [
    {"companyCode": "600036", "companyName": "招商银行"},
    {"companyCode": "000858", "companyName": "五粮液"},
    {"companyCode": "000568", "companyName": "泸州老窖"}
  ],
  "metricComparisons": [
    {
      "displayName": "净资产收益率",
      "targetValue": 32.5,
      "peerValues": {
        "000858": 22.3,
        "000568": 18.7
      },
      "industryAverage": 16.2,
      "rank": 1,
      "assessment": "显著优于同行"
    },
    {
      "displayName": "毛利率",
      "targetValue": 91.3,
      "peerValues": {
        "000858": 75.8,
        "000568": 68.5
      },
      "industryAverage": 72.0,
      "rank": 1,
      "assessment": "行业领先"
    }
  ],
  "strengths": ["净资产收益率", "毛利率", "净利润率", "现金流"],
  "weaknesses": [],
  "summary": "公司在净资产收益率、毛利率等方面表现突出，显著优于同行业可比公司。"
}
```

### 第五步：未来预测分析

预测未来3年财务指标：

```bash
curl "http://localhost:8080/api/forecast/TASK_abc123def456?periods=3"
```

**预测结果：**
```json
{
  "metricForecasts": [
    {
      "displayName": "营业收入",
      "unit": "亿元",
      "historicalValues": [
        {"period": "2022A", "value": 1212.0},
        {"period": "2023A", "value": 1357.0}
      ],
      "forecastValues": [
        {"period": "2024E", "value": 1520.0},
        {"period": "2025E", "value": 1703.0},
        {"period": "2026E", "value": 1907.0}
      ],
      "confidenceLevel": 0.95
    },
    {
      "displayName": "净利润",
      "unit": "亿元",
      "historicalValues": [
        {"period": "2022A", "value": 627.0},
        {"period": "2023A", "value": 717.0}
      ],
      "forecastValues": [
        {"period": "2024E", "value": 812.0},
        {"period": "2025E", "value": 921.0},
        {"period": "2026E", "value": 1045.0}
      ],
      "confidenceLevel": 0.95
    }
  ],
  "scenarios": {
    "optimistic": {
      "scenarioName": "乐观情景",
      "description": "高端酒需求强劲，产品提价顺利",
      "probability": 0.25
    },
    "base": {
      "scenarioName": "基准情景",
      "description": "维持当前增长趋势，市场环境稳定",
      "probability": 0.50
    },
    "pessimistic": {
      "scenarioName": "悲观情景",
      "description": "消费疲软，竞争加剧",
      "probability": 0.25
    }
  },
  "keyAssumptions": [
    "高端白酒需求保持稳定",
    "产能逐步释放",
    "品牌溢价能力维持",
    "无重大政策冲击"
  ]
}
```

### 第六步：综合投资建议

基于以上分析，生成综合投资建议：

**财务质量：⭐⭐⭐⭐⭐**
- ROE超过30%，盈利能力极强
- 现金流充裕，利润质量高
- 负债率低，财务结构稳健

**成长性：⭐⭐⭐⭐**
- 收入和利润保持双位数增长
- 品牌势能持续释放
- 预计未来3年年均增长12%

**估值水平：中等偏高**
- PE（TTM）：35倍
- PB：12倍
- 相对行业估值溢价明显

**风险提示：**
1. 白酒行业政策风险
2. 消费升级趋势可能放缓
3. 估值较高，短期波动风险

**投资建议：长期持有**
- 适合风险偏好中等的长期价值投资者
- 建议在合理估值区间分批建仓
- 关注年度业绩和行业政策变化

---

## 案例2：识别财务造假风险

### 案例：某科技公司（虚构案例）

**异常信号1：现金流与利润严重背离**
```json
{
  "riskType": "FRAUD",
  "riskLevel": "HIGH",
  "title": "现金流与利润持续背离",
  "description": "过去3年，经营现金流持续为负，而账面利润持续增长",
  "triggerMetric": "operating_cash_flow_to_net_profit",
  "metricValue": -0.35,
  "threshold": 0.80,
  "impact": "利润可能含有大量应收账款或虚增收入",
  "recommendation": "深入核查应收账款真实性和收入确认政策"
}
```

**异常信号2：应收账款异常增长**
```json
{
  "riskType": "FRAUD",
  "riskLevel": "HIGH",
  "title": "应收账款增速远超营收增速",
  "description": "应收账款增长85%，而营收仅增长28%",
  "triggerMetric": "receivables_to_revenue_growth",
  "metricValue": 3.04,
  "threshold": 1.50,
  "impact": "可能存在虚构销售或客户偿付能力问题",
  "recommendation": "核查前五大客户真实性和信用状况"
}
```

**异常信号3：存货周转率异常下降**
```json
{
  "riskType": "OPERATIONAL",
  "riskLevel": "MEDIUM",
  "title": "存货周转率大幅下降",
  "description": "存货周转率从8.5降至4.2，存货积压严重",
  "triggerMetric": "inventory_turnover",
  "metricValue": 4.2,
  "threshold": 6.0,
  "impact": "产品销售不畅或存货减值风险",
  "recommendation": "关注存货跌价准备计提是否充分"
}
```

**综合判断：高风险**
- 综合风险评分：78分（高风险）
- 建议：谨慎投资，需要第三方尽职调查

---

## 案例3：困境反转分析

### 案例：某新能源汽车公司（比亚迪为原型）

**历史困境期（2018-2019）**
- 补贴退坡导致利润下滑
- 市场份额被特斯拉挤压
- 股价持续低迷

**转折信号识别：**

1. **产品创新突破**
   - 刀片电池技术发布
   - DMi混动系统推出
   - 产品竞争力显著提升

2. **财务指标改善**
```json
{
  "trendAnalysis": {
    "metric": "gross_profit_margin",
    "trendType": "UPWARD",
    "trendStrength": 0.85,
    "turningPoint": {
      "period": "2020Q2",
      "type": "trough",
      "description": "毛利率触底反弹，新产品开始放量"
    }
  }
}
```

3. **市场份额回升**
   - 2020年：市场份额10% → 2023年：市场份额35%
   - 销量增长300%

**预测模型显示：**
```json
{
  "forecast": {
    "revenue_growth_rate": 0.65,
    "profit_margin_improvement": 0.08,
    "market_share_target": 0.40
  },
  "scenario": "optimistic",
  "confidence": 0.75
}
```

**投资建议：**
- 困境反转确认，建议积极配置
- 目标价位：上涨空间50%+
- 风险：产能扩张速度、竞争加剧

---

## 案例4：跨行业对比分析

### 制造业 vs 互联网公司

**财务特征对比：**

| 指标 | 制造业（格力电器） | 互联网（腾讯） |
|-----|---------------|-------------|
| 毛利率 | 28% | 52% |
| ROE | 18% | 25% |
| 资产周转率 | 0.85 | 0.45 |
| 现金流/利润 | 1.15 | 0.92 |
| 资本支出比例 | 高 | 中 |

**分析结论：**
- 制造业：重资产、高周转、稳定现金流
- 互联网：轻资产、高毛利、规模效应强
- 估值方法：制造业适用PB，互联网适用PS/PE

---

## API集成示例

### Python SDK示例

```python
import requests

class FinAIClient:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url
    
    def create_task(self, company_code, company_name, report_period):
        """创建分析任务"""
        url = f"{self.base_url}/api/tasks"
        data = {
            "companyCode": company_code,
            "companyName": company_name,
            "reportPeriod": report_period,
            "analysisType": "FULL"
        }
        response = requests.post(url, json=data)
        return response.json()
    
    def ask_question(self, task_id, question):
        """智能对话提问"""
        url = f"{self.base_url}/api/chat/{task_id}/ask"
        data = {"question": question}
        response = requests.post(url, json=data)
        return response.json()
    
    def get_risk_assessment(self, task_id):
        """获取风险评估"""
        url = f"{self.base_url}/api/risks/{task_id}/assessment"
        response = requests.get(url)
        return response.json()
    
    def compare_with_peers(self, task_id, peer_codes):
        """行业对比"""
        url = f"{self.base_url}/api/comparison/{task_id}/peers"
        response = requests.post(url, json=peer_codes)
        return response.json()
    
    def forecast_metrics(self, task_id, periods=3):
        """预测分析"""
        url = f"{self.base_url}/api/forecast/{task_id}"
        params = {"periods": periods}
        response = requests.get(url, params=params)
        return response.json()

# 使用示例
client = FinAIClient()

# 1. 创建任务
task = client.create_task("600519", "贵州茅台", "2023A")
task_id = task["taskId"]

# 2. 智能对话
answer = client.ask_question(task_id, "公司的盈利能力如何？")
print(answer["answer"])

# 3. 风险评估
risk = client.get_risk_assessment(task_id)
print(f"综合风险评分: {risk['overallRiskScore']}")

# 4. 行业对比
comparison = client.compare_with_peers(task_id, ["000858", "000568"])
print(f"优势领域: {comparison['strengths']}")

# 5. 预测分析
forecast = client.forecast_metrics(task_id, periods=3)
print(f"未来营收预测: {forecast['metricForecasts'][0]['forecastValues']}")
```

---

## 总结

FinAI 提供了一套完整的财务分析工具链：

1. **智能对话** - 用自然语言询问财务问题
2. **风险预警** - 多维度风险检测
3. **行业对比** - 横向对比分析
4. **预测分析** - 基于历史数据预测未来

这些功能可以单独使用，也可以组合使用，为投资决策提供全方位支持。

---

更新时间：2026-09-21
版本：v1.1.0
