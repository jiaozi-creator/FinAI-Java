import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { Button } from 'antd'
import api from '../services/api'

function ChatAnalysis() {
  const { taskId } = useParams()
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [suggestions, setSuggestions] = useState([])
  const messagesEndRef = useRef(null)

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        const [history, suggested] = await Promise.all([
          api.get(`/chat/${taskId}/history`),
          api.get(`/chat/${taskId}/suggestions`),
        ])
        if (!active) return
        setMessages(history || [])
        setSuggestions(suggested || [])
      } catch (error) {
        console.error(error)
      }
    }
    load()
    return () => {
      active = false
    }
  }, [taskId])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  const handleAsk = async () => {
    const text = question.trim()
    if (!text || loading) return
    setMessages((prev) => [...prev, { role: 'user', content: text }])
    setQuestion('')
    setLoading(true)
    try {
      const data = await api.post(`/chat/${taskId}/ask`, { question: text })
      setMessages((prev) => [...prev, {
        role: 'assistant',
        content: data.answer,
        evidence: data.evidences,
        confidence: data.confidence,
      }])
    } catch (error) {
      setMessages((prev) => [...prev, {
        role: 'assistant',
        content: '这次没有拿到回复。看一下后端和模型配置再试。',
        error: true,
      }])
    } finally {
      setLoading(false)
    }
  }

  const handleClearHistory = async () => {
    if (!window.confirm('清空这次任务的对话记录？')) return
    try {
      await api.delete(`/chat/${taskId}/history`)
      setMessages([])
    } catch (error) {
      console.error(error)
    }
  }

  return (
    <div className="chat-shell">
      <section className="chat-main">
        <div className="chat-bar">
          <div>
            <h2>追问</h2>
            <p>数字以报告里的计算结果为准，对话只做解释。</p>
          </div>
          <button className="text-btn" type="button" onClick={handleClearHistory}>清空</button>
        </div>
        <div className="chat-stream">
          {messages.length === 0 && (
            <div className="chat-empty">
              <h3>从右边的问题开始</h3>
              <p>可以问利润质量、现金流和估值假设，不要让模型另报一套数字。</p>
            </div>
          )}
          {messages.map((msg, index) => (
            <div key={index} className={`bubble ${msg.error ? 'error' : msg.role}`}>
              {msg.content}
              {msg.evidence?.length > 0 && (
                <div className="evidence-item">
                  {msg.evidence.map((item, i) => <div key={i}>第 {item.pageNumber ?? '-'} 页 · {item.excerpt}</div>)}
                </div>
              )}
            </div>
          ))}
          {loading && <div className="bubble assistant">正在组织回答…</div>}
          <div ref={messagesEndRef} />
        </div>
        <form
          className="chat-input"
          onSubmit={(event) => {
            event.preventDefault()
            handleAsk()
          }}
        >
          <input
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="问扣非、现金流或估值假设"
            disabled={loading}
          />
          <Button type="primary" htmlType="submit" loading={loading} disabled={!question.trim()}>
            发送
          </Button>
        </form>
      </section>
      <aside className="chat-side">
        <h3>可以问</h3>
        {suggestions.length === 0 && <p>暂无建议问题</p>}
        {suggestions.map((suggestion) => (
          <button key={suggestion} className="suggest" type="button" onClick={() => setQuestion(suggestion)}>
            {suggestion}
          </button>
        ))}
      </aside>
    </div>
  )
}

export default ChatAnalysis
