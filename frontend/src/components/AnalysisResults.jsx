import { Alert, Descriptions, Table } from 'antd'
import { formatMetric, MetricEvidence } from './FinancialMetricsTable'

const amount = value => formatMetric(value, { displayScale: 2 })
const percent = value => formatMetric(value, { unit: '%', displayScale: 2 })

export function AcceptanceStatus({ report }) {
  const historical = '历史结果未执行本项检查'
  return <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
    <Descriptions.Item label="财报自动检查">{report?.financialAutoCheckStatus || historical}</Descriptions.Item>
    <Descriptions.Item label="估值输入完整性">{report?.valuationInputStatus || historical}</Descriptions.Item>
    <Descriptions.Item label="估值计算检查">{report?.valuationCalculationStatus || historical}</Descriptions.Item>
    <Descriptions.Item label="人工财务复核">{report?.manualFinancialReviewStatus === 'PENDING' ? '待复核' : report?.manualFinancialReviewStatus || '待复核'}</Descriptions.Item>
    <Descriptions.Item label="运行标识">{report?.executionSummary?.runId || historical}</Descriptions.Item>
    <Descriptions.Item label="PDF 哈希">{report?.executionSummary?.fileSha256 || '未保存'}</Descriptions.Item>
    <Descriptions.Item label="代码版本">{report?.executionSummary?.codeVersion || '历史版本未登记'}</Descriptions.Item>
    <Descriptions.Item label="参数版本">{report?.executionSummary?.parameterVersion || '未经参数确认'}</Descriptions.Item>
  </Descriptions>
}

export function FinancialAnalysis({ metrics }) {
  const dupont = metrics?.duPontAnalysis, reconciliation = metrics?.cashFlowReconciliation
  const template = [['nri_disposal', '资产处置损益'], ['nri_subsidy', '政府补助'], ['nri_fair_value', '公允价值变动损益'], ['nri_fund_occupation', '资金占用费'], ['nri_impairment_reversal', '减值转回'], ['nri_other', '其他营业外收支'], ['nri_other_defined', '其他非经常性项目'], ['nri_tax', '所得税影响额'], ['nri_minority_impact', '少数股东影响额'], ['nri_total', '合计']]
  const nonRecurring = template.map(([fieldId, name]) => metrics?.nonRecurringItems?.find(i => i.fieldId === fieldId) || { fieldId, name, amount: null, dataQualityStatus: 'SOURCE_NOT_FOUND' })
  return <div style={{ marginTop: 16 }}>
    <h3>基础杜邦分解</h3>
    {dupont ? <><p>{dupont.explanation}</p><Descriptions bordered size="small">
      <Descriptions.Item label="归母净利率">{percent(dupont.netMargin)}</Descriptions.Item>
      <Descriptions.Item label="平均资产周转率">{amount(dupont.averageAssetTurnover)} 倍</Descriptions.Item>
      <Descriptions.Item label="平均权益乘数">{amount(dupont.averageEquityMultiplier)} 倍</Descriptions.Item>
      <Descriptions.Item label="分解乘积">{percent(dupont.decomposedRoe)}</Descriptions.Item>
      <Descriptions.Item label="平均归母权益 ROE">{percent(dupont.averageEquityRoe)}</Descriptions.Item>
      <Descriptions.Item label="状态">{dupont.status}</Descriptions.Item>
    </Descriptions></> : <p>历史结果未执行本项检查</p>}
    <h3>现金流补充资料调节（合并口径）</h3>
    {reconciliation ? <>
      <p>{reconciliation.reason}</p>
      <Descriptions bordered size="small"><Descriptions.Item label="合并净利润">{amount(reconciliation.consolidatedNetProfit)}</Descriptions.Item>
        <Descriptions.Item label="调节项目合计">{amount(reconciliation.adjustmentTotal)}</Descriptions.Item>
        <Descriptions.Item label="计算 CFO">{amount(reconciliation.calculatedOperatingCashFlow)}</Descriptions.Item>
        <Descriptions.Item label="披露 CFO">{amount(reconciliation.reportedOperatingCashFlow)}</Descriptions.Item>
        <Descriptions.Item label="残差">{amount(reconciliation.residual)}</Descriptions.Item><Descriptions.Item label="状态">{reconciliation.status}</Descriptions.Item>
        <Descriptions.Item label="期间/范围">{reconciliation.currentPeriod || '未知'} / {reconciliation.scope || '未知'}</Descriptions.Item></Descriptions>
      <Table size="small" scroll={{ x: 650 }} pagination={false} rowKey="fieldId" dataSource={reconciliation.adjustments || []} columns={[
        { title: '调节项目', dataIndex: 'name' }, { title: '本期（元）', dataIndex: 'amount', render: amount },
        { title: '上期（元）', dataIndex: 'priorAmount', render: amount }, { title: '状态', dataIndex: 'dataQualityStatus' },
        { title: '证据', render: (_, row) => <details><summary>第 {row.page ?? '-'} 页 · {row.period || '-'} · {row.scope || '-'}</summary><pre style={{ whiteSpace: 'pre-wrap' }}>{row.snippet}</pre></details> },
      ]} />
    </> : <p>历史结果未执行本项检查</p>}
    <h3>非经常性损益模板与扣减角色</h3>
    <Table size="small" scroll={{ x: 800 }} pagination={false} rowKey="fieldId" dataSource={nonRecurring} columns={[
      { title: '项目', dataIndex: 'name' }, { title: '金额（元）', render: (_, row) => row.amount == null ? '原文未找到' : amount(row.amount) },
      { title: '合计角色', render: (_, row) => ['nri_tax', 'nri_minority_impact'].includes(row.fieldId) ? '减项（保留原始符号）' : row.fieldId === 'nri_total' ? '披露合计' : row.amount == null ? '不纳入金额合计' : '加项' },
      { title: '证据', render: (_, row) => row.page ? <details><summary>第 {row.page} 页</summary><pre style={{ whiteSpace: 'pre-wrap' }}>{row.snippet}</pre></details> : '原文未找到' },
    ]} />
  </div>
}

export function ValuationSummary({ valuation }) {
  const base = valuation?.dcfValuation?.baseCase
  const inputRows = valuation?.forecastInputs || []
  const sensitivity = valuation?.sensitivityAnalysis
  const scenarios = [base, valuation?.dcfValuation?.pessimistic, valuation?.dcfValuation?.optimistic].filter(Boolean)
  return <div style={{ margin: '16px 0' }}>
    <p>参数版本：{valuation?.parameterVersion || '历史结果未登记'}；范围：{valuation?.valuationScope || '未确认'}</p>
    <p>净债务诊断：{valuation?.netDebtDiagnostic == null ? `数据不完整：${(valuation?.netDebtDiagnosticMissingItems || []).join('；') || '历史结果未执行本项检查'}` : `${amount(valuation.netDebtDiagnostic)} 元`}（${valuation?.netDebtDiagnosticStatus || '历史结果未执行本项检查'}；诊断口径，未经完整桥接不作为正式股权估值）</p>
    {valuation?.netDebtDiagnosticFormula && <p>{valuation.netDebtDiagnosticFormula}</p>}
    {!!valuation?.netDebtDiagnosticInputs?.length && <details><summary>现金、债务及现金调节证据</summary>
      <Table size="small" scroll={{ x: 750 }} pagination={false} rowKey="fieldId" dataSource={valuation.netDebtDiagnosticInputs} columns={[
        { title: '项目', dataIndex: 'name' }, { title: '原披露金额', dataIndex: 'amount', render: amount },
        { title: '单位', dataIndex: 'unit' }, { title: '期间', dataIndex: 'period' }, { title: '范围', dataIndex: 'scope' },
        { title: '证据', render: (_, row) => <details><summary>第 {row.page ?? '-'} 页 · {row.sourceFile || '来源未保存'}</summary><pre style={{ whiteSpace: 'pre-wrap' }}>{row.snippet}</pre></details> },
      ]} />
    </details>}
    {base && <>
      <Descriptions bordered size="small"><Descriptions.Item label="企业价值 EV">{amount(base.enterpriseValue)}</Descriptions.Item>
        <Descriptions.Item label="普通股权益">{base.equityValue == null ? '桥接待确认' : amount(base.equityValue)}</Descriptions.Item>
        <Descriptions.Item label="每股价值">{base.valuePerShare == null ? '桥接/股数待确认' : amount(base.valuePerShare)}</Descriptions.Item>
        <Descriptions.Item label="终值">{amount(base.terminalValue)}</Descriptions.Item><Descriptions.Item label="终值现值">{amount(base.terminalPresentValue)}</Descriptions.Item>
        <Descriptions.Item label="终值现值占比">{percent(base.terminalShare)}</Descriptions.Item></Descriptions>
      <Table size="small" scroll={{ x: 1250 }} pagination={false} rowKey="year" dataSource={base.freeCashFlows.map((fcff, i) => ({ ...inputRows[i], year: i + 1, fcff, factor: base.discountFactors?.[i], pv: base.presentValues?.[i] }))} columns={[
        { title: '年', dataIndex: 'year' }, ...[['ebit', 'EBIT'], ['taxRate', '税率'], ['depreciation', '折旧摊销'], ['capex', '资本开支'], ['workingCapitalIncrease', '营运资本增加'], ['fcff', '标准FCFF'], ['pv', '现值']].map(([dataIndex, title]) => ({ title, dataIndex, render: dataIndex === 'taxRate' ? percent : amount })),
        { title: '折现因子', dataIndex: 'factor' }, { title: '依据', dataIndex: 'source' },
      ]} />
      <Table size="small" pagination={false} rowKey="name" dataSource={scenarios} columns={[
        { title: '明确输入的情景', dataIndex: 'name' }, { title: '逐年FCFF', render: (_, row) => row.freeCashFlows?.map(amount).join(' / ') },
        { title: 'EV', dataIndex: 'enterpriseValue', render: amount }, { title: '每股价值', dataIndex: 'valuePerShare', render: amount }, { title: '参数版本', dataIndex: 'parameterVersion' },
      ]} />
    </>}
    {sensitivity && <Table style={{ marginTop: 16 }} size="small" scroll={{ x: 700 }} pagination={false} rowKey="wacc" dataSource={(sensitivity.waccRange || []).map((wacc, i) => ({ wacc, cells: sensitivity.valuationMatrix?.[i] || [] }))} columns={[
      { title: 'WACC / g', dataIndex: 'wacc', render: percent }, ...(sensitivity.terminalGrowthRange || []).map((g, i) => ({ title: percent(g), key: i, render: (_, row) => row.cells[i] == null ? '无效（WACC≤g等）' : amount(row.cells[i]) })),
    ]} />}
    {(valuation?.assumedPePrice != null || valuation?.assumedPbPrice != null) && <Alert type="info" message={`用户倍数假设价格：PE ${amount(valuation.assumedPePrice)}；PB ${amount(valuation.assumedPbPrice)}。来源：${valuation.multipleSource}；不代表行业平均或投资评级。`} />}
    {valuation?.bridgeSources && <details><summary>逐项桥接来源与范围</summary>{Object.entries(valuation.bridgeSources).map(([id, source]) => <p key={id}>{id}：{source}</p>)}</details>}
  </div>
}
