import { useLocation } from 'react-router-dom'
import { ApiOutlined } from '@ant-design/icons'

const TITLES = {
  '/dashboard': '工作台',
  '/tasks': '任务',
  '/tasks/create': '新建任务',
}

function titleFor(pathname) {
  if (TITLES[pathname]) return TITLES[pathname]
  if (pathname.endsWith('/report')) return '分析报告'
  if (pathname.endsWith('/chat')) return '追问'
  if (pathname.startsWith('/tasks/')) return '任务详情'
  return 'FinAI'
}

function AppHeader() {
  const { pathname } = useLocation()

  return (
    <header className="topbar">
      <div className="topbar-kicker">{titleFor(pathname)}</div>
      <a href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noopener noreferrer">
        <ApiOutlined /> 接口说明
      </a>
    </header>
  )
}

export default AppHeader
