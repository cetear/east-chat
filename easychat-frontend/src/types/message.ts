export type MessageType = 'text' | 'thought' | 'action' | 'observation'

export interface Message {
  id: string
  role: 'user' | 'assistant'
  type: MessageType
  content: string
  toolName?: string
  toolInput?: Record<string, unknown>
  toolOutput?: string
  timestamp: number
}

export interface Session {
  id: number
  sessionId: string
  title: string
  modelType: string
  summary?: string
  maxRounds: number
  messages: Message[]
  createdAt: number
  updatedAt: number
}

export interface AgentEvent {
  type: 'thought' | 'action' | 'observation' | 'message' | 'error' | 'finish'
  content?: string
  tool?: string
  input?: Record<string, unknown>
}

export interface ChatRequest {
  messages: Array<{ role: string; content: string }>
  model: string
  sessionId?: string
  toolsEnabled?: boolean
  ragEnabled?: boolean
}

export interface ModelConfig {
  modelCode: string
  modelName: string
  apiKey: string
  baseUrl: string
  modelNameApi: string
  temperature: number
  maxTokens: number
  isEnabled: boolean
}
