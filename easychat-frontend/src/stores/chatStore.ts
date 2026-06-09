import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as chatApi from '@/api/chat'
import type { ChatMessage, ChatSession, SessionView, StreamEventMessage } from '@/types/message'

function sortSessions(sessions: SessionView[]): SessionView[] {
  return [...sessions].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  )
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionView[]>([])
  const messagesBySession = ref<Record<string, ChatMessage[]>>({})
  const activeSessionId = ref<string | null>(null)
  const pendingSessionTitle = ref('')
  const draftMessages = ref<ChatMessage[]>([])
  const isStreaming = ref(false)
  const streamContent = ref('')
  const streamingEvents = ref<StreamEventMessage[]>([])
  const streamingSessionId = ref<string | null>(null)

  const activeSession = computed<ChatSession | null>(() => {
    const session = sessions.value.find(item => item.sessionCode === activeSessionId.value)
    if (!session) return null

    return {
      ...session,
      messages: messagesBySession.value[session.sessionCode] ?? [],
    }
  })

  const currentMessages = computed<ChatMessage[]>(() => {
    if (!activeSessionId.value) return draftMessages.value
    return messagesBySession.value[activeSessionId.value] ?? []
  })

  function setSessionMessages(sessionId: string, messages: ChatMessage[]): void {
    messagesBySession.value = {
      ...messagesBySession.value,
      [sessionId]: [...messages],
    }
  }

  async function loadSessions(): Promise<void> {
    const response = await chatApi.getSessions()
    sessions.value = sortSessions(response)
    if (sessions.value.length > 0) {
      await switchSession(sessions.value[0].sessionCode)
    } else {
      activeSessionId.value = null
      draftMessages.value = []
    }
  }

  async function createSession(modelCode: string): Promise<SessionView> {
    const session = await chatApi.createSession(modelCode)
    sessions.value = sortSessions([session, ...sessions.value.filter(item => item.sessionCode !== session.sessionCode)])
    setSessionMessages(session.sessionCode, [])
    activeSessionId.value = session.sessionCode
    pendingSessionTitle.value = ''
    draftMessages.value = []
    return session
  }

  async function switchSession(sessionId: string): Promise<void> {
    const session = await chatApi.getSession(sessionId)
    const existingMessages = messagesBySession.value[sessionId] ?? []
    const nextMessages = Array.isArray(session.messages) ? session.messages : existingMessages
    sessions.value = sortSessions([
      session,
      ...sessions.value.filter(item => item.sessionCode !== sessionId),
    ])
    setSessionMessages(sessionId, nextMessages)
    activeSessionId.value = sessionId
    pendingSessionTitle.value = ''
    draftMessages.value = []
  }

  async function deleteSession(sessionId: string): Promise<void> {
    await chatApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter(item => item.sessionCode !== sessionId)

    const nextMessages = { ...messagesBySession.value }
    delete nextMessages[sessionId]
    messagesBySession.value = nextMessages

    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.sessionCode ?? null
      pendingSessionTitle.value = ''
    }

    if (!activeSessionId.value) {
      draftMessages.value = []
    }
  }

  async function updateSession(session: SessionView): Promise<SessionView> {
    const updated = await chatApi.updateSession(session.sessionCode, {
      title: session.title,
      modelCode: session.modelCode,
      systemPrompt: session.systemPrompt,
      maxRounds: session.maxRounds,
      status: session.status,
    })

    sessions.value = sortSessions([
      updated,
      ...sessions.value.filter(item => item.sessionCode !== updated.sessionCode),
    ])

    return updated
  }

  function startDraftSession(): void {
    activeSessionId.value = null
    pendingSessionTitle.value = ''
    draftMessages.value = []
    streamingSessionId.value = null
    clearStreamingState()
  }

  function setActiveSessionById(sessionId: string): void {
    activeSessionId.value = sessionId
    pendingSessionTitle.value = ''
    draftMessages.value = []
  }

  function addUserMessage(content: string, sessionId?: string | null): void {
    const normalized = content.trim()
    if (!normalized) return

    const message: ChatMessage = { role: 'user', content: normalized }
    const targetSessionId = sessionId ?? activeSessionId.value

    if (targetSessionId) {
      const current = messagesBySession.value[targetSessionId] ?? []
      setSessionMessages(targetSessionId, [...current, message])
      return
    }

    pendingSessionTitle.value = normalized
    draftMessages.value = [...draftMessages.value, message]
  }

  function addAssistantMessage(content: string, sessionId?: string | null): void {
    const normalized = content.trim()
    if (!normalized) return

    const targetSessionId = sessionId ?? activeSessionId.value
    if (!targetSessionId) return

    const current = messagesBySession.value[targetSessionId] ?? []
    setSessionMessages(targetSessionId, [...current, { role: 'assistant', content: normalized }])
  }

  function bindSessionFromResponse(sessionId: string): void {
    streamingSessionId.value = sessionId
    if (!(sessionId in messagesBySession.value)) {
      setSessionMessages(sessionId, draftMessages.value)
    }
    if (!activeSessionId.value) {
      activeSessionId.value = sessionId
    }
    draftMessages.value = []
  }

  function addStreamingEvent(event: StreamEventMessage): void {
    streamingEvents.value = [...streamingEvents.value, event]
  }

  function appendStreamContent(token: string): void {
    streamContent.value += token
  }

  function finalizeAssistantMessage(): void {
    const targetSessionId = streamingSessionId.value ?? activeSessionId.value
    if (targetSessionId && streamContent.value.trim()) {
      addAssistantMessage(streamContent.value, targetSessionId)
    }
    clearStreamingState()
  }

  function clearStreamingState(): void {
    isStreaming.value = false
    streamContent.value = ''
    streamingEvents.value = []
    streamingSessionId.value = null
  }

  return {
    sessions,
    messagesBySession,
    activeSessionId,
    activeSession,
    currentMessages,
    pendingSessionTitle,
    draftMessages,
    isStreaming,
    streamContent,
    streamingEvents,
    loadSessions,
    createSession,
    switchSession,
    deleteSession,
    updateSession,
    startDraftSession,
    setSessionMessages,
    addUserMessage,
    addAssistantMessage,
    bindSessionFromResponse,
    addStreamingEvent,
    appendStreamContent,
    finalizeAssistantMessage,
    clearStreamingState,
  }
})
