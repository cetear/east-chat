import type { ChatMessage, ChatRequest, ChatResponse, SessionUpdate, SessionView } from '@/types/message'
import { jsonBody, requestJson, requestStream, requestVoid } from './request'
const sessionPath = (code: string) => `/api/session/${encodeURIComponent(code)}`
export function streamChat(data: ChatRequest, signal?: AbortSignal): Promise<Response> {
  return requestStream('/api/chat/stream', { ...jsonBody('POST', data), headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' }, signal })
}
export function chat(data: ChatRequest, signal?: AbortSignal): Promise<ChatResponse> {
  return requestJson('/api/chat', { ...jsonBody('POST', data), signal })
}
export function createSession(): Promise<SessionView> { return requestJson('/api/session', { method: 'POST' }) }
export function getSessions(): Promise<SessionView[]> { return requestJson('/api/sessions') }
export function getSession(code: string): Promise<SessionView> { return requestJson(sessionPath(code)) }
export function getMessages(code: string): Promise<ChatMessage[]> { return requestJson(`${sessionPath(code)}/messages`) }
export function updateSession(code: string, update: SessionUpdate): Promise<SessionView> {
  return requestJson(sessionPath(code), jsonBody('PUT', update))
}
export function deleteSession(code: string): Promise<void> { return requestVoid(sessionPath(code), { method: 'DELETE' }) }
export function setMaxRounds(code: string, maxRounds: number): Promise<SessionView> {
  return requestJson(`${sessionPath(code)}/max-rounds`, jsonBody('PUT', { maxRounds }))
}
