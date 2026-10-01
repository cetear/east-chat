export interface Source { chunkId: string; docId: string; title: string; pageNo: number | null }
export interface Usage {
  complete: boolean; attempts: number; reportedCalls: number
  promptTokens: number | null; completionTokens: number | null; totalTokens: number | null
  modelFinishReason?: string
}
export interface ChatMeta { sessionId?: string; providerCode?: string; finishReason?: string; usage?: Usage }
export interface ChatMessage {
  id?: number; sessionId?: number; role: 'user' | 'assistant'; content: string
  images?: string[]; contentType?: string; messageOrder?: number
  modelCode?: string | null; providerCode?: string | null
  status?: 0 | 1 | 2 | 3; errorMsg?: string | null; finishReason?: string | null
  paramJson?: string | null; latencyMs?: number | null; createdAt?: string
  promptTokens?: number | null; completionTokens?: number | null; totalTokens?: number | null
}
export interface StreamEventMessage {
  id: string; role: 'assistant'; type: 'thought' | 'action' | 'observation'; content: string
  toolName?: string; toolInput?: Record<string, unknown>; toolOutput?: string; timestamp: number
}
export interface SessionView {
  id: number; sessionCode: string; title: string; systemPrompt: string | null
  maxRounds: number; status: number; createdAt: string; updatedAt: string
}
export type SessionUpdate = Partial<Pick<SessionView, 'title' | 'systemPrompt' | 'maxRounds' | 'status'>>
export interface ChatSession extends SessionView { messages: ChatMessage[] }
export interface ChatRequest {
  sessionId?: string | null; model: string
  messages: Array<{ role: 'user'; content: string | null; images?: string[] }>
  toolsEnabled?: boolean; ragEnabled?: boolean; dataset?: string
}
export interface ChatResponse extends ChatMeta { content: string; sessionId: string; sources?: string; warning?: string }
