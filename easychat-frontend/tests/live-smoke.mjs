// Real-backend smoke checks. Creates one isolated session and removes only that session.
import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
const base = process.env.EASYCHAT_TEST_BASE_URL || 'http://127.0.0.1:5173'
const results = []
async function call(path, method = 'GET', body) {
  const res = await fetch(base + path, { method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }), signal: AbortSignal.timeout(15000) })
  const text = await res.text()
  let data; try { data = JSON.parse(text) } catch { data = text }
  return { status: res.status, data, contentType: res.headers.get('content-type') }
}
async function check(name, fn) {
  try { const detail = await fn(); results.push({ name, passed: true, detail }); console.log('PASS ' + name, JSON.stringify(detail ?? null)) }
  catch (error) { results.push({ name, passed: false, error: error.message }); console.log('FAIL ' + name + ': ' + error.message) }
}
let code
try {
  await check('Same-origin model catalog', async () => {
    const r = await call('/api/models'); assert.equal(r.status, 200); assert.equal(r.data.code, 200); assert.ok(Array.isArray(r.data.data))
    return { models: r.data.data.length }
  })
  await check('Session creation without body', async () => {
    const r = await call('/api/session', 'POST'); assert.equal(r.status, 200); assert.ok(typeof r.data.sessionCode === 'string')
    code = r.data.sessionCode
    return { sessionCode: code, status: r.data.status, maxRounds: r.data.maxRounds }
  })
  if (!code) throw new Error('Session creation failed; dependent checks skipped')
  const path = '/api/session/' + encodeURIComponent(code)
  await check('Session partial update and GET', async () => {
    const r = await call(path, 'PUT', { title: 'Codex 联调临时会话', systemPrompt: '测试提示', maxRounds: 3 })
    assert.equal(r.status, 200); assert.equal(r.data.title, 'Codex 联调临时会话'); assert.equal(r.data.maxRounds, 3)
    const next = await call(path); assert.equal(next.data.systemPrompt, '测试提示'); assert.equal(next.data.sessionCode, code)
  })
  await check('Session included in user listing', async () => {
    const r = await call('/api/sessions'); assert.equal(r.status, 200); assert.ok(r.data.some(s => s.sessionCode === code))
    return { sessionCount: r.data.length }
  })
  await check('Dedicated history returns array', async () => {
    const r = await call(path + '/messages'); assert.equal(r.status, 200); assert.deepEqual(r.data, [])
  })
  await check('maxRounds accepts 100, rejects 101', async () => {
    const valid = await call(path + '/max-rounds', 'PUT', { maxRounds: 100 }); assert.equal(valid.status, 200); assert.equal(valid.data.maxRounds, 100)
    const invalid = await call(path + '/max-rounds', 'PUT', { maxRounds: 101 }); assert.equal(invalid.status, 400)
    return invalid.data
  })
  await check('Clear prompt/title with empty strings', async () => {
    const r = await call(path, 'PUT', { title: '', systemPrompt: '' }); assert.equal(r.status, 200); assert.equal(r.data.title, ''); assert.equal(r.data.systemPrompt, '')
  })
  await check('Closed session prevents chat', async () => {
    const r = await call(path, 'PUT', { status: 0 }); assert.equal(r.status, 200); assert.equal(r.data.status, 0)
    const c = await call('/api/chat', 'POST', { sessionId: code, model: 'codex-nonexistent-model', messages: [{ role: 'user', content: '关闭会话联调' }] })
    assert.equal(c.status, 409); return c.data
  })
  await check('Unknown model returns explicit chat failure', async () => {
    await call(path, 'PUT', { status: 1 })
    const r = await call('/api/chat', 'POST', { sessionId: code, model: 'codex-nonexistent-model', messages: [{ role: 'user', content: '无效模型联调' }] })
    assert.equal(r.status, 400); return r.data
  })
  await check('Stream request handles pre-stream validation failure', async () => {
    const r = await call('/api/chat/stream', 'POST', { sessionId: code, model: 'codex-nonexistent-model', messages: [{ role: 'user', content: '流式无效模型联调' }] })
    if (r.status === 200) { assert.ok(r.contentType.includes('text/event-stream')); assert.match(r.data, /event:error|event: error/); return { terminalError: true, body: r.data } }
    assert.equal(r.status, 400); return r.data
  })
  await check('Admin model/provider/ops read through proxy', async () => {
    const model = await call('/model/list'); assert.equal(model.status, 200); assert.equal(model.data.code, 200)
    const provider = await call('/model/provider/list'); assert.equal(provider.status, 200); assert.equal(provider.data.code, 200)
    const ops = await call('/api/ops/status'); assert.equal(ops.status, 200)
    return { modelCount: model.data.data.length, providerCount: provider.data.data.length, database: ops.data.database, esEnabled: ops.data.esEnabled }
  })
  await check('Knowledge list all/default through proxy', async () => {
    for (const query of ['', '?dataset=default']) {
      const r = await call('/api/knowledge/docs' + query); assert.equal(r.status, 200); assert.equal(r.data.code, 200); assert.ok(Array.isArray(r.data.data))
    }
  })
  await check('ES connection health', async () => {
    const r = await call('/es/test'); assert.equal(r.status, 200, JSON.stringify(r)); return r.data
  })
} finally {
  if (code) {
    await check('Delete only created test session returns 204', async () => { const r = await call('/api/session/' + code, 'DELETE'); assert.equal(r.status, 204) })
    await check('Deleted test session returns 404', async () => { const r = await call('/api/session/' + code); assert.equal(r.status, 404) })
  }
  await writeFile('tests/live-results.json', JSON.stringify({ checkedAt: new Date().toISOString(), base, results }, null, 2) + '\n')
}
process.exitCode = results.some(r => !r.passed) ? 1 : 0

