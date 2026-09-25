import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 可以在这里添加token等认证信息
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    console.error('API Error:', message)
    return Promise.reject(error)
  }
)

// 任务相关API
export const taskAPI = {
  // 创建任务
  createTask: (formData) => {
    return api.post('/tasks', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },

  openDemo: () => api.post('/tasks/demo'),

  // 获取任务详情
  getTask: (taskId) => {
    return api.get(`/tasks/${taskId}`)
  },

  // 获取任务列表
  listTasks: (params) => {
    return api.get('/tasks', { params })
  },

  // 获取报告
  getReport: (taskId) => {
    return api.get(`/tasks/${taskId}/report`)
  },

  // 获取财务指标
  getMetrics: (taskId) => {
    return api.get(`/tasks/${taskId}/metrics`)
  },

  // 获取异常信号
  getAnomalies: (taskId) => {
    return api.get(`/tasks/${taskId}/anomalies`)
  },

  // 获取估值结果
  getValuation: (taskId) => {
    return api.get(`/tasks/${taskId}/valuation`)
  },

  // 获取证据
  getEvidence: (taskId, fieldId) => {
    return api.get(`/tasks/${taskId}/evidence`, {
      params: { fieldId },
    })
  },

  // 取消任务
  cancelTask: (taskId) => {
    return api.post(`/tasks/${taskId}/cancel`)
  },

  // 删除任务
  deleteTask: (taskId) => {
    return api.delete(`/tasks/${taskId}`)
  },
}

// 审计日志API
export const auditAPI = {
  // 获取任务日志
  getTaskLogs: (taskId) => {
    return api.get(`/audit/tasks/${taskId}/logs`)
  },

  // 导出日志
  exportLogs: (taskId) => {
    return api.get(`/audit/tasks/${taskId}/export`, {
      responseType: 'blob',
    })
  },
}

export default api
