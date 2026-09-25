import { Routes, Route, Navigate } from 'react-router-dom'
import AppHeader from './components/AppHeader'
import AppSidebar from './components/AppSidebar'
import Dashboard from './pages/Dashboard'
import TaskList from './pages/TaskList'
import TaskDetail from './pages/TaskDetail'
import CreateTask from './pages/CreateTask'
import ReportView from './pages/ReportView'
import ChatAnalysis from './pages/ChatAnalysis'
import RiskAssessment from './pages/RiskAssessment'
import './App.css'

function App() {
  return (
    <div className="shell">
      <AppSidebar />
      <div className="workspace">
        <AppHeader />
        <main className="workspace-body">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/tasks" element={<TaskList />} />
            <Route path="/tasks/create" element={<CreateTask />} />
            <Route path="/tasks/:taskId" element={<TaskDetail />} />
            <Route path="/tasks/:taskId/report" element={<ReportView />} />
            <Route path="/tasks/:taskId/chat" element={<ChatAnalysis />} />
            <Route path="/tasks/:taskId/risks" element={<RiskAssessment />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}

export default App
