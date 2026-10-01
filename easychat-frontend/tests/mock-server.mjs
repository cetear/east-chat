// Isolated browser fixture. All API writes remain in memory; never proxies to the real backend.
import { createServer } from 'vite'
const sessions = []
const histories = new Map()
let next = 0
const stamp = '2026-09-28T10:00:00'
function session() {
  const item = { id: ++next, sessionCode: `fixture-${next}`, title: 'New Chat', maxRounds: 10, systemPrompt: null, status: 1, createdAt: stamp, updatedAt: stamp }
  sessions.push(item); histories.set(item.sessionCode, [])
  return item
}
const server = await createServer({
  server: { host: '127.0.0.1', port: 5173, strictPort: true },
  define: {
    'import.meta.env.VITE_AUTH_ENABLED': JSON.stringify('true'),
    'import.meta.env.VITE_KNOWLEDGE_ENABLED': JSON.stringify('true'),
    'import.meta.env.VITE_MEDIA_ENABLED': JSON.stringify('true'),
  },
  plugins: [{
    name: 'isolated-contract-fixture',
    transformIndexHtml(html) { return html.replace('<head>', '<head><script>window.easychatIdentity={token:"fixture-only",roles:["admin"]}</script>') },
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const path = req.url.split('?')[0]
        if (!/^\/(api|model|es)(\/|$)/.test(path)) return next()
        const json = (data, status = 200) => { res.statusCode = status; res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(data)) }
        const result = data => json({ code: 200, message: 'success', data })
        const chunks = []
        for await (const chunk of req) chunks.push(chunk)
        let body = {}
        try { body = JSON.parse(Buffer.concat(chunks).toString() || '{}') } catch {}
        if (path === '/api/models') return result([{ modelCode: 'fixture-model-code', modelName: '测试视觉模型', supportVision: 1 }])
        if (path === '/api/sessions') return json([...sessions].reverse())
        if (path === '/api/session' && req.method === 'POST') return json(session())
        if (path.startsWith('/api/session/')) {
          const code = decodeURIComponent(path.split('/')[3])
          const item = sessions.find(s => s.sessionCode === code)
          if (!item) return json({ error: 'not found' }, 404)
          if (path.endsWith('/messages')) return json(histories.get(code))
          if (req.method === 'PUT') { Object.assign(item, body); return json(item) }
          if (req.method === 'DELETE') { sessions.splice(sessions.indexOf(item), 1); histories.delete(code); res.statusCode = 204; return res.end() }
          return json(item)
        }
        if (path === '/api/chat' || path === '/api/chat/stream') {
          if (body.model !== 'fixture-model-code') return json({ error: 'expected modelCode, not label' }, 400)
          if (body.messages.length !== 1 || body.messages[0].role !== 'user') return json({ error: 'expected new user only' }, 400)
          const history = histories.get(body.sessionId)
          const prompt = body.messages[0].content
          history.push({ id: history.length + 1, role: 'user', content: prompt, status: 1, paramJson: JSON.stringify({ images: body.messages[0].images }) })
          const answer = { id: history.length + 1, role: 'assistant', content: '', status: 2 }
          history.push(answer)
          const sources = [{ chunkId: 'c', docId: 'd', title: '测试资料.txt', pageNo: 2 }]
          if (path === '/api/chat') {
            Object.assign(answer, { content: '同步回答', status: 1, finishReason: 'stop' })
            return json({ content: answer.content, sessionId: body.sessionId, sources: JSON.stringify(sources), usage: { complete: false, totalTokens: null, attempts: 1, reportedCalls: 0 } })
          }
          res.setHeader('Content-Type', 'text/event-stream')
          const event = (name, data) => res.write(`event: ${name}\ndata: ${typeof data === 'string' ? data : JSON.stringify(data)}\n\n`)
          event('meta', { sessionId: body.sessionId })
          if (body.toolsEnabled) { event('action', { tool: 'hello', input: {} }); event('observation', { content: '{"success":true,"result":"hello"}' }) }
          event('sources', sources)
          event('warning', '测试：检索降级提示')
          answer.content = '你好，**流式回答**。'
          event('message', answer.content)
          if (prompt.includes('断流')) { answer.status = 0; answer.errorMsg = '连接中断'; return res.end() }
          if (prompt.includes('错误')) { answer.status = 0; answer.errorMsg = '模型失败'; event('error', answer.errorMsg); return res.end() }
          const timer = setTimeout(() => {
            answer.status = 1; answer.finishReason = 'stop'
            event('meta', { providerCode: 'fixture-provider', usage: { complete: false, totalTokens: null, attempts: 1, reportedCalls: 0 } })
            event('finish', 'stop'); res.end()
          }, prompt.includes('停止') ? 20000 : 300)
          res.on('close', () => { clearTimeout(timer); if (answer.status === 2) answer.status = 3 })
          return
        }
        if (path === '/api/knowledge/docs') return result([{ docCode: 'd', fileName: '测试资料.txt', dataset: 'default', status: 'INDEXED', chunkCount: 3, errorMsg: null, createdAt: stamp }])
        if (path === '/model/list') return result([{ modelCode: 'fixture-model-code', modelName: '测试视觉模型', supportVision: 1, enabled: 1 }])
        if (path === '/model/provider/list') return result([{ providerCode: 'fixture-provider', baseUrl: 'http://localhost', enabled: 1, circuitStatus: 'CLOSED', failCount: 0 }])
        if (path === '/api/ops/status') return json({ database: 'up', routes: {}, esEnabled: true })
        return json({ error: 'Fixture does not implement this operation' }, 404)
      })
    },
  }],
})
await server.listen()
console.log('Isolated browser fixture ready at http://localhost:5173')
