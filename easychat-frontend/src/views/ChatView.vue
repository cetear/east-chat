<template>
  <div class="chat-view">
    <ChatSidebar
      :sessions="chatStore.sessions"
      :active-session-id="chatStore.activeSessionId"
      @new-chat="handleNewChat"
      @session-click="handleSessionClick"
      @delete-session="handleDeleteSession"
      @update-session="handleUpdateSession"
    />

    <div class="chat-main">
      <div class="chat-header">
        <div>
          <h2>{{ currentTitle }}</h2>
          <p class="chat-subtitle">{{ currentModelLabel }}</p>
        </div>

        <div class="header-actions">
          <el-button-group>
            <el-button
              :type="configStore.isStreamingMode ? 'primary' : 'default'"
              @click="configStore.isStreamingMode = true"
            >
              流式输出
            </el-button>
            <el-button
              :type="configStore.isStreamingMode ? 'default' : 'primary'"
              @click="configStore.isStreamingMode = false"
            >
              非流式输出
            </el-button>
          </el-button-group>

          <el-switch
            v-model="configStore.toolsEnabled"
            inline-prompt
            active-text="Tools"
          />

          <el-switch
            v-model="configStore.ragEnabled"
            inline-prompt
            active-text="RAG"
          />

          <ModelSelector
            :model-value="configStore.currentModel"
            :disabled="chatStore.isStreaming"
            @update:model-value="handleModelChange"
          />

          <el-button
            v-if="chatStore.isStreaming"
            type="danger"
            plain
            @click="handleStopStreaming"
          >
            停止生成
          </el-button>
        </div>
      </div>

      <div ref="messagesContainer" class="chat-messages">
        <ChatMessage
          v-for="(message, index) in chatStore.currentMessages"
          :key="`${message.role}-${index}`"
          :message="message"
        />

        <StreamMessage
          v-if="chatStore.isStreaming"
          :content="chatStore.streamContent"
          :loading="chatStore.isStreaming"
          :streaming-events="chatStore.streamingEvents"
        />

        <div v-if="!chatStore.currentMessages.length && !chatStore.isStreaming" class="empty-state">
          <h3>开始一段新的对话</h3>
          <p>输入第一条消息后，后端会自动创建会话。</p>
        </div>
      </div>

      <ChatInput
        :disabled="chatStore.isStreaming"
        @send="handleSendMessage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ChatSidebar from '@/components/ChatSidebar.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import ModelSelector from '@/components/ModelSelector.vue'
import StreamMessage from '@/components/StreamMessage.vue'
import { chat, streamChat } from '@/api/chat'
import { useChatStore } from '@/stores/chatStore'
import { useConfigStore } from '@/stores/configStore'
import type { ChatMessage as ChatMessageType, SessionView } from '@/types/message'
import { handleStreamChat } from '@/utils/sse'

const chatStore = useChatStore()
const configStore = useConfigStore()
const messagesContainer = ref<HTMLElement | null>(null)
const activeAbortController = ref<AbortController | null>(null)

const currentTitle = computed(() => {
  return chatStore.activeSession?.title || chatStore.pendingSessionTitle || 'New Chat'
})

const currentModelLabel = computed(() => {
  return `当前模型: ${configStore.currentModel}`
})

function scrollToBottom(): void {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

function createRequestMessages(content: string): ChatMessageType[] {
  return [{ role: 'user', content: content.trim() }]
}

async function refreshActiveSession(sessionId: string): Promise<void> {
  await chatStore.switchSession(sessionId)
  configStore.setModel(chatStore.activeSession?.modelCode || configStore.currentModel)
  scrollToBottom()
}

async function persistSessionTitle(session: SessionView, content: string): Promise<void> {
  if (session.title && session.title !== 'New Chat') return

  const title = content.slice(0, 20) + (content.length > 20 ? '...' : '')
  await chatStore.updateSession({
    ...session,
    title,
  })
}

async function handleStreamSend(content: string): Promise<void> {
  chatStore.isStreaming = true
  chatStore.streamContent = ''
  chatStore.streamingEvents = []

  const controller = new AbortController()
  activeAbortController.value = controller

  const existingSessionId = chatStore.activeSessionId
  const requestMessages = createRequestMessages(content)

  if (existingSessionId) {
    chatStore.addUserMessage(content, existingSessionId)
  } else {
    chatStore.pendingSessionTitle = content.trim()
  }

  scrollToBottom()

  try {
    const response = await streamChat({
      sessionId: existingSessionId,
      model: configStore.currentModel,
      messages: requestMessages,
      toolsEnabled: configStore.toolsEnabled,
      ragEnabled: configStore.ragEnabled,
    }, controller.signal)

    handleStreamChat(response, {
      onSession(sessionId) {
        chatStore.bindSessionFromResponse(sessionId)
      },
      onMessage(token) {
        chatStore.appendStreamContent(token)
        scrollToBottom()
      },
      onThought(data) {
        chatStore.addStreamingEvent({
          id: crypto.randomUUID(),
          role: 'assistant',
          type: 'thought',
          content: data.content,
          timestamp: Date.now(),
        })
        scrollToBottom()
      },
      onAction(data) {
        chatStore.addStreamingEvent({
          id: crypto.randomUUID(),
          role: 'assistant',
          type: 'action',
          content: '',
          toolName: data.tool,
          toolInput: data.input,
          timestamp: Date.now(),
        })
        scrollToBottom()
      },
      onObservation(data) {
        chatStore.addStreamingEvent({
          id: crypto.randomUUID(),
          role: 'assistant',
          type: 'observation',
          content: data.content,
          timestamp: Date.now(),
        })
        scrollToBottom()
      },
      onError(message) {
        if (message !== 'The user aborted a request.') {
          ElMessage.error(`消息发送失败: ${message}`)
        }
        chatStore.clearStreamingState()
      },
      async onFinish() {
        const sessionId = chatStore.activeSessionId
        if (sessionId && !existingSessionId) {
          chatStore.addUserMessage(content, sessionId)
          await refreshActiveSession(sessionId)
          const session = chatStore.activeSession
          if (session) {
            await persistSessionTitle(session, content)
          }
        }

        chatStore.finalizeAssistantMessage()
        if (sessionId) {
          await refreshActiveSession(sessionId)
        }
        scrollToBottom()
      },
    })
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      ElMessage.error((error as Error).message || '消息发送失败，请重试')
    }
    chatStore.clearStreamingState()
  } finally {
    activeAbortController.value = null
  }
}

async function handleNormalSend(content: string): Promise<void> {
  chatStore.isStreaming = true
  const existingSessionId = chatStore.activeSessionId
  const requestMessages = createRequestMessages(content)

  if (existingSessionId) {
    chatStore.addUserMessage(content, existingSessionId)
  } else {
    chatStore.pendingSessionTitle = content.trim()
  }

  scrollToBottom()

  try {
    const response = await chat({
      sessionId: existingSessionId,
      model: configStore.currentModel,
      messages: requestMessages,
      toolsEnabled: configStore.toolsEnabled,
      ragEnabled: configStore.ragEnabled,
    })

    const sessionId = response.sessionId
    if (!sessionId) {
      throw new Error('后端未返回 sessionId')
    }

    if (!existingSessionId) {
      chatStore.bindSessionFromResponse(sessionId)
      chatStore.addUserMessage(content, sessionId)
    }

    chatStore.addAssistantMessage(response.content, sessionId)
    await refreshActiveSession(sessionId)

    const session = chatStore.activeSession
    if (session) {
      await persistSessionTitle(session, content)
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '消息发送失败，请重试')
  } finally {
    chatStore.isStreaming = false
    scrollToBottom()
  }
}

async function handleSendMessage(content: string): Promise<void> {
  if (!content.trim()) return

  if (configStore.isStreamingMode) {
    await handleStreamSend(content)
    return
  }

  await handleNormalSend(content)
}

function handleStopStreaming(): void {
  activeAbortController.value?.abort()
  chatStore.clearStreamingState()
}

function handleNewChat(): void {
  chatStore.startDraftSession()
}

async function handleSessionClick(sessionId: string): Promise<void> {
  await refreshActiveSession(sessionId)
}

async function handleDeleteSession(sessionId: string): Promise<void> {
  await chatStore.deleteSession(sessionId)
  if (chatStore.activeSessionId) {
    await refreshActiveSession(chatStore.activeSessionId)
  }
}

async function handleUpdateSession(session: SessionView): Promise<void> {
  await chatStore.updateSession(session)
  if (session.sessionCode === chatStore.activeSessionId) {
    await refreshActiveSession(session.sessionCode)
  }
}

async function handleModelChange(model: string): Promise<void> {
  configStore.setModel(model)
  if (!chatStore.activeSession) return

  await chatStore.updateSession({
    ...chatStore.activeSession,
    modelCode: model,
  })
  await refreshActiveSession(chatStore.activeSession.sessionCode)
}

onMounted(async () => {
  await chatStore.loadSessions()
  if (chatStore.activeSession?.modelCode) {
    configStore.setModel(chatStore.activeSession.modelCode)
  }
})

onBeforeUnmount(() => {
  activeAbortController.value?.abort()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100vh;
  width: 100%;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #fafafa;
}

.chat-header {
  padding: 15px 20px;
  border-bottom: 1px solid #e0e0e0;
  background-color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.chat-header h2 {
  margin: 0;
  font-size: 20px;
}

.chat-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.empty-state {
  margin: auto;
  text-align: center;
  color: #909399;
}

.empty-state h3 {
  margin-bottom: 10px;
  color: #303133;
}

.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #555;
}
</style>
