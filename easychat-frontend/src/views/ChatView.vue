<template>
  <div class="chat-view">
    <ChatSidebar
      :sessions="chatStore.sessions"
      :active-session-id="chatStore.activeSessionId"
      :model-options="configStore.modelOptions"
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
            :options="configStore.modelOptions"
            :loading="configStore.modelOptionsLoading"
            :disabled="chatStore.isStreaming || !chatStore.activeSessionId"
            @visible-change="handleModelSelectorVisibleChange"
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
          <h3>请先新建或选择一个会话</h3>
          <p>会话创建完成后，再为当前会话选择模型并发送消息。</p>
        </div>
      </div>

      <ChatInput
        :disabled="chatStore.isStreaming || !chatStore.activeSessionId"
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
  return chatStore.activeSession?.title || '未选择会话'
})

const currentModelLabel = computed(() => {
  if (!chatStore.activeSessionId) {
    return '请先新建或选择会话'
  }

  const modelCode = configStore.currentModel
  return `当前模型: ${configStore.getModelLabel(modelCode)}`
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
  scrollToBottom()
}

function resolveSelectedModel(): string | null {
  const modelCode = configStore.currentModel || configStore.modelOptions[0]?.code
  if (!modelCode) {
    ElMessage.warning('未获取到可用模型，请检查 /model/list 接口')
    return null
  }

  if (modelCode !== configStore.currentModel) {
    configStore.setModel(modelCode)
  }

  const selectedModel = configStore.modelOptions.find(item => item.code === modelCode)
  return selectedModel?.name || modelCode
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
  const sessionId = chatStore.activeSessionId
  if (!sessionId) {
    ElMessage.warning('请先新建会话')
    return
  }

  const model = resolveSelectedModel()
  if (!model) {
    return
  }

  chatStore.isStreaming = true
  chatStore.streamContent = ''
  chatStore.streamingEvents = []

  const controller = new AbortController()
  activeAbortController.value = controller
  const requestMessages = createRequestMessages(content)

  chatStore.addUserMessage(content, sessionId)
  scrollToBottom()

  try {
    const response = await streamChat({
      sessionId,
      model,
      messages: requestMessages,
      toolsEnabled: configStore.toolsEnabled,
      ragEnabled: configStore.ragEnabled,
    }, controller.signal)

    handleStreamChat(response, {
      onSession(nextSessionId) {
        chatStore.bindSessionFromResponse(nextSessionId)
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
        chatStore.finalizeAssistantMessage()
        await refreshActiveSession(sessionId)

        const session = chatStore.activeSession
        if (session) {
          await persistSessionTitle(session, content)
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
  const sessionId = chatStore.activeSessionId
  if (!sessionId) {
    ElMessage.warning('请先新建会话')
    return
  }

  const model = resolveSelectedModel()
  if (!model) {
    return
  }

  chatStore.isStreaming = true
  const requestMessages = createRequestMessages(content)

  chatStore.addUserMessage(content, sessionId)
  scrollToBottom()

  try {
    const response = await chat({
      sessionId,
      model,
      messages: requestMessages,
      toolsEnabled: configStore.toolsEnabled,
      ragEnabled: configStore.ragEnabled,
    })

    if (!response.sessionId) {
      throw new Error('后端未返回 sessionId')
    }

    chatStore.addAssistantMessage(response.content, response.sessionId)
    await refreshActiveSession(response.sessionId)

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

async function handleNewChat(): Promise<void> {
  if (chatStore.isStreaming) return

  try {
    await chatStore.createSession()
    scrollToBottom()
  } catch (error) {
    ElMessage.error((error as Error).message || '新建会话失败，请重试')
  }
}

async function handleSessionClick(sessionId: string): Promise<void> {
  try {
    await refreshActiveSession(sessionId)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载会话失败，请重试')
  }
}

async function handleDeleteSession(sessionId: string): Promise<void> {
  try {
    await chatStore.deleteSession(sessionId)
    if (chatStore.activeSessionId) {
      await refreshActiveSession(chatStore.activeSessionId)
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '删除会话失败，请重试')
  }
}

async function handleUpdateSession(session: SessionView): Promise<void> {
  try {
    await chatStore.updateSession(session)
    if (session.sessionCode === chatStore.activeSessionId) {
      await refreshActiveSession(session.sessionCode)
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '更新会话失败，请重试')
  }
}

async function handleModelChange(model: string): Promise<void> {
  configStore.setModel(model)
}

async function handleModelSelectorVisibleChange(visible: boolean): Promise<void> {
  if (!visible) return

  try {
    await configStore.loadModelOptions()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载模型列表失败')
  }
}

onMounted(async () => {
  const [sessionResult] = await Promise.allSettled([
    chatStore.loadSessions(),
  ])

  if (sessionResult.status === 'rejected') {
    ElMessage.error((sessionResult.reason as Error).message || '加载会话列表失败')
    return
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
