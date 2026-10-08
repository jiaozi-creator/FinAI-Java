import { Table, Tag } from 'antd'

export const statusLabels = {
  VALUE_AVAILABLE: '已提取/已计算', DISCLOSED_ZERO: '年报披露为零', SOURCE_NOT_FOUND: '原文未找到',
  EXTRACTION_FAILED: '已披露但抽取失败', CALCULABLE_INPUT_AVAILABLE: '可按公式计算', INPUT_INCOMPLETE: '输入不完整',
  PERIOD_MISMATCH: '期间冲突', SCOPE_MISMATCH: '范围冲突', UNIT_MISMATCH: '单位冲突',
  NOT_APPLICABLE: '不适用', BLOCKED_BY_DATA_QUALITY: '因数据质量阻断', COMPUTED: '已计算',
  PASSED: '复核通过', NEEDS_REVIEW: '待复核',
}

export function sourceLabel(source) {
  return ({ extract: '财报事实', formula: '公式计算', default_unconfirmed: '默认值 待确认', user_confirmed: '用户已确认',
    llm_suggested: '模型建议 待确认', user: '用户给定', llm_suggest: '模型建议 待确认' })[source] || source || '未标注'
}

export function formatMetric(value, detail = {}, kind) {
  if (value === null || value === undefined || value === '') return statusLabels[detail.status] || '数据不可用';
  const number = Number(value)
  if (!Number.isFinite(number)) return '无效数值'
  if (kind === 'pct' || detail.unit === '%') return `${(number * 100).toFixed(detail.displayScale ?? 2)}%`
  const scale = detail.displayScale ?? 2
  return number.toLocaleString('zh-CN', { minimumFractionDigits: scale, maximumFractionDigits: scale })
}

const fields = [
  ['营业收入', 'revenue', 'revenue', 'revenueGrowth'],
  ['营业总收入', 'revenue_total', 'revenueTotal', 'revenueTotalGrowth'],
  ['合并净利润', 'net_profit_consolidated', 'netProfitConsolidated'],
  ['归母净利润', 'net_profit', 'netProfit', 'netProfitGrowth'],
  ['扣非归母净利润', 'net_profit_deducted', 'netProfitDeducted', 'netProfitDeductedGrowth'],
  ['经营现金流', 'operating_cash_flow', 'operatingCashFlow', 'operatingCashFlowGrowth'],
  ['资本开支代理值', 'capex', 'capexProxy'],
  ['简式自由现金流', 'simple_free_cash_flow', 'simpleFreeCashFlow', 'simpleFreeCashFlowGrowth'],
  ['资产总计', 'total_assets', 'totalAssets', 'totalAssetsGrowth'],
  ['负债合计', 'total_liabilities', 'totalLiabilities', 'totalLiabilitiesGrowth'],
  ['归母权益', 'net_assets', 'netAssets', 'netAssetsGrowth'],
  ['少数股东权益', 'minority_interest', 'minorityInterest', 'minorityInterestGrowth'],
  ['合并权益', 'total_equity', 'totalEquity'], ['货币资金', 'cash', 'cash'],
  ['应收票据', 'notes_receivable', 'notesReceivable'],
  ['期末总股本', 'shares_outstanding', 'sharesOutstanding', 'sharesOutstandingGrowth'],
  ['基本每股收益', 'basic_eps', 'basicEps', 'basicEpsGrowth'],
]
const ratios = [
  ['毛利率', 'gross_margin', 'grossMargin', '%'], ['归母净利率', 'net_margin', 'netMargin', '%'],
  ['合并净利率（营业收入分母）', 'consolidated_net_margin', 'consolidatedNetMargin', '%'],
  ['加权平均 ROE（披露）', 'disclosed_weighted_roe', 'disclosedWeightedRoe', '%'],
  ['平均归母权益 ROE', 'average_equity_roe', 'averageEquityRoe', '%'], ['ROA', 'roa', 'roa', '%'],
  ['资产负债率', 'asset_liability_ratio', 'assetLiabilityRatio', '%'], ['流动比率', 'current_ratio', 'currentRatio', '倍'],
  ['经营现金流/归母净利润', 'ocf_to_net_profit', 'ocfToNetProfit', '倍'],
]

export function metricRows(metrics) {
  const details = metrics?.metricDetails || {}
  return fields.map(([name, id, key, growth]) => {
    const value = metrics?.coreMetrics?.[key] ?? (id === 'simple_free_cash_flow' ? metrics?.coreMetrics?.freeCashFlow : undefined)
    const detail = details[id] || { value, status: value == null ? 'INPUT_INCOMPLETE' : 'VALUE_AVAILABLE',
      unit: id === 'shares_outstanding' ? '股' : id === 'basic_eps' ? '元/股' : '元',
      displayScale: id === 'shares_outstanding' ? 0 : id === 'basic_eps' ? Math.max(2, String(value ?? '').split('.')[1]?.length || 0) : 2 }
    const growthDetail = details[`${id}_growth`] || { value: metrics?.yoyGrowth?.[growth], unit: '%', status: 'INPUT_INCOMPLETE', missingReason: '历史快照缺少同比元数据' }
    return { name, id, detail, growthDetail, hasGrowth: Boolean(growth) }
  })
}

export function ratioRows(metrics) {
  return ratios.map(([name, id, key, unit]) => ({ name, id, detail: metrics?.metricDetails?.[id] || {
    value: metrics?.ratios?.[key] ?? (key === 'disclosedWeightedRoe' ? metrics?.ratios?.roe : null), unit,
    status: metrics?.ratios?.[key] == null ? 'INPUT_INCOMPLETE' : 'VALUE_AVAILABLE',
  } }))
}

export function MetricEvidence({ detail }) {
  return <details style={{ maxWidth: 480, overflowWrap: 'anywhere' }}>
    <summary>公式与证据</summary>
    <p>{detail.formula || '财报直接披露'} · {sourceLabel(detail.source)}</p>
    <p>{detail.scope || '范围未标注'} · {detail.period || '期间未标注'}</p>
    {(detail.evidenceRefs || []).map((e, i) => <div key={i} style={{ marginBottom: 8, whiteSpace: 'pre-wrap' }}>
      第 {e.page ?? '-'} 页 · {e.period} · {e.unit} · {e.value}<br />{e.snippet}
    </div>)}
    {!(detail.evidenceRefs || []).length && <p>证据未保存</p>}
  </details>
}

export default function FinancialMetricsTable({ metrics }) {
  return <>
    <Table size="small" pagination={false} rowKey="id" scroll={{ x: 1050 }} dataSource={metricRows(metrics)} columns={[
      { title: '科目', dataIndex: 'name', width: 180 },
      { title: '本期', render: (_, row) => formatMetric(row.detail.value, row.detail) },
      { title: '单位', render: (_, row) => row.detail.unit || '未标注' },
      { title: '同比', render: (_, row) => row.hasGrowth ? <><div>{formatMetric(row.growthDetail.value, row.growthDetail)}</div>
        {row.growthDetail.missingReason && <div>{row.growthDetail.missingReason}</div>}<MetricEvidence detail={row.growthDetail} /></> : '不适用' },
      { title: '数据状态与原因', render: (_, row) => <><Tag>{statusLabels[row.detail.status] || row.detail.status}</Tag>{row.detail.missingReason}</> },
      { title: '证据', render: (_, row) => <MetricEvidence detail={row.detail} /> },
    ]} />
    <Table style={{ marginTop: 16 }} size="small" pagination={false} rowKey="id" scroll={{ x: 850 }} dataSource={ratioRows(metrics)} columns={[
      { title: '比率', dataIndex: 'name', width: 240 },
      { title: '数值', render: (_, row) => formatMetric(row.detail.value, row.detail) },
      { title: '单位', render: (_, row) => row.detail.unit },
      { title: '数据状态与原因', render: (_, row) => <>{statusLabels[row.detail.status] || row.detail.status} {row.detail.missingReason}</> },
      { title: '公式与证据', render: (_, row) => <MetricEvidence detail={row.detail} /> },
    ]} />
  </>
}
