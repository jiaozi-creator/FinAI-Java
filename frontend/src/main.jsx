import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ConfigProvider
        locale={zhCN}
        theme={{
          token: {
            colorPrimary: '#0c6b63',
            colorInfo: '#0c6b63',
            colorText: '#1c1915',
            colorTextSecondary: '#6f685d',
            colorBorder: '#e5dfd3',
            colorBgContainer: '#fffcf8',
            borderRadius: 8,
            fontFamily: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
          },
          components: {
            Menu: {
              itemSelectedBg: '#e6f3f1',
              itemSelectedColor: '#0c6b63',
              itemHoverBg: '#f6f3ec',
            },
            Table: {
              headerBg: '#f7f4ee',
              headerColor: '#6f685d',
              rowHoverBg: '#f7f4ee',
            },
            Button: { primaryShadow: 'none' },
          },
        }}
      >
        <App />
      </ConfigProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
