import { useState } from 'react'
import { Form, Input, Select, Button, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { taskAPI } from '../services/api'
import PageHeader from '../components/PageHeader'

const { Option } = Select

function CreateTask() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [fileList, setFileList] = useState([])
  const navigate = useNavigate()

  const onFinish = async (values) => {
    try {
      setLoading(true)

      const formData = new FormData()
      formData.append('companyCode', values.companyCode)
      formData.append('companyName', values.companyName)
      formData.append('reportPeriod', values.reportPeriod)
      formData.append('analysisType', values.analysisType)

      if (fileList.length > 0) {
        const picked = fileList[0]
        const blob = picked.originFileObj || picked
        formData.append('file', blob, picked.name || 'statement.pdf')
      }

      const response = await taskAPI.createTask(formData)
      message.success('任务创建成功')
      navigate(`/tasks/${response.taskId}`)
    } catch (error) {
      message.error('任务创建失败')
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  const uploadProps = {
    onRemove: () => {
      setFileList([])
    },
    beforeUpload: (file) => {
      const isPDF = file.type === 'application/pdf'
      if (!isPDF) {
        message.error('只能上传PDF文件')
        return false
      }
      const isLt50M = file.size / 1024 / 1024 < 50
      if (!isLt50M) {
        message.error('文件大小不能超过50MB')
        return false
      }
      setFileList([{ uid: file.uid || file.name, name: file.name, status: 'done', originFileObj: file }])
      return false
    },
    fileList,
  }

  return (
    <div>
      <PageHeader
        kicker="录入"
        title="新建任务"
        description="上传带文本层的财报 PDF。扫描件抽不到数字，对应科目会显示未提取。"
      />
      <div className="form-sheet">
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{
            analysisType: 'FULL',
          }}
        >
          <Form.Item
            label="公司代码"
            name="companyCode"
            rules={[{ required: true, message: '请输入公司代码' }]}
          >
            <Input placeholder="例如: 600519" />
          </Form.Item>

          <Form.Item
            label="公司名称"
            name="companyName"
            rules={[{ required: true, message: '请输入公司名称' }]}
          >
            <Input placeholder="例如: 贵州茅台" />
          </Form.Item>

          <Form.Item
            label="报告期"
            name="reportPeriod"
            rules={[{ required: true, message: '请输入报告期' }]}
          >
            <Input placeholder="例如: 2023A" />
          </Form.Item>

          <Form.Item
            label="分析类型"
            name="analysisType"
            rules={[{ required: true, message: '请选择分析类型' }]}
          >
            <Select>
              <Option value="QUICK">快速分析</Option>
              <Option value="STANDARD">标准分析</Option>
              <Option value="FULL">完整分析(含估值)</Option>
              <Option value="VALUATION_ONLY">仅估值</Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="上传财报PDF (可选)"
            name="file"
            extra="支持上传年报、半年报、季报等PDF文件，最大50MB"
          >
            <Upload {...uploadProps} maxCount={1}>
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              创建任务
            </Button>
          </Form.Item>
        </Form>
      </div>
    </div>
  )
}

export default CreateTask
