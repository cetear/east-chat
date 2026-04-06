const BASE = '/api'

const jsonHeaders = { 'Content-Type': 'application/json' }

export function streamChat(data: Record<string, unknown>): Promise<Response> {
  return fetch(`${BASE}/chat/stream`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(data),
  })
}

export function chat(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/chat`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(data),
  }).then(r => r.json())
}

export function createSession(modelType: string): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/session`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ modelType }),
  }).then(r => r.json())
}

export function getSessions(): Promise<Record<string, unknown>[]> {
  return fetch(`${BASE}/sessions`).then(r => r.json())
}

export function getSession(sessionId: string): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/session/${sessionId}`).then(r => r.json())
}

export function updateSession(sessionId: string, session: Record<string, unknown>): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/session/${sessionId}`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify(session),
  }).then(r => r.json())
}

export function deleteSession(sessionId: string): Promise<Response> {
  return fetch(`${BASE}/session/${sessionId}`, { method: 'DELETE' })
}

export function setMaxRounds(sessionId: string, maxRounds: number): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/session/${sessionId}/max-rounds`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify({ maxRounds }),
  }).then(r => r.json())
}
