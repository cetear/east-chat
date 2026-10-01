import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as api from '@/api/chat'
import type { ChatMessage, SessionUpdate, SessionView, StreamEventMessage } from '@/types/message'
const sort = (items: SessionView[]) => [...items].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionView[]>([])
  const messagesBySession = ref<Record<string, ChatMessage[]>>({})
  const historyErrors = ref<Record<string, string>>({})
  const activeSessionId = ref<string | null>(null)
  const isStreaming = ref(false)
  const streamContent = ref('')
  const streamingEvents = ref<StreamEventMessage[]>([])
  let identityVersion = 0
  const activeSession = computed(() => sessions.value.find(s => s.sessionCode === activeSessionId.value) ?? null)
  const currentMessages = computed(() => messagesBySession.value[activeSessionId.value ?? ''] ?? [])
  const historyError = computed(() => historyErrors.value[activeSessionId.value ?? ''] ?? '')
  const historyReady = computed(() => !!activeSessionId.value && !historyError.value &&
    Object.prototype.hasOwnProperty.call(messagesBySession.value, activeSessionId.value))
  function setSessionMessages(code: string, messages: ChatMessage[]): void { messagesBySession.value[code] = messages }
  function upsert(session: SessionView): void {
    sessions.value = sort([session, ...sessions.value.filter(s => s.sessionCode !== session.sessionCode)])
  }
  async function refreshSession(code: string): Promise<ChatMessage[]> {
    const version = identityVersion
    try {
      const [session, messages] = await Promise.all([api.getSession(code), api.getMessages(code)])
      if (version !== identityVersion) return []
      upsert(session)
      setSessionMessages(code, messages)
      delete historyErrors.value[code]
      return messages
    } catch (error) {
      if (version === identityVersion) historyErrors.value[code] = (error as Error).message || '会话历史加载失败'
      throw error
    }
  }
  async function switchSession(code: string): Promise<void> {
    activeSessionId.value = code
    await refreshSession(code)
  }
  async function loadSessions(): Promise<void> {
    const version = identityVersion
    const result = await api.getSessions()
    if (version !== identityVersion) return
    sessions.value = sort(result)
    const first = sessions.value[0]
    if (first) await switchSession(first.sessionCode)
  }
  async function createSession(): Promise<SessionView> {
    const version = identityVersion
    const session = await api.createSession()
    if (version !== identityVersion) throw new Error('登录身份已变更')
    upsert(session)
    setSessionMessages(session.sessionCode, [])
    activeSessionId.value = session.sessionCode
    return session
  }
  async function deleteSession(code: string): Promise<void> {
    const version = identityVersion
    await api.deleteSession(code)
    if (version !== identityVersion) return
    sessions.value = sessions.value.filter(s => s.sessionCode !== code)
    delete messagesBySession.value[code]
    delete historyErrors.value[code]
    if (activeSessionId.value === code) {
      activeSessionId.value = null
      if (sessions.value[0]) await switchSession(sessions.value[0].sessionCode)
    }
  }
  async function updateSession(code: string, update: SessionUpdate): Promise<void> {
    const version = identityVersion
    const session = await api.updateSession(code, update)
    if (version === identityVersion) upsert(session)
  }
  function addUserMessage(content: string, code: string, images: string[]): void {
    setSessionMessages(code, [...(messagesBySession.value[code] ?? []), { role: 'user', content, images }])
  }
  function reset(): void {
    ++identityVersion
    sessions.value = []
    messagesBySession.value = {}
    historyErrors.value = {}
    activeSessionId.value = null
    isStreaming.value = false
    streamContent.value = ''
    streamingEvents.value = []
  }
  return { sessions, messagesBySession, activeSessionId, activeSession, currentMessages, historyError, historyReady, isStreaming,
    streamContent, streamingEvents, setSessionMessages, refreshSession, switchSession, loadSessions,
    createSession, deleteSession, updateSession, addUserMessage, reset }
})
