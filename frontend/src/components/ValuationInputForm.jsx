import { useEffect, useState } from 'react'
import { Button, Checkbox, Form, Input, InputNumber, Modal, Alert, message } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import { taskAPI } from '../services/api'

const numbers = [
  ['confirmedFcff', '标准 FCFF 基数（元）', 0.01], ['confirmedGrowth', 'FCFF 预测增速（小数）', -0.3, 0.3],
  ['wacc', 'WACC（小数）', 0.0001], ['terminalGrowth', '永续增长率（小数）', -0.99],
  ['years', '显式预测期（年）', 1, 30], ['usableCash', '可用现金（元）', 0],
  ['grossDebt', '有息债务（元）', 0], ['minorityAdjustment', '少数股东调整（元）', 0],
  ['nonCoreAssets', '非核心资产调整（元）', 0], ['dilutedShares', '摊薄股数（股）', 1],
]

export default function ValuationInputForm({ taskId, disabled }) {
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [record, setRecord] = useState(null)
  const [form] = Form.useForm()
  useEffect(() => { taskAPI.getValuationInputs(taskId).then(setRecord).catch(() => setRecord(null)) }, [taskId])
  const show = () => {
    form.resetFields()
    if (record?.parametersJson) form.setFieldsValue({ ...JSON.parse(record.parametersJson), confirmedBy: record.confirmedBy, rationale: record.rationale, assumptionsConfirmed: false, bridgePolicyConfirmed: false })
    setOpen(true)
  }
  const confirm = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const { confirmedBy, rationale, ...parameters } = values
      const saved = await taskAPI.confirmValuationInputs(taskId, { parameters, confirmedBy, rationale })
      setRecord(saved)
      setOpen(false)
      message.success('确认版本已保存，正在创建新的运行')
      await taskAPI.rerun(taskId)
      window.location.reload()
    } catch (error) { message.error(error.response?.data?.message || '确认或重算失败') }
    finally { setSaving(false) }
  }
  return <>
    <Button icon={<CheckOutlined />} disabled={disabled} onClick={show}>确认估值参数</Button>
    {record && <p>确认版本 {record.id} · {record.confirmedBy} · {record.confirmedAt}</p>}
    <Modal title="估值输入确认" open={open} onCancel={() => setOpen(false)} onOk={confirm} confirmLoading={saving} okText="确认并重算" width={640}>
      <Alert type="warning" message="所有金额为元，比率为小数；桥接政策及预测输入需财务复核。" style={{ marginBottom: 16 }} />
      <Form form={form} layout="vertical">
        {numbers.map(([name, label, min, max]) => <Form.Item key={name} name={name} label={label} rules={[{ required: true }]}>
          <InputNumber stringMode={name !== 'years'} min={min} max={max} precision={name === 'years' ? 0 : undefined} style={{ width: '100%' }} />
        </Form.Item>)}
        <Form.Item name="bridgePolicy" label="桥接政策（现金范围、财务公司资金、债务、少数股东、非核心资产及摊薄依据）" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={4} /></Form.Item>
        <Form.Item name="rationale" label="预测、折现率和其他输入来源及依据" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={3} /></Form.Item>
        <Form.Item name="confirmedBy" label="确认人" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        {['assumptionsConfirmed', 'bridgePolicyConfirmed'].map((name, i) => <Form.Item key={name} name={name} valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error('需要明确确认')) }]}>
          <Checkbox>{i === 0 ? '确认标准 FCFF、增速、WACC、永续增长率及预测期' : '确认桥接政策、现金债务、调整金额及摊薄股数'}</Checkbox>
        </Form.Item>)}
      </Form>
    </Modal>
  </>
}
