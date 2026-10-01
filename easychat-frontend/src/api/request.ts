import { authorizationHeaders, identity, setIdentity } from '../auth'
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL?.trim() || '').replace(/\/+$/, '')
export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) { super(message); this.name = 'ApiError'; this.status = status }
}
export function retryableReadError(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError &&
    (error.status >= 500 || error.status === 408 || error.status === 429))
}
export async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const requestedIdentity = identity.value
  const headers = new Headers(init.headers)
  for (const [key, value] of Object.entries(authorizationHeaders())) headers.set(key, value)
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  if (!response.ok) {
    const text = await response.text()
    let message = text || `HTTP ${response.status}`
    try { const body = JSON.parse(text); message = body.error || body.message || message } catch { /* text fallback */ }
    if (response.status === 401) {
      if (identity.value === requestedIdentity) {
        setIdentity(null)
        window.dispatchEvent(new Event('easychat:unauthorized'))
      }
      message = `登录已失效，请通过现有登录系统重新登录。${message}`
    }
    if (response.status === 403) message = `无权执行此操作。${message}`
    throw new ApiError(message, response.status)
  }
  return response
}
export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  return (await request(path, init)).json() as Promise<T>
}
export async function requestResult<T>(path: string, init?: RequestInit): Promise<T> {
  const result = await requestJson<{ code: number; message: string; data: T }>(path, init)
  if (result.code !== 200) throw new ApiError(result.message || '操作失败', result.code)
  return result.data
}
export async function requestVoid(path: string, init?: RequestInit): Promise<void> { await request(path, init) }
export async function requestStream(path: string, init?: RequestInit): Promise<Response> {
  const response = await request(path, init)
  if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
    await response.body?.cancel()
    throw new Error('响应不是 SSE')
  }
  return response
}
export function jsonBody(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}
