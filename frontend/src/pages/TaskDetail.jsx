import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Descriptions, Tag, Progress, Button, Space, Spin, message, Table, Alert, Popover } from 'antd'
import { ReloadOutlined, FileTextOutlined, StopOutlined, MessageOutlined } from '@ant-design/icons'
import { taskAPI, auditAPI } from '../services/api'
import PageHeader from '../components/PageHeader'

function fmtAmount(value) {
  if (value === null || value === undefined || value === '') return '未提取'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return num.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function fmtPct(value) {
  if (value === null || value === undefined || value === '') return '未提取'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return `${(num * 100).toFixed(2)}%`
}

function sourceLabel(source) {
  const map = {
    extract: '事实提取',
    formula: '公式假设',
    user: '用户给定',
    llm_suggest: '模型建议',
  }
  return map[source] || source || '未标注'
}

function TaskDetail() {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [task, setTask] = useState(null)
  const [loading, setLoading] = useState(true)
  const [metrics, setMetrics] = useState(null)
  const [anomalies, setAnomalies] = useState([])
  const [valuation, setValuation] = useState(null)
  const [evidence, setEvidence] = useState([])
  const [auditLogs, setAuditLogs] = useState([])
  const [resultNote, setResultNote] = useState('')
  const statusRef = useRef(null)

  useEffect(() => {
    statusRef.current = task?.status
  }, [task?.status])

  useEffect(() => {
    let cancelled = false

    const load = async (silent) => {
      if (!silent) setLoading(true)
      try {
        const taskData = await taskAPI.getTask(taskId)
        if (cancelled) return
        setTask(taskData)
        statusRef.current = taskData.status
        if (taskData.status === 'COMPLETED') {
          const [metricsData, anomaliesData, valuationData, evidenceData, auditData] = await Promise.all([
            taskAPI.getMetrics(taskId).catch((error) => ({ __error: error })),
            taskAPI.getAnomalies(taskId).catch(() => []),
            taskAPI.getValuation(taskId).catch(() => null),
            taskAPI.getEvidence(taskId).catch(() => []),
            auditAPI.getTaskLogs(taskId).catch(() => []),
          ])
          if (cancelled) return
          if (metricsData?.__error) {
            const raw = metricsData.__error?.response?.data?.message || ''
            setResultNote(raw.includes('分析结果不存在')
              ? '这次任务没有计算快照。重新上传财报再建一个任务，指标、异常和估值才会出现。'
              : (raw || '指标没有加载出来。'))
            setMetrics(null)
            setAnomalies([])
            setValuation(null)
            setEvidence([])
            setAuditLogs([])
          } else {
            setResultNote('')
            setMetrics(metricsData)
            setAnomalies(anomaliesData || [])
            setValuation(valuationData)
            setEvidence(evidenceData || [])
            setAuditLogs(auditData || [])
          }
        }
      } catch (error) {
        if (!silent) message.error('加载任务详情失败')
      } finally {
        if (!cancelled && !silent) setLoading(false)
      }
    }

    load(false)
    const interval = setInterval(() => {
      const status = statusRef.current
      if (status === 'RUNNING' || status === 'QUEUED' || status === 'CREATED') {
        load(true)
      }
    }, 5000)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [taskId])

  const handleCancel = async () => {
    try {
      await taskAPI.cancelTask(taskId)
      message.success('任务已取消')
      const taskData = await taskAPI.getTask(taskId)
      setTask(taskData)
    } catch (error) {
      message.error('取消任务失败')
    }
  }

  const getStatusTag = (status) => {
    const statusConfig = {
      CREATED: { color: 'default', text: '已创建' },
      QUEUED: { color: 'processing', text: '排队中' },
      RUNNING: { color: 'processing', text: '运行中' },
      COMPLETED: { color: 'success', text: '已完成' },
      FAILED: { color: 'error', text: '失败' },
      CANCELLED: { color: 'default', text: '已取消' },
    }
    const config = statusConfig[status] || statusConfig.CREATED
    return <Tag color={config.color}>{config.text}</Tag>
  }

  const evidenceFor = (fieldId) => evidence.filter((item) => item.fieldId === fieldId)

  const evidencePopover = (fieldId) => {
    const rows = evidenceFor(fieldId)
    if (rows.length === 0) return '未提取'
    return (
      <Popover
        title="证据"
        content={rows.map((item) => (
          <div key={`${item.id}-${item.period}-${item.cellRef}`} style={{ maxWidth: 360, marginBottom: 8 }}>
            <div>期间 {item.period} · {item.unit || '单位未标注'} · 第 {item.page ?? '-'} 页</div>
            <div>{item.snippet || item.value}</div>
          </div>
        ))}
      >
        <a>查看来源</a>
      </Popover>
    )
  }

  if (loading && !task) {
    return (
      <div>
        <PageHeader kicker="任务" title="正在读取" description="取任务状态和计算结果。" />
        <div className="center-state"><Spin /></div>
      </div>
    )
  }

  if (!task) {
    return <div>任务不存在</div>
  }

  const core = metrics?.coreMetrics || {}
  const yoy = metrics?.yoyGrowth || {}
  const ratios = metrics?.ratios || {}
  const metricRows = [
    ['营业收入', core.revenue, yoy.revenueGrowth, 'revenue'],
    ['归母净利润', core.netProfit, yoy.netProfitGrowth, 'net_profit'],
    ['扣非净利润', core.netProfitDeducted, yoy.netProfitDeductedGrowth, 'net_profit_deducted'],
    ['经营活动现金流', core.operatingCashFlow, yoy.operatingCashFlowGrowth, 'operating_cash_flow'],
    ['自由现金流', core.freeCashFlow, null, 'capex'],
    ['资产总计', core.totalAssets, null, 'total_assets'],
    ['负债合计', core.totalLiabilities, null, 'total_liabilities'],
    ['归母权益', core.netAssets, null, 'net_assets'],
    ['少数股东权益', core.minorityInterest, null, 'minority_interest'],
    ['期末总股本', core.sharesOutstanding, null, 'shares_outstanding'],
    ['基本每股收益', core.basicEps, null, 'basic_eps'],
  ].map(([name, amount, growth, fieldId]) => ({ name, amount, growth, fieldId }))

  const ratioRows = [
    ['毛利率', ratios.grossMargin, 'pct'],
    ['净利率', ratios.netMargin, 'pct'],
    ['ROE（期末权益）', ratios.roe, 'pct'],
    ['ROA', ratios.roa, 'pct'],
    ['资产负债率', ratios.assetLiabilityRatio, 'pct'],
    ['流动比率', ratios.currentRatio, 'multiple'],
    ['经营现金流/归母净利润', ratios.ocfToNetProfit, 'multiple'],
  ].map(([name, value, kind]) => ({ name, value, kind }))

  const sensitivity = valuation?.sensitivityAnalysis
  const sensitivityColumns = [
    { title: 'WACC \\ 永续g', dataIndex: 'wacc', width: 120 },
    ...(sensitivity?.terminalGrowthRange || []).map((growth, index) => ({
      title: fmtPct(growth),
      dataIndex: `c${index}`,
      render: (value) => fmtAmount(value),
    })),
  ]
  const sensitivityRows = (sensitivity?.waccRange || []).map((wacc, rowIndex) => {
    const row = { key: rowIndex, wacc: fmtPct(wacc) }
    ;(sensitivity.valuationMatrix?.[rowIndex] || []).forEach((cell, index) => {
      row[`c${index}`] = cell
    })
    return row
  })

  return (
    <div>
      <PageHeader
        kicker={task.companyCode}
        title={task.companyName || '任务详情'}
        description={`${task.reportPeriod || ''} · ${task.analysisType || ''} · ${task.taskId}`}
      />
      {task.companyCode === 'DEMO01' && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="内置样例"
          description="底稿是系统自带的报表文本，不是上市公司真实年报。指标、异常和估值由计算链路生成。"
        />
      )}
      <Card
        title="执行状态"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => window.location.reload()}>
              刷新
            </Button>
            {task.status === 'COMPLETED' && (
              <>
                <Button type="primary" icon={<FileTextOutlined />} onClick={() => navigate(`/tasks/${taskId}/report`)}>
                  查看报告
                </Button>
                <Button icon={<MessageOutlined />} onClick={() => navigate(`/tasks/${taskId}/chat`)}>
                  对话追问
                </Button>
              </>
            )}
            {(task.status === 'RUNNING' || task.status === 'QUEUED') && (
              <Button danger icon={<StopOutlined />} onClick={handleCancel}>
                取消任务
              </Button>
            )}
          </Space>
        }
      >
        <Descriptions bordered column={2}>
          <Descriptions.Item label="任务ID">{task.taskId}</Descriptions.Item>
          <Descriptions.Item label="状态">{getStatusTag(task.status)}</Descriptions.Item>
          <Descriptions.Item label="公司代码">{task.companyCode}</Descriptions.Item>
          <Descriptions.Item label="公司名称">{task.companyName}</Descriptions.Item>
          <Descriptions.Item label="报告期">{task.reportPeriod}</Descriptions.Item>
          <Descriptions.Item label="分析类型">{task.analysisType}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{task.createdAt ? new Date(task.createdAt).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="完成时间">{task.completedAt ? new Date(task.completedAt).toLocaleString() : '-'}</Descriptions.Item>
        </Descriptions>

        {(task.status === 'RUNNING' || task.status === 'QUEUED' || task.status === 'CREATED') && (
          <div style={{ marginTop: 24 }}>
            <div style={{ marginBottom: 8 }}>当前步骤：{task.currentStep || '准备中'}</div>
            <Progress percent={task.progress || 0} status="active" />
          </div>
        )}

        {task.status === 'FAILED' && task.errorMessage && (
          <Alert style={{ marginTop: 24 }} type="error" message="任务失败" description={task.errorMessage} />
        )}
      </Card>

      {task.status === 'COMPLETED' && resultNote && (
        <Alert style={{ marginTop: 16 }} type="warning" showIcon message="还没有可展示的计算结果" description={resultNote} />
      )}

      {task.status === 'COMPLETED' && !resultNote && (
        <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 16 }}>
          <Card title={`财务指标（${metrics?.unit || '单位未标注'}，同比；环比未计算）`}>
            <Table
              size="small"
              pagination={false}
              rowKey="fieldId"
              dataSource={metricRows}
              columns={[
                { title: '科目', dataIndex: 'name' },
                { title: '本期', dataIndex: 'amount', render: fmtAmount },
                { title: '同比', dataIndex: 'growth', render: fmtPct },
                { title: '证据', dataIndex: 'fieldId', render: evidencePopover },
              ]}
            />
            <Table
              style={{ marginTop: 16 }}
              size="small"
              pagination={false}
              rowKey="name"
              dataSource={ratioRows}
              columns={[
                { title: '比率', dataIndex: 'name' },
                {
                  title: '数值',
                  dataIndex: 'value',
                  render: (value, record) => (record.kind === 'pct' ? fmtPct(value) : fmtAmount(value)),
                },
              ]}
            />
          </Card>

          {(metrics?.nonRecurringItems || []).length > 0 && (
            <Card title="非经常性损益明细">
              <Table
                size="small"
                pagination={false}
                rowKey="fieldId"
                dataSource={metrics.nonRecurringItems}
                columns={[
                  { title: '项目', dataIndex: 'name' },
                  { title: '金额', dataIndex: 'amount', render: fmtAmount },
                  { title: '页码', dataIndex: 'page' },
                ]}
              />
            </Card>
          )}

          <Card title="异常信号">
            {anomalies.length === 0 ? (
              <Alert type="info" message="三条规则都没有命中，或缺少计算所需字段。" />
            ) : (
              anomalies.map((anomaly) => (
                <Card key={anomaly.anomalyId || anomaly.name} type="inner" style={{ marginBottom: 12 }}>
                  <Space>
                    <strong>{anomaly.name}</strong>
                    <Tag color={anomaly.severity === 'HIGH' ? 'red' : anomaly.severity === 'MEDIUM' ? 'orange' : 'blue'}>
                      {anomaly.severity}
                    </Tag>
                    <Tag>{anomaly.verificationStatus}</Tag>
                  </Space>
                  <p>{anomaly.description}</p>
                  <p>
                    规则 {anomaly.ruleTriggered} · 版本 {anomaly.ruleVersion} · 实际值{' '}
                    {anomaly.ruleTriggered === 'accounting_policy_mention' ? '未提取' : fmtPct(anomaly.actualValue)} · 阈值{' '}
                    {anomaly.ruleTriggered === 'accounting_policy_mention' ? '未提取' : fmtPct(anomaly.threshold)}
                  </p>
                  <p>{anomaly.verificationDetails}</p>
                  <p>{anomaly.recommendation}</p>
                  {(anomaly.evidence || []).filter(Boolean).map((item, index) => (
                    <div key={index} className="evidence-item">
                      第 {item.page ?? '-'} 页 · {item.fieldName} · {item.snippet || item.value}
                    </div>
                  ))}
                </Card>
              ))
            )}
          </Card>

          <Card title="估值">
            {!valuation?.valuationRange ? (
              <Alert
                type="warning"
                message="估值区间未计算"
                description={(valuation?.assumptions || []).map((item) => `${item.parameter}：${item.rationale}`).join(' ') || '缺少经营现金流或历史增速。'}
              />
            ) : (
              <>
                <Descriptions bordered column={3}>
                  <Descriptions.Item label="低">{fmtAmount(valuation.valuationRange.low)}</Descriptions.Item>
                  <Descriptions.Item label="中">{fmtAmount(valuation.valuationRange.mid)}</Descriptions.Item>
                  <Descriptions.Item label="高">{fmtAmount(valuation.valuationRange.high)}</Descriptions.Item>
                </Descriptions>
                <p style={{ marginTop: 12 }}>{valuation.valuationRange.methodology}</p>
                <p>{valuation.valuationRange.applicabilityNote}</p>
                <Table
                  style={{ marginTop: 16 }}
                  size="small"
                  pagination={false}
                  rowKey="wacc"
                  columns={sensitivityColumns}
                  dataSource={sensitivityRows}
                />
              </>
            )}
            <Table
              style={{ marginTop: 16 }}
              size="small"
              pagination={false}
              rowKey="parameter"
              dataSource={valuation?.assumptions || []}
              columns={[
                { title: '参数', dataIndex: 'parameter' },
                { title: '取值', dataIndex: 'value', render: (value) => value ?? '未提取' },
                { title: '来源', dataIndex: 'source', render: sourceLabel },
                { title: '依据', dataIndex: 'rationale' },
              ]}
            />
          </Card>

          <Card title="审计记录">
            <Table
              size="small"
              pagination={false}
              rowKey={(row, index) => `${row.operation}-${index}`}
              dataSource={auditLogs}
              columns={[
                { title: '操作', dataIndex: 'operation', width: 180 },
                { title: '状态', dataIndex: 'status', width: 90 },
                { title: '结果', dataIndex: 'output', ellipsis: true },
                { title: '时间', dataIndex: 'createdAt', width: 180 },
              ]}
            />
          </Card>
        </Space>
      )}
    </div>
  )
}

export default TaskDetail
