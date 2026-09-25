import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Spin, Tabs, Descriptions, Table, Tag, Button } from 'antd'
import { taskAPI } from '../services/api'

const { TabPane } = Tabs

function explainReportError(error) {
  const raw = error?.response?.data?.message || ''
  if (raw.includes('not completed')) return '任务还在跑，或者还没成功结束。报告只在状态变成已完成后出现。'
  if (raw.includes('分析结果不存在')) return '这次任务没有计算快照。多半是改代码之前创建的旧任务，重新建一个即可。'
  if (!error?.response) return '没有连上后端。先确认 8080 端口的服务已启动。'
  return raw || '报告没有加载出来。'
}

function ReportView() {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [errorText, setErrorText] = useState('')

  useEffect(() => {
    let active = true
    const fetchReport = async () => {
      try {
        setLoading(true)
        setErrorText('')
        const data = await taskAPI.getReport(taskId)
        if (active) setReport(data)
      } catch (error) {
        if (active) setErrorText(explainReportError(error))
      } finally {
        if (active) setLoading(false)
      }
    }
    fetchReport()
    return () => {
      active = false
    }
  }, [taskId])

  if (loading) {
    return (
      <div className="loading-container">
        <Spin size="large" tip="正在读取报告" />
      </div>
    )
  }

  if (!report) {
    return (
      <div className="empty-panel">
        <div className="kicker">报告</div>
        <h2>这份报告还不能打开</h2>
        <p>{errorText || '没有可展示的报告。'}</p>
        <Button type="primary" onClick={() => navigate(`/tasks/${taskId}`)}>返回任务</Button>
      </div>
    )
  }

  return (
    <div>
      <article className="report-sheet">
        <div className="kicker">研究报告</div>
        <h1>{report.title}</h1>
        <div className="report-meta">
          <span>{report.companyOverview?.companyName}</span>
          <span>{report.companyOverview?.companyCode}</span>
          <span>{report.companyOverview?.reportPeriod}</span>
          <span>{report.companyOverview?.reportType}</span>
        </div>
        <Tabs defaultActiveKey="1">
          <TabPane tab="公司概况" key="1">
            <Descriptions bordered>
              <Descriptions.Item label="公司代码">
                {report.companyOverview?.companyCode}
              </Descriptions.Item>
              <Descriptions.Item label="公司名称">
                {report.companyOverview?.companyName}
              </Descriptions.Item>
              <Descriptions.Item label="行业">
                {report.companyOverview?.industry || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="板块">
                {report.companyOverview?.sector || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="报告期">
                {report.companyOverview?.reportPeriod}
              </Descriptions.Item>
              <Descriptions.Item label="报告类型">
                {report.companyOverview?.reportType || '-'}
              </Descriptions.Item>
            </Descriptions>
          </TabPane>

          <TabPane tab="财务指标" key="2">
            {report.metrics ? (
              <Card>
                <h3>核心指标</h3>
                <Descriptions bordered column={2}>
                  <Descriptions.Item label="营业收入">
                    {report.metrics.coreMetrics?.revenue || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="归母净利润">
                    {report.metrics.coreMetrics?.netProfit || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="扣非净利润">
                    {report.metrics.coreMetrics?.netProfitDeducted || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="经营现金流">
                    {report.metrics.coreMetrics?.operatingCashFlow || '-'}
                  </Descriptions.Item>
                </Descriptions>

                <h3 style={{ marginTop: 24 }}>财务比率</h3>
                <Descriptions bordered column={2}>
                  <Descriptions.Item label="毛利率">
                    {report.metrics.ratios?.grossMargin || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="净利率">
                    {report.metrics.ratios?.netMargin || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="ROE">
                    {report.metrics.ratios?.roe || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="ROA">
                    {report.metrics.ratios?.roa || '-'}
                  </Descriptions.Item>
                </Descriptions>
              </Card>
            ) : (
              <p>暂无数据</p>
            )}
          </TabPane>

          <TabPane tab="异常信号" key="3">
            {report.anomalies && report.anomalies.length > 0 ? (
              <div>
                {report.anomalies.map((anomaly, index) => (
                  <Card key={index} style={{ marginBottom: 16 }}>
                    <h3>{anomaly.name}</h3>
                    <p>{anomaly.description}</p>
                    <div>
                      <Tag
                        color={
                          anomaly.severity === 'HIGH'
                            ? 'red'
                            : anomaly.severity === 'MEDIUM'
                            ? 'orange'
                            : 'blue'
                        }
                      >
                        {anomaly.severity}
                      </Tag>
                      {anomaly.ruleTriggered && (
                        <Tag>触发规则: {anomaly.ruleTriggered}</Tag>
                      )}
                    </div>
                    {anomaly.recommendation && (
                      <p style={{ marginTop: 12 }}>
                        <strong>建议:</strong> {anomaly.recommendation}
                      </p>
                    )}
                  </Card>
                ))}
              </div>
            ) : (
              <p>未检测到异常信号</p>
            )}
          </TabPane>

          <TabPane tab="财务质量分析" key="4">
            {report.qualityAnalysis ? (
              <Card>
                <div className="report-section">
                  <h3>成长能力</h3>
                  <p>{report.qualityAnalysis.growthAnalysis || '暂无分析'}</p>
                </div>
                <div className="report-section">
                  <h3>盈利能力</h3>
                  <p>{report.qualityAnalysis.profitabilityAnalysis || '暂无分析'}</p>
                </div>
                <div className="report-section">
                  <h3>现金流质量</h3>
                  <p>{report.qualityAnalysis.cashFlowAnalysis || '暂无分析'}</p>
                </div>
                <div className="report-section">
                  <h3>综合评估</h3>
                  <p>{report.qualityAnalysis.overallAssessment || '暂无评估'}</p>
                </div>
              </Card>
            ) : (
              <p>暂无分析</p>
            )}
          </TabPane>

          <TabPane tab="勾稽" key="articulation">
            {report.articulationChecks?.length ? (
              report.articulationChecks.map((check) => (
                <Card key={check.name} style={{ marginBottom: 12 }}>
                  <Tag>{check.statementType || '事实'}</Tag>
                  <Tag color={check.status === 'PASS' ? 'green' : check.status === 'FAIL' ? 'red' : 'default'}>{check.status}</Tag>
                  <p>{check.name}</p>
                  <p>{check.detail}</p>
                </Card>
              ))
            ) : (
              <p>未做勾稽</p>
            )}
          </TabPane>

          {report.valuation && (
            <TabPane tab="估值结果" key="5">
              <Card>
                <h3>估值区间</h3>
                {report.valuation.valuationRange ? (
                  <>
                    <Descriptions bordered>
                      <Descriptions.Item label="低值">{report.valuation.valuationRange.low ?? '未提取'}</Descriptions.Item>
                      <Descriptions.Item label="中值">{report.valuation.valuationRange.mid ?? '未提取'}</Descriptions.Item>
                      <Descriptions.Item label="高值">{report.valuation.valuationRange.high ?? '未提取'}</Descriptions.Item>
                    </Descriptions>
                    <p style={{ marginTop: 12 }}>{report.valuation.valuationRange.methodology}</p>
                    <p>{report.valuation.valuationRange.applicabilityNote}</p>
                  </>
                ) : (
                  <p>估值区间未计算</p>
                )}
                <Table
                  style={{ marginTop: 16 }}
                  size="small"
                  pagination={false}
                  rowKey="parameter"
                  dataSource={report.valuation.assumptions || []}
                  columns={[
                    { title: '参数', dataIndex: 'parameter' },
                    { title: '取值', dataIndex: 'value' },
                    { title: '来源', dataIndex: 'source' },
                    { title: '依据', dataIndex: 'rationale' },
                  ]}
                />
              </Card>
            </TabPane>
          )}

          <TabPane tab="主要风险" key="6">
            {report.risks && report.risks.length > 0 ? (
              <div>
                {report.risks.map((risk, index) => (
                  <Card key={index} style={{ marginBottom: 16 }}>
                    <h4>{risk.category}</h4>
                    <p>{risk.description}</p>
                    <Tag color="red">{risk.severity}</Tag>
                  </Card>
                ))}
              </div>
            ) : (
              <p>暂无风险提示</p>
            )}
          </TabPane>

          <TabPane tab="结论与建议" key="7">
            <Card>
              <p>{report.conclusion || '本报告由FinAI自动生成，仅供参考。'}</p>
            </Card>
          </TabPane>

          <TabPane tab="运行摘要" key="8">
            {report.executionSummary && (
              <Descriptions bordered>
                <Descriptions.Item label="LLM模型">
                  {report.executionSummary.llmModel}
                </Descriptions.Item>
                <Descriptions.Item label="LLM调用次数">
                  {report.executionSummary.llmCallCount}
                </Descriptions.Item>
                <Descriptions.Item label="执行时长">
                  {report.executionSummary.executionTimeMs
                    ? `${(report.executionSummary.executionTimeMs / 1000).toFixed(2)}秒`
                    : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="生成时间">
                  {report.executionSummary.generatedAt}
                </Descriptions.Item>
              </Descriptions>
            )}
          </TabPane>
        </Tabs>
      </article>
    </div>
  )
}

export default ReportView
