import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { handleStreamChat } from '../src/utils/sse'
import { requestJson, requestResult, requestStream, ApiError, retryableReadError } from '../src/api/request'
import { setIdentity, identity } from '../src/auth'
import * as chat from '../src/api/chat'
import { getModelList } from '../src/api/model'
import { uploadDocument, getDocuments, deleteDocument, retryDocument } from '../src/api/knowledge'
import { transcribe } from '../src/api/media'
import { useChatStore } from '../src/stores/chatStore'
import { useConfigStore } from '../src/stores/configStore'
import { normalizeDataset, validImage } from '../src/utils/validation'
import { recordPayload } from '../src/utils/adminForm'
import { renderMarkdown } from '../src/utils/markdown'
import { messageNotice, persistedReply } from '../src/utils/chatPresentation'
import { listModels, saveRecord } from '../src/api/admin'

test('Completed history replaces local output even if the final text differs', () => {
  const saved = { role: 'assistant' as const, content: '最终答案\n', status: 1 as const }
  const messages = [{ role: 'assistant' as const, content: '旧回答', status: 1 as const },
    { role: 'user' as const, content: '问题' }, saved]
  assert.equal(persistedReply(messages, 1, '最终答案', true), saved)
  assert.equal(persistedReply(messages.slice(0, 1), 1, '旧回答', true), undefined)
  assert.equal(persistedReply([{ ...saved, status: 2 }], 0, saved.content, true), undefined)
  assert.equal(persistedReply([saved], 0, '未保存的部分', false), undefined)
  assert.equal(persistedReply([saved], 0, saved.content, false), saved)
})

test('Chat notices omit routine success metadata and retain actionable states', () => {
  const saved = { role: 'assistant' as const, content: '回答', status: 1 as const, finishReason: 'stop', totalTokens: 23 }
  assert.equal(messageNotice(saved), '')
  assert.equal(messageNotice({ ...saved, role: 'user' }), '')
  assert.match(messageNotice({ ...saved, status: 0, errorMsg: '服务不可用' }), /服务不可用/)
  assert.match(messageNotice({ ...saved, status: 3 }), /停止/)
  assert.match(messageNotice({ ...saved, status: 2 }), /生成/)
  assert.match(messageNotice({ ...saved, finishReason: 'length' }), /长度限制/)
})
function response(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}
function stream(text: string, size = 1): Response {
  const bytes = new TextEncoder().encode(text)
  let offset = 0
  return new Response(new ReadableStream({
    pull(controller) {
      if (offset >= bytes.length) { controller.close(); return }
      controller.enqueue(bytes.slice(offset, offset + size)); offset += size
    },
  }), { headers: { 'Content-Type': 'text/event-stream' } })
}
const session = { id: 42, sessionCode: 'code-uuid', title: 'New Chat', systemPrompt: null, maxRounds: 10, status: 1, createdAt: '2026-09-27T21:00:00', updatedAt: '2026-09-27T21:00:00' }

test('SSE preserves UTF-8, CRLF boundaries, multiple data lines, spaces and JSON-looking text', async () => {
  let result = ''
  let finish = ''
  const terminal = await handleStreamChat(stream(': comment\r\nevent: message\r\ndata:  你好  \r\ndata: next \r\n\r\nevent: message\r\ndata: {"content":"literal"}\r\n\r\nevent: finish\r\ndata: length\r\n\r\n'), {
    onMessage: text => { result += text }, onFinish: reason => { finish = reason },
  })
  assert.equal(result, ' 你好  \nnext {"content":"literal"}')
  assert.equal(finish, 'length')
  assert.equal(terminal, 'finish')
})
test('SSE dispatches metadata, tools, sources and nonterminal warnings separately', async () => {
  const seen: unknown[] = []
  const terminal = await handleStreamChat(stream('event: meta\ndata: {"sessionId":"code-uuid"}\n\nevent: action\ndata: {"tool":"hello","input":{}}\n\nevent: observation\ndata: {"content":"{\\"success\\":false}"}\n\nevent: sources\ndata: [{"chunkId":"c","docId":"d","title":"资料","pageNo":null}]\n\nevent: warning\ndata: 降级\n\nevent: message\ndata: 最终答案\n\nevent: meta\ndata: {"usage":{"totalTokens":null,"complete":false}}\n\nevent: finish\ndata: stop\n\n', 7), {
    onMeta: data => seen.push(data), onAction: data => seen.push(data), onObservation: data => seen.push(data),
    onSources: data => seen.push(data), onWarning: text => seen.push(text), onMessage: text => seen.push(text),
  })
  assert.equal(terminal, 'finish')
  assert.equal(seen.length, 7)
  assert.deepEqual(seen[0], { sessionId: 'code-uuid' })
  assert.deepEqual(seen[2], { content: '{"success":false}' })
  assert.deepEqual(seen[6], { usage: { totalTokens: null, complete: false } })
})
test('SSE error terminates without waiting for finish and keeps partial answer', async () => {
  let content = '', error = ''
  const terminal = await handleStreamChat(stream('event: message\ndata: partial\n\nevent: error\ndata: failed\n\nevent: message\ndata: ignored\n\n', 1000), {
    onMessage: value => { content += value }, onError: value => { error = value }, onFinish: () => assert.fail('finish is not allowed'),
  })
  assert.equal(terminal, 'error'); assert.equal(content, 'partial'); assert.equal(error, 'failed')
})
test('EOF or terminal meta alone is not successful completion', async () => {
  for (const text of ['', 'event: message\ndata: partial\n\n', 'event: meta\ndata: {"finishReason":"stop"}\n\n', 'event: finish\ndata: stop']) {
    await assert.rejects(handleStreamChat(stream(text), {}), /终态/)
  }
})
test('Malformed event JSON rejects and releases reader', async () => {
  const source = stream('event: meta\ndata: {broken}\n\n')
  await assert.rejects(handleStreamChat(source, {}), SyntaxError)
  assert.equal(source.body!.locked, false)
})
test('Abort during pending read propagates distinctly and unlocks reader', async () => {
  let control: ReadableStreamDefaultController<Uint8Array>
  const source = new Response(new ReadableStream<Uint8Array>({ start(controller) { control = controller } }))
  const pending = handleStreamChat(source, {})
  control!.error(new DOMException('Stopped', 'AbortError'))
  await assert.rejects(pending, { name: 'AbortError' })
  assert.equal(source.body!.locked, false)
})
test('Result requires code 200; direct JSON remains unwrapped', async () => {
  setIdentity({ token: 'test-token', roles: ['user'] })
  globalThis.fetch = async (_url, init) => {
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer test-token')
    return response({ code: 200, data: ['model'] })
  }
  assert.deepEqual(await requestResult('/api/models'), ['model'])
  assert.deepEqual(await requestJson('/api/direct'), { code: 200, data: ['model'] })
  globalThis.fetch = async () => response({ code: 0, message: 'not success', data: [] })
  await assert.rejects(requestResult('/api/models'), /not success/)
})
test('HTTP errors retain non-JSON fallback and status; 401 clears identity without retry', async () => {
  globalThis.fetch = async () => new Response('gateway unavailable', { status: 503 })
  await assert.rejects(requestJson('/api/sessions'), (error: unknown) => error instanceof ApiError && error.status === 503 && error.message === 'gateway unavailable')
  let count = 0
  globalThis.fetch = async () => { count++; return response({ error: 'expired' }, 401) }
  await assert.rejects(requestJson('/api/sessions'), /登录已失效/)
  assert.equal(identity.value, null); assert.equal(count, 1)
  globalThis.fetch = async () => response({ error: 'admin only' }, 403)
  await assert.rejects(requestJson('/model/list'), /无权/)
})
test('SSE rejects successful HTML responses', async () => {
  globalThis.fetch = async () => new Response('<html>login</html>', { headers: { 'Content-Type': 'text/html' } })
  await assert.rejects(requestStream('/api/chat/stream'), /不是 SSE/)
})
test('Catalog uses ordinary-user endpoint and numeric vision flag', async () => {
  globalThis.fetch = async url => { assert.equal(url, '/api/models'); return response({ code: 200, data: [{ modelCode: 'code', modelName: 'Display', supportVision: 1 }] }) }
  assert.deepEqual(await getModelList(), [{ code: 'code', name: 'Display', supportVision: true }])
})
test('Session path uses escaped code, history endpoint, and DELETE accepts 204', async () => {
  const calls: Array<[string, string | undefined]> = []
  globalThis.fetch = async (url, init) => { calls.push([String(url), init?.method]); return init?.method === 'DELETE' ? new Response(null, { status: 204 }) : response([]) }
  await chat.getMessages('a/b')
  await chat.deleteSession('a/b')
  assert.deepEqual(calls, [['/api/session/a%2Fb/messages', undefined], ['/api/session/a%2Fb', 'DELETE']])
})
test('Chat sends only requested user turn and code; creation has no body; sync metadata preserved', async () => {
  const payload = { model: 'code', sessionId: 'code-uuid', messages: [{ role: 'user' as const, content: '', images: ['data:image/png;base64,YQ=='] }], dataset: 'alpha' }
  globalThis.fetch = async (url, init) => {
    if (url === '/api/session') { assert.equal(init?.body, undefined); return response(session) }
    assert.deepEqual(JSON.parse(String(init?.body)), payload)
    return response({ content: 'ok', sessionId: 'code-uuid', sources: '[{"title":"file"}]', usage: { totalTokens: null } })
  }
  await chat.createSession()
  const result = await chat.chat(payload)
  assert.deepEqual(JSON.parse(result.sources!), [{ title: 'file' }])
  assert.equal(result.usage?.totalTokens, null)
})
test('Store loads history independently and replaces optimistic records on refresh', async () => {
  setActivePinia(createPinia())
  const store = useChatStore()
  let history = [{ role: 'user', content: 'saved' }]
  globalThis.fetch = async url => response(String(url).endsWith('/messages') ? history : url === '/api/sessions' ? [session] : session)
  await store.loadSessions()
  assert.equal(store.activeSessionId, 'code-uuid')
  assert.equal(store.currentMessages[0]?.content, 'saved')
  store.addUserMessage('optimistic', 'code-uuid', [])
  history = [{ role: 'user', content: 'saved' }, { role: 'assistant', content: 'persisted' }]
  await store.refreshSession('code-uuid')
  assert.deepEqual(store.currentMessages.map(m => m.content), ['saved', 'persisted'])
  await store.refreshSession('code-uuid')
  assert.equal(store.currentMessages.length, 2)
})
test('Store reset prevents old identity history from populating new user cache', async () => {
  setActivePinia(createPinia())
  const store = useChatStore()
  const resolves: Array<(response: Response) => void> = []
  globalThis.fetch = async () => new Promise(resolve => resolves.push(resolve))
  const pending = store.switchSession('old')
  store.reset()
  resolves[0]!(response({ ...session, sessionCode: 'old' }))
  resolves[1]!(response([{ role: 'user', content: 'private' }]))
  await pending
  assert.deepEqual(store.sessions, []); assert.deepEqual(store.currentMessages, [])
  assert.equal(store.activeSessionId, null)
})
test('Model requests arriving after identity changes are discarded', async () => {
  setActivePinia(createPinia())
  setIdentity({ token: 'old', roles: [] })
  let finish: (response: Response) => void
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const store = useConfigStore()
  const pending = store.loadModelOptions()
  setIdentity({ token: 'new', roles: [] })
  finish!(response({ code: 200, data: [{ modelCode: 'old', modelName: 'old', supportVision: 0 }] }))
  await pending
  assert.deepEqual(store.modelOptions, [])
})
test('Knowledge upload is FormData with bearer and no manual multipart header; docCode is string', async () => {
  setIdentity({ token: 'token', roles: [] })
  globalThis.fetch = async (url, init) => {
    assert.equal(url, '/api/knowledge/upload')
    assert.equal(new Headers(init?.headers).get('Content-Type'), null)
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer token')
    assert.ok(init?.body instanceof FormData)
    assert.equal(init.body.get('dataset'), 'alpha')
    assert.ok(init.body.get('file') instanceof Blob)
    return response({ code: 200, data: 'doc-code' })
  }
  assert.equal(await uploadDocument(new File(['hello'], 'a.txt'), ' alpha '), 'doc-code')
})
test('Knowledge all-dataset listing and retry/delete routes match contract', async () => {
  const paths: string[] = []
  globalThis.fetch = async (url, init) => { paths.push(`${init?.method ?? 'GET'} ${url}`); return response({ code: 200, data: null }) }
  await getDocuments()
  await getDocuments('default')
  await retryDocument('code')
  await deleteDocument('code')
  assert.deepEqual(paths, ['GET /api/knowledge/docs', 'GET /api/knowledge/docs?dataset=default', 'POST /api/knowledge/docs/code/retry', 'DELETE /api/knowledge/docs/code'])
})
test('Media returns direct text, rejects empty text, validates files before upload', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return response({ text: '转写' }) }
  assert.equal(await transcribe(new File(['abc'], 'a.mp4')), '转写')
  await assert.rejects(transcribe(new File([], 'a.mp3')), /文件大小/)
  await assert.rejects(transcribe(new File(['a'], 'a.exe')), /格式/)
  assert.equal(calls, 1)
  globalThis.fetch = async () => response({ text: '' })
  await assert.rejects(transcribe(new File(['a'], 'a.mp3')), /未返回文字/)
})
test('Dataset and image validation matches limits', () => {
  assert.equal(normalizeDataset('  '), 'default')
  assert.equal(normalizeDataset(' team-A_1 '), 'team-A_1')
  for (const value of ['../x', '中文', '_abc', 'CON', 'lpt9', 'a'.repeat(65)]) assert.throws(() => normalizeDataset(value))
  assert.equal(validImage('blob:https://example.com/x'), false)
  assert.equal(validImage('data:image/svg+xml;base64,YQ=='), false)
  assert.equal(validImage('data:image/png;base64,YQ=='), true)
  assert.equal(validImage('https://example.com/image.png'), true)
  assert.equal(validImage('https://a/' + 'x'.repeat(2_000_000)), false)
})
test('Admin forms omit blank secrets, encode numeric values and validate JSON object config', () => {
  assert.deepEqual(recordPayload('provider', { providerCode: 'p', baseUrl: 'https://example.com', apiKey: '', enabled: '1' }), { providerCode: 'p', baseUrl: 'https://example.com', enabled: 1 })
  assert.equal(recordPayload('model', { modelCode: 'm', modelName: 'Model', defaultConfig: '{}' }).defaultConfig, '{}')
  assert.throws(() => recordPayload('model', { modelCode: 'm', modelName: 'M', defaultConfig: '[]' }))
  assert.throws(() => recordPayload('route', { modelCode: 'm', providerCode: 'p', maxRetry: '4' }))
  assert.throws(() => recordPayload('route', { modelCode: 'm', providerCode: 'p', weight: '0' }))
  assert.throws(() => recordPayload('provider', { providerCode: 'p', baseUrl: 'file:///tmp/a' }))
})
test('Admin APIs block ordinary users and use code paths for updates', async () => {
  setIdentity({ token: 'user', roles: ['user'] })
  assert.throws(() => listModels(), /管理员/)
  setIdentity({ token: 'admin', roles: ['admin'] })
  globalThis.fetch = async (url, init) => { assert.equal(url, '/model/provider/p%2F1'); assert.equal(init?.method, 'PUT'); return response({ code: 200, data: null }) }
  await saveRecord('provider', { providerCode: 'p/1', baseUrl: 'https://example.com' }, true)
})
test('Markdown escapes raw HTML and blocks executable links', () => {
  const rendered = renderMarkdown('<img src=x onerror=alert(1)>\n\n[click](javascript:alert%281%29)\n\n**bold**')
  assert.ok(!rendered.includes('<img'))
  assert.ok(!rendered.includes('href="javascript:'))
  assert.ok(rendered.includes('<strong>bold</strong>'))
  assert.ok(renderMarkdown('[safe](https://example.com)').includes('rel="noopener noreferrer"'))
})

test('Late 401 from an old identity does not clear a newly supplied token', async () => {
  setIdentity({ token: 'old', roles: [] })
  let finish: (response: Response) => void
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const pending = requestJson('/api/sessions')
  setIdentity({ token: 'new', roles: [] })
  finish!(response({ error: 'expired old token' }, 401))
  await assert.rejects(pending, /登录已失效/)
  assert.equal(identity.value?.token, 'new')
})
test('Admin results are discarded after an identity switch', async () => {
  setIdentity({ token: 'old-admin', roles: ['admin'] })
  let finish: (response: Response) => void
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const pending = listModels()
  setIdentity({ token: 'new-admin', roles: ['admin'] })
  finish!(response({ code: 200, data: [{ modelCode: 'private' }] }))
  await assert.rejects(pending, /身份已变更/)
})
test('History failure keeps the selected session and disables readiness until recovery', async () => {
  setActivePinia(createPinia())
  const store = useChatStore()
  globalThis.fetch = async url => url === '/api/sessions' ? response([session]) :
    response({ error: 'parameter name not available' }, 400)
  await assert.rejects(store.loadSessions(), /parameter name/)
  assert.equal(store.activeSessionId, session.sessionCode)
  assert.equal(store.activeSession?.sessionCode, session.sessionCode)
  assert.equal(store.historyReady, false)
  assert.match(store.historyError, /parameter name/)
  globalThis.fetch = async url => response(String(url).endsWith('/messages') ? [{ role: 'assistant', content: 'saved', status: 1 }] : session)
  await store.refreshSession(session.sessionCode)
  assert.equal(store.historyReady, true)
  assert.equal(store.historyError, '')
  assert.equal(store.currentMessages[0]?.content, 'saved')
})
test('Failed history refresh preserves cached messages but marks them unconfirmed', async () => {
  setActivePinia(createPinia())
  const store = useChatStore()
  globalThis.fetch = async url => response(String(url).endsWith('/messages') ? [{ role: 'user', content: 'previous' }] : session)
  await store.switchSession(session.sessionCode)
  globalThis.fetch = async () => response({ error: 'backend unavailable' }, 500)
  await assert.rejects(store.refreshSession(session.sessionCode), /backend unavailable/)
  assert.equal(store.currentMessages[0]?.content, 'previous')
  assert.equal(store.historyReady, false)
})
test('Model catalog empty success differs from load failure and can recover', async () => {
  setActivePinia(createPinia())
  const store = useConfigStore()
  globalThis.fetch = async () => response({ code: 200, data: [] })
  await store.loadModelOptions()
  assert.equal(store.modelOptionsLoaded, true)
  assert.equal(store.modelOptionsError, '')
  globalThis.fetch = async () => response({ error: 'backend down' }, 503)
  await assert.rejects(store.loadModelOptions(), /backend down/)
  assert.equal(store.modelOptionsLoaded, false)
  assert.match(store.modelOptionsError, /backend down/)
  assert.equal(store.currentModel, '')
  globalThis.fetch = async () => response({ code: 200, data: [{ modelCode: 'recovered', modelName: 'Recovered', supportVision: 0 }] })
  await store.loadModelOptions()
  assert.equal(store.modelOptionsError, '')
  assert.equal(store.currentModel, 'recovered')
})
test('Knowledge reads back off only for transient failures, not parameter/auth errors', () => {
  for (const status of [400, 401, 403, 404, 409]) assert.equal(retryableReadError(new ApiError('failure', status)), false)
  for (const status of [408, 429, 500, 503]) assert.equal(retryableReadError(new ApiError('failure', status)), true)
  assert.equal(retryableReadError(new TypeError('fetch failed')), true)
  assert.equal(retryableReadError(new Error('invalid dataset')), false)
  assert.equal(retryableReadError(new DOMException('stopped', 'AbortError')), false)
})
