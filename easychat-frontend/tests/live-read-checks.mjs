// Read-only and validation-only probes: no model configuration or successful uploads.
import { writeFile, readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
const base = process.env.EASYCHAT_TEST_BASE_URL || 'http://127.0.0.1:5173'
const previous = JSON.parse(await readFile('tests/live-results.json', 'utf8'))
const code = previous.results.find(item => item.name === 'Session creation without body')?.detail?.sessionCode
const results = []
async function probe(name, path, init = {}, direct = false) {
  const started = Date.now()
  try {
    const response = await fetch((direct ? 'http://localhost:8080' : base) + path, { ...init, signal: AbortSignal.timeout(15000) })
    const text = await response.text()
    let body
    try { body = JSON.parse(text) } catch { body = text }
    const result = { name, method: init.method || 'GET', path, direct, status: response.status, contentType: response.headers.get('content-type'), elapsedMs: Date.now() - started, body }
    results.push(result)
    console.log(JSON.stringify(result))
  } catch (error) {
    results.push({ name, path, direct, error: error.message })
    console.log(name + ': ' + error.message)
  }
}
if (code) {
  await probe('Session detail direct', '/api/session/' + code, {}, true)
  await probe('Session detail proxied', '/api/session/' + code)
  await probe('Session history proxied', '/api/session/' + code + '/messages')
}
await probe('Model code lookup binding', '/model/code/codex-missing-model')
await probe('Provider code lookup binding', '/model/provider/code/codex-missing-provider')
await probe('Route lookup binding', '/model/provider/codex-missing-provider/models')
await probe('ES info', '/es/test')
await probe('ES allowed index read', '/es/getDocuments?index=easychat&size=1')
await probe('ES invalid size validation', '/es/getDocuments?index=easychat&size=101')
await probe('Knowledge invalid dataset', '/api/knowledge/docs?dataset=CON')
await probe('Knowledge missing document status', '/api/knowledge/docs/codex-check-' + randomUUID() + '/status')
for (const [name, path] of [['Knowledge empty file', '/api/knowledge/upload'], ['Media empty file', '/api/media/transcribe']]) {
  const form = new FormData()
  form.append('file', new Blob([], { type: 'text/plain' }), 'codex-empty.txt')
  await probe(name, path, { method: 'POST', body: form })
}
await writeFile('tests/live-diagnostics.json', JSON.stringify({ checkedAt: new Date().toISOString(), base, results }, null, 2) + '\n')

