export type ChatRole = 'user' | 'assistant' | 'system'

export interface ChatMessage {
  role: ChatRole
  content: string
}

export type StreamEventType = 'thought' | 'action' | 'observation'

export interface StreamEventMessage {
  id: string
  role: 'assistant'
  type: StreamEventType
  content: string
  toolName?: string
  toolInput?: Record<string, unknown>
  toolOutput?: string
  timestamp: number
}

export interface SessionView {
  id: number
  sessionCode: string
  title: string
  modelCode: string
  systemPrompt: string | null
  maxRounds: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface SessionDetail extends SessionView {
  messages?: ChatMessage[]
}

export interface ChatSession extends SessionView {
  messages: ChatMessage[]
}

export interface ChatRequest {
  sessionId?: string | null
  model?: string
  messages: ChatMessage[]
  toolsEnabled?: boolean
  ragEnabled?: boolean
}

export interface ChatResponse {
  content: string
  sessionId: string
}

export interface AgentEvent {
  type: 'thought' | 'action' | 'observation' | 'message' | 'error' | 'finish'
  content?: string
  tool?: string
  input?: Record<string, unknown>
}
