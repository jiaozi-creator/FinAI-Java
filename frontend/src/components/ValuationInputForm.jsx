import { useEffect, useState } from 'react'
import { Button, Checkbox, Form, Input, InputNumber, Modal, Alert, Select, message } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import { taskAPI } from '../services/api'

const components = [['ebit', 'EBIT（调整后经营利润）'], ['taxRate', '经营税率（0—1）'], ['depreciation', '折旧摊销'], ['capex', '经营资本开支'], ['workingCapitalIncrease', '经营营运资本增加']]
const bridges = [['usableCash', '可用现金'], ['grossDebt', '有息债务'], ['minorityAdjustment', '少数股东价值扣减'], ['nonCoreAssets', '非核心资产加回'], ['dilutedShares', '摊薄股数（股）']]
const definitions = { ebit: '利润起点、利息和非经营收益调整；不能直接将营业利润视为 EBIT', taxRate: '法定、历史实际或预测经营税率及选择依据', depreciation: '项目范围、来源及对应 EBIT 已扣费用，避免重复加回', capex: '现金支付代理或调整后经营资本开支；历史与预测依据分开', workingCapitalIncrease: '经营资产负债清单及变动；排除现金、有息债务，避免桥接重复' }
export default function ValuationInputForm({ taskId, disabled }) {
  const [open, setOpen] = useState(false), [saving, setSaving] = useState(false), [record, setRecord] = useState(null)
  const [form] = Form.useForm()
  const mode = Form.useWatch('inputMode', form) || 'COMPONENTS'
  const years = Form.useWatch('years', form) || 5
  const bridgeConfirmed = Form.useWatch('bridgePolicyConfirmed', form)
  const withScenarios = Form.useWatch('withScenarios', form)
  useEffect(() => { taskAPI.getValuationInputs(taskId).then(setRecord).catch(() => setRecord(null)) }, [taskId])
  const show = () => {
    form.resetFields()
    form.setFieldsValue({ inputMode: 'COMPONENTS', years: 5 })
    if (record?.parametersJson) form.setFieldsValue({ ...JSON.parse(record.parametersJson), confirmedBy: record.confirmedBy, rationale: record.rationale, assumptionsConfirmed: false, bridgePolicyConfirmed: false })
    setOpen(true)
  }
  const confirm = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const { confirmedBy, rationale, withScenarios, ...parameters } = values
      const clean = rows => (rows || []).slice(0, Number(parameters.years)).map((row, i) => ({ ...row, year: i + 1 }))
      parameters.forecast = clean(parameters.forecast)
      if (withScenarios) parameters.scenarios = Object.fromEntries(Object.entries(parameters.scenarios || {}).map(([name, rows]) => [name, clean(rows)]))
      else delete parameters.scenarios
      delete parameters.confirmedFcff
      delete parameters.confirmedGrowth
      const saved = await taskAPI.confirmValuationInputs(taskId, { parameters, confirmedBy, rationale })
      setRecord(saved)
      setOpen(false)
      message.success('确认版本已保存，正在创建新运行')
      await taskAPI.rerun(taskId)
      window.location.reload()
    } catch (error) { message.error(error.response?.data?.message || '确认或重算失败') }
    finally { setSaving(false) }
  }
  const numberInput = (name, label, required = true, min, max) => <Form.Item key={JSON.stringify(name)} name={name} label={label} rules={required ? [{ required: true }] : []}>
    <InputNumber stringMode min={min} max={max} style={{ width: '100%' }} />
  </Form.Item>
  const path = (prefix, title) => <section style={{ overflowX: 'auto' }}>
    <h4>{title}预测（金额统一为元）</h4>
    {Array.from({ length: Math.min(30, Math.max(1, Number(years))) }, (_, i) => <fieldset key={i} style={{ marginBottom: 16, padding: 12 }}>
      <legend>第 {i + 1} 年</legend>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(160px,1fr))', gap: 12 }}>
        {(mode === 'DIRECT' ? [['fcff', '标准 FCFF']] : components).map(([id, label]) => numberInput([...prefix, i, id], label, true, ['taxRate', 'capex', 'depreciation'].includes(id) ? 0 : undefined, id === 'taxRate' ? 1 : undefined))}
      </div>
      <Form.Item name={[...prefix, i, 'source']} label="本年度来源、预测依据及经营范围" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={2} /></Form.Item>
      {mode === 'COMPONENTS' && <Form.Item noStyle shouldUpdate>{({ getFieldValue }) => {
        const row = getFieldValue(prefix)?.[i] || {}
        const complete = components.every(([id]) => row[id] !== undefined && row[id] !== null && row[id] !== '')
        const preview = complete ? Number(row.ebit) * (1 - Number(row.taxRate)) + Number(row.depreciation) - Number(row.capex) - Number(row.workingCapitalIncrease) : null
        return <p>FCFF 预览：{preview == null ? '输入未完整' : preview.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}（最终由后端高精度计算）</p>
      }}</Form.Item>}
    </fieldset>)}
  </section>
  return <>
    <Button icon={<CheckOutlined />} disabled={disabled} onClick={show}>确认估值参数</Button>
    {record && <p>确认版本 {record.id} · {record.confirmedBy} · {record.confirmedAt}</p>}
    <Modal title="逐年标准 FCFF 与桥接确认" open={open} onCancel={() => setOpen(false)} onOk={confirm} confirmLoading={saving} okText="保存确认并创建新运行" width={1000}>
      <Alert type="info" message="逐年 FCFF 和折现参数确认后可计算 EV；桥接、股数未确认时不输出普通股权益或每股价值。不要以简式 FCF/CFO 替代标准 FCFF。" />
      <Form form={form} layout="vertical">
        <Form.Item name="inputMode" label="现金流模式" rules={[{ required: true }]}><Select options={[{ value: 'COMPONENTS', label: '逐年分项计算标准 FCFF' }, { value: 'DIRECT', label: '逐年直接输入标准 FCFF' }]} /></Form.Item>
        <Form.Item name="valuationScope" label="估值范围" rules={[{ required: true }]}><Select options={[{ value: 'LIQUOR_OPERATIONS', label: '酒类经营业务' }, { value: 'WHOLE_GROUP', label: '整个集团' }]} /></Form.Item>
        <Form.Item name="financialCompanyTreatment" label="财务公司及金融资产负债处理（如无也须说明）" rules={[{ required: true, whitespace: true }]}><Input.TextArea /></Form.Item>
        <Form.Item name="forecastSource" label="现金流口径、预测路径与折现参数来源" rules={[{ required: true, whitespace: true }]}><Input.TextArea /></Form.Item>
        <Form.Item name="years" label="预测期（1—30年）" rules={[{ required: true }]}><InputNumber min={1} max={30} precision={0} /></Form.Item>
        {numberInput('wacc', 'WACC（小数，须大于永续增长率）', true, 0.000001)}
        {numberInput('terminalGrowth', '永续增长率（小数）', true, -0.999999)}
        {mode === 'COMPONENTS' && components.map(([id, label]) => <Form.Item key={id} name={['inputDefinitions', id]} label={`${label}定义与调整依据`} rules={[{ required: true, whitespace: true }]}><Input.TextArea placeholder={definitions[id]} rows={2} /></Form.Item>)}
        {path(['forecast'], '基准')}
        <Form.Item name="withScenarios" valuePropName="checked"><Checkbox>明确输入悲观/乐观逐年预测（不自动生成）</Checkbox></Form.Item>
        {withScenarios && ['悲观', '乐观'].map(name => <div key={name}>{path(['scenarios', name], name)}</div>)}
        <Form.Item name="bridgePolicyConfirmed" valuePropName="checked"><Checkbox>本次同时确认完整股权桥接</Checkbox></Form.Item>
        {bridges.map(([id, label]) => <div key={id}>
          {numberInput(id, label, bridgeConfirmed, id === 'dilutedShares' ? 0.000001 : 0)}
          <Form.Item name={['bridgeSources', id]} label={`${label}范围、来源；零调整须说明理由`} rules={bridgeConfirmed ? [{ required: true, whitespace: true }] : []}><Input.TextArea rows={2} /></Form.Item>
        </div>)}
        <Form.Item name="bridgePolicy" label="桥接政策与经营范围一致性" rules={bridgeConfirmed ? [{ required: true, whitespace: true }] : []}><Input.TextArea /></Form.Item>
        {numberInput('peMultiple', '可选：用户假设 PE 倍数', false, 0)}
        {numberInput('pbMultiple', '可选：用户假设 PB 倍数', false, 0)}
        <Form.Item name="multipleSource" label="倍数来源（假设价格，不作行业平均或评级）"><Input /></Form.Item>
        <Form.Item name="rationale" label="本次确认总体依据" rules={[{ required: true, whitespace: true }]}><Input.TextArea /></Form.Item>
        <Form.Item name="confirmedBy" label="确认人" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        <Form.Item name="assumptionsConfirmed" valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error('需要明确确认')) }]}><Checkbox>确认估值范围、逐年标准 FCFF、来源、WACC、永续增长率及预测期</Checkbox></Form.Item>
      </Form>
    </Modal>
  </>
}
