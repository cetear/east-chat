import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Message } from '@/types/message'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<any[]>([])
  const activeSessionId = ref('')
  const isStreaming = ref(false)
  const streamContent = ref('')
  const streamingEvents = ref<Message[]>([])

  const activeSession = computed(() =>
    sessions.value.find(s => s.sessionId === activeSessionId.value)
  )

  const currentMessages = ref<any[]>([])

  async function loadSessions() {
    const response = await chatApi.getSessions()
    sessions.value = (response as any[]).map(s => ({ ...s, messages: s.messages || [] }))
    if (sessions.value.length > 0) {
      await switchSession(sessions.value[0].sessionId)
    } else {
      await createSession('openai')
    }
  }

  async function createSession(modelType: string) {
    const newSession = await chatApi.createSession(modelType) as any
    if (!newSession.messages) newSession.messages = []
    sessions.value.unshift(newSession)
    activeSessionId.value = newSession.sessionId
    currentMessages.value = []
    return newSession
  }

  async function switchSession(sessionId: string) {
    const session = await chatApi.getSession(sessionId) as any
    const index = sessions.value.findIndex(s => s.sessionId === sessionId)
    if (index !== -1) {
      sessions.value[index] = session
    }
    activeSessionId.value = sessionId
    if (!session.messages) session.messages = []
    currentMessages.value = [...session.messages]
  }

  async function deleteSession(sessionId: string) {
    await chatApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (activeSessionId.value === sessionId) {
      if (sessions.value.length > 0) {
        await switchSession(sessions.value[0].sessionId)
      } else {
        await createSession('openai')
      }
    }
  }

  async function updateSession(session: any) {
    await chatApi.updateSession(session.sessionId, session)
    const index = sessions.value.findIndex(s => s.sessionId === session.sessionId)
    if (index !== -1) {
      sessions.value[index] = session
    }
  }

  function addUserMessage(content: string) {
    const msg = { role: 'user', content }
    if (activeSession.value) {
      if (!activeSession.value.messages) activeSession.value.messages = []
      activeSession.value.messages = [...activeSession.value.messages, msg]
      currentMessages.value = [...activeSession.value.messages]
    }
  }

  function addStreamingEvent(event: Message) {
    streamingEvents.value.push(event)
  }

  function appendStreamContent(token: string) {
    streamContent.value += token
  }

  function finalizeAssistantMessage() {
    if (activeSession.value) {
      const aiMsg = { role: 'assistant', content: streamContent.value }
      activeSession.value.messages = [...activeSession.value.messages, aiMsg]
      currentMessages.value = [...activeSession.value.messages]
    }
    clearStreamingState()
  }

  function clearStreamingState() {
    isStreaming.value = false
    streamContent.value = ''
    streamingEvents.value = []
  }

  return {
    sessions, activeSessionId, activeSession, currentMessages,
    isStreaming, streamContent, streamingEvents,
    loadSessions, createSession, switchSession, deleteSession, updateSession,
    addUserMessage, addStreamingEvent, appendStreamContent,
    finalizeAssistantMessage, clearStreamingState,
  }
})
