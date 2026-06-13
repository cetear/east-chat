import type { ChatRequest, ChatResponse, SessionDetail, SessionView } from '@/types/message'
import { requestJson, requestStream, requestVoid } from './request'

const BASE = '/api'

const jsonHeaders = { 'Content-Type': 'application/json' }

export function streamChat(data: ChatRequest, signal?: AbortSignal): Promise<Response> {
  return requestStream(`${BASE}/chat/stream`, {
    method: 'POST',
    headers: {
      ...jsonHeaders,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(data),
    signal,
  })
}

export function chat(data: ChatRequest): Promise<ChatResponse> {
  return requestJson<ChatResponse>(`${BASE}/chat`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(data),
  })
}

export function createSession(): Promise<SessionView> {
  return requestJson<SessionView>(`${BASE}/session`, {
    method: 'POST',
    headers: jsonHeaders,
  })
}

export function getSessions(): Promise<SessionView[]> {
  return requestJson<SessionView[]>(`${BASE}/sessions`)
}

export function getSession(sessionId: string): Promise<SessionDetail> {
  return requestJson<SessionDetail>(`${BASE}/session/${sessionId}`)
}

export function updateSession(sessionId: string, session: Partial<SessionView>): Promise<SessionView> {
  return requestJson<SessionView>(`${BASE}/session/${sessionId}`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify(session),
  })
}

export function deleteSession(sessionId: string): Promise<void> {
  return requestVoid(`${BASE}/session/${sessionId}`, { method: 'DELETE' })
}

export function setMaxRounds(sessionId: string, maxRounds: number): Promise<SessionView> {
  return requestJson<SessionView>(`${BASE}/session/${sessionId}/max-rounds`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify({ maxRounds }),
  })
}
