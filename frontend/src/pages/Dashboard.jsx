import { useState, useEffect } from 'react'
import { Card, Table, Tag, Spin, message, Button } from 'antd'
import { useNavigate } from 'react-router-dom'
import { taskAPI } from '../services/api'
import PageHeader from '../components/PageHeader'

function Dashboard() {
  const [loading, setLoading] = useState(true)
  const [tasks, setTasks] = useState([])
  const [stats, setStats] = useState({
    total: 0,
    completed: 0,
    running: 0,
    failed: 0,
  })
  const [openingDemo, setOpeningDemo] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    fetchDashboardData()
  }, [])

  const fetchDashboardData = async () => {
    try {
      setLoading(true)
      const data = await taskAPI.listTasks({ page: 1, size: 10 })
      setTasks(data)

      // 计算统计数据
      const statsData = {
        total: data.length,
        completed: data.filter(t => t.status === 'COMPLETED').length,
        running: data.filter(t => t.status === 'RUNNING').length,
        failed: data.filter(t => t.status === 'FAILED').length,
      }
      setStats(statsData)
    } catch (error) {
      message.error('加载数据失败')
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  const openDemo = async () => {
    try {
      setOpeningDemo(true)
      const task = await taskAPI.openDemo()
      navigate(`/tasks/${task.taskId}`)
    } catch (error) {
      message.error('演示样例没有打开，先确认后端已重启')
    } finally {
      setOpeningDemo(false)
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
    const config = statusConfig[status] || { color: 'default', text: status }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  const columns = [
    {
      title: '任务ID',
      dataIndex: 'taskId',
      key: 'taskId',
      render: (text) => text.substring(0, 12) + '...',
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
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => getStatusTag(status),
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
        <a onClick={() => navigate(`/tasks/${record.taskId}`)}>查看详情</a>
      ),
    },
  ]

  if (loading) {
    return (
      <div>
        <PageHeader kicker="概览" title="工作台" description="正在读取最近的任务。" />
        <div className="center-state"><Spin /></div>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        kicker="概览"
        title="工作台"
        description="最近十个任务的进度。总数不是全库统计，只反映这一页。"
        extra={<Button type="primary" loading={openingDemo} onClick={openDemo}>打开演示样例</Button>}
      />

      <div className="stat-grid">
        <div className="stat"><span>这一页</span><strong>{stats.total}</strong></div>
        <div className="stat"><span>已完成</span><strong><em>{stats.completed}</em></strong></div>
        <div className="stat"><span>运行中</span><strong>{stats.running}</strong></div>
        <div className="stat"><span>失败</span><strong>{stats.failed}</strong></div>
      </div>

      <Card title="最近任务" styles={{ body: { padding: 8 } }}>
        <Table
          columns={columns}
          dataSource={tasks}
          rowKey="taskId"
          pagination={false}
        />
      </Card>
    </div>
  )
}

export default Dashboard
