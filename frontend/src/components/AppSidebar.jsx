import { Menu } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  DashboardOutlined,
  UnorderedListOutlined,
  PlusCircleOutlined,
} from '@ant-design/icons'

function selectedKey(pathname) {
  if (pathname.startsWith('/tasks/create')) return '/tasks/create'
  if (pathname.startsWith('/tasks')) return '/tasks'
  return '/dashboard'
}

function AppSidebar() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <aside className="sider">
      <div className="brand">
        <div className="brand-mark">
          <div className="brand-seal">研</div>
          <div>
            <h1>FinAI</h1>
            <p>金融投研工作台</p>
          </div>
        </div>
      </div>
      <Menu
        mode="inline"
        selectedKeys={[selectedKey(location.pathname)]}
        items={[
          { key: '/dashboard', icon: <DashboardOutlined />, label: '工作台' },
          { key: '/tasks', icon: <UnorderedListOutlined />, label: '任务' },
          { key: '/tasks/create', icon: <PlusCircleOutlined />, label: '新建任务' },
        ]}
        onClick={({ key }) => navigate(key)}
      />
      <div className="sider-foot">选题 2 财务分析 · 选题 4 估值</div>
    </aside>
  )
}

export default AppSidebar
