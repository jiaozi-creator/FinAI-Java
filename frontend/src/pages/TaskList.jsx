import { useState, useEffect } from 'react'
import { Table, Tag, Button, Space, Input, Select, message, Modal } from 'antd'
import { useNavigate } from 'react-router-dom'
import { SearchOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons'
import { taskAPI } from '../services/api'
import PageHeader from '../components/PageHeader'

const { Search } = Input
const { Option } = Select

function TaskList() {
  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const [filters, setFilters] = useState({
    companyCode: null,
    status: null,
  })
  const navigate = useNavigate()

  useEffect(() => {
    fetchTasks()
  }, [pagination.current, filters])

  const fetchTasks = async () => {
    try {
      setLoading(true)
      const params = {
        page: pagination.current,
        size: pagination.pageSize,
        ...filters,
      }
      const data = await taskAPI.listTasks(params)
      setTasks(data)
      setPagination(prev => ({
        ...prev,
        total: data.length,
      }))
    } catch (error) {
      message.error('加载任务列表失败')
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = (taskId) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个任务吗？此操作不可恢复。',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          await taskAPI.deleteTask(taskId)
          message.success('删除成功')
          fetchTasks()
        } catch (error) {
          message.error('删除失败')
        }
      },
    })
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
    const config = statusConfig[status] || { color: 'default', text: status }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  const columns = [
    {
      title: '任务ID',
      dataIndex: 'taskId',
      key: 'taskId',
      render: (text) => (
        <a onClick={() => navigate(`/tasks/${text}`)}>
          {text.substring(0, 12)}...
        </a>
      ),
    },
    {
      title: '公司代码',
      dataIndex: 'companyCode',
      key: 'companyCode',
    },
    {
      title: '公司名称',
      dataIndex: 'companyName',
      key: 'companyName',
    },
    {
      title: '报告期',
      dataIndex: 'reportPeriod',
      key: 'reportPeriod',
    },
    {
      title: '分析类型',
      dataIndex: 'analysisType',
      key: 'analysisType',
      render: (type) => {
        const typeMap = {
          QUICK: '快速分析',
          STANDARD: '标准分析',
          FULL: '完整分析',
          VALUATION_ONLY: '仅估值',
        }
        return typeMap[type] || type
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => getStatusTag(status),
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      render: (progress) => `${progress}%`,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/tasks/${record.taskId}`)}
          >
            详情
          </Button>
          {record.status === 'COMPLETED' && (
            <Button
              type="link"
              size="small"
              onClick={() => navigate(`/tasks/${record.taskId}/report`)}
            >
              报告
            </Button>
          )}
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.taskId)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        kicker="列表"
        title="任务"
        description="一份财报一次计算。完成后进入详情看指标、异常和估值。"
      />
      <div className="toolbar">
        <Space>
          <Select
            placeholder="选择状态"
            allowClear
            style={{ width: 150 }}
            onChange={(value) => setFilters({ ...filters, status: value })}
          >
            <Option value="RUNNING">运行中</Option>
            <Option value="COMPLETED">已完成</Option>
            <Option value="FAILED">失败</Option>
          </Select>
          <Search
            placeholder="搜索公司代码"
            allowClear
            style={{ width: 200 }}
            onSearch={(value) => setFilters({ ...filters, companyCode: value || null })}
          />
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchTasks}>
            刷新
          </Button>
          <Button type="primary" onClick={() => navigate('/tasks/create')}>
            创建任务
          </Button>
        </Space>
      </div>

      <div className="sheet">
        <Table
          columns={columns}
          dataSource={tasks}
          rowKey="taskId"
          loading={loading}
          pagination={pagination}
          onChange={(newPagination) => setPagination(newPagination)}
        />
      </div>
    </div>
  )
}

export default TaskList
