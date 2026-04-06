<template>
  <div class="chat-view">
    <ChatSidebar
      :sessions="chatStore.sessions"
      :activeSessionId="chatStore.activeSessionId"
      @new-chat="handleNewChat"
      @session-click="handleSessionClick"
      @delete-session="handleDeleteSession"
      @update-session="handleUpdateSession"
    />
    <div class="chat-main">
      <div class="chat-header">
        <h2>{{ chatStore.activeSession?.title || '新会话' }}</h2>
        <div class="header-actions">
          <el-button-group>
            <el-button
              :type="configStore.isStreamingMode ? 'primary' : 'default'"
              @click="configStore.isStreamingMode = true"
            >
              流式输出
            </el-button>
            <el-button
              :type="!configStore.isStreamingMode ? 'primary' : 'default'"
              @click="configStore.isStreamingMode = false"
            >
              非流式输出
            </el-button>
          </el-button-group>
          <el-switch
            v-model="configStore.toolsEnabled"
            active-text="Tools"
            inactive-text=""
            style="margin-left: 10px;"
          />
          <ModelSelector @model-change="handleModelChange" />
          <el-button type="info" @click="configStore.modelConfigVisible = true">
            <el-icon><Setting /></el-icon>
            配置
          </el-button>
        </div>
      </div>
      <ModelConfig
        v-model:visible="configStore.modelConfigVisible"
        :modelCode="configStore.currentModel"
        @config-updated="() => {}"
      />

      <div class="chat-messages" ref="messagesContainer">
        <ChatMessage
          v-for="(message, index) in chatStore.currentMessages"
          :key="index"
          :message="message"
        />
        <StreamMessage
          v-if="chatStore.isStreaming"
          :content="chatStore.streamContent"
          :loading="chatStore.isStreaming"
          :streamingEvents="chatStore.streamingEvents"
        />
      </div>
      <ChatInput
        :disabled="chatStore.isStreaming"
        @send="handleSendMessage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import ChatSidebar from '@/components/ChatSidebar.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import StreamMessage from '@/components/StreamMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import ModelSelector from '@/components/ModelSelector.vue'
import ModelConfig from '@/components/ModelConfig.vue'
import { useChatStore } from '@/stores/chatStore'
import { useConfigStore } from '@/stores/configStore'
import { streamChat, chat, updateSession } from '@/api/chat'
import { handleStreamChat } from '@/utils/sse'

const chatStore = useChatStore()
const configStore = useConfigStore()
const messagesContainer = ref<HTMLElement | null>(null)

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const handleSendMessage = async (content: string) => {
  if (!content.trim() || !chatStore.activeSession) return

  chatStore.addUserMessage(content)
  scrollToBottom()

  // 自动更新会话标题
  if (chatStore.activeSession.title === '新会话') {
    chatStore.activeSession.title = content.substring(0, 20) + (content.length > 20 ? '...' : '')
    await updateSession(chatStore.activeSession.sessionId, chatStore.activeSession)
  }

  if (configStore.isStreamingMode) {
    chatStore.isStreaming = true
    chatStore.streamContent = ''
    chatStore.streamingEvents = []

    try {
      const response = await streamChat({
        messages: chatStore.activeSession.messages,
        model: configStore.currentModel,
        sessionId: chatStore.activeSession.sessionId,
        toolsEnabled: configStore.toolsEnabled,
        ragEnabled: configStore.ragEnabled,
      })

      handleStreamChat(response, {
        onMessage(token) {
          chatStore.appendStreamContent(token)
          scrollToBottom()
        },
        onThought(data) {
          chatStore.addStreamingEvent({
            id: crypto.randomUUID(), role: 'assistant', type: 'thought',
            content: data.content, timestamp: Date.now(),
          })
          scrollToBottom()
        },
        onAction(data) {
          chatStore.addStreamingEvent({
            id: crypto.randomUUID(), role: 'assistant', type: 'action',
            content: '', toolName: data.tool, toolInput: data.input, timestamp: Date.now(),
          })
          scrollToBottom()
        },
        onObservation(data) {
          chatStore.addStreamingEvent({
            id: crypto.randomUUID(), role: 'assistant', type: 'observation',
            content: data.content, timestamp: Date.now(),
          })
          scrollToBottom()
        },
        onError(msg) {
          ElMessage.error('消息发送失败：' + msg)
          chatStore.clearStreamingState()
        },
        async onFinish() {
          chatStore.finalizeAssistantMessage()
          await updateSession(chatStore.activeSession!.sessionId, chatStore.activeSession)
          scrollToBottom()
        },
      })
    } catch (error: any) {
      ElMessage.error('发送失败，请重试')
      chatStore.clearStreamingState()
    }
  } else {
    chatStore.isStreaming = true
    try {
      const response = await chat({
        messages: chatStore.activeSession.messages,
        model: configStore.currentModel,
        sessionId: chatStore.activeSession.sessionId,
      }) as any

      if (response.error) {
        ElMessage.error('AI 响应出错：' + response.error)
      } else {
        const aiMsg = { role: 'assistant', content: response.content }
        chatStore.activeSession.messages = [...chatStore.activeSession.messages, aiMsg]
        chatStore.currentMessages = [...chatStore.activeSession.messages]
        await updateSession(chatStore.activeSession.sessionId, chatStore.activeSession)
        scrollToBottom()
      }
    } catch (error: any) {
      ElMessage.error('发送失败，请重试')
    } finally {
      chatStore.isStreaming = false
    }
  }
}

const handleNewChat = async () => {
  await chatStore.createSession(configStore.currentModel)
}

const handleSessionClick = async (sessionId: string) => {
  await chatStore.switchSession(sessionId)
  if (chatStore.activeSession) {
    configStore.currentModel = chatStore.activeSession.modelType
  }
}

const handleDeleteSession = async (sessionId: string) => {
  await chatStore.deleteSession(sessionId)
}

const handleUpdateSession = async (session: any) => {
  await chatStore.updateSession(session)
}

const handleModelChange = (model: string) => {
  configStore.setModel(model)
  if (chatStore.activeSession) {
    chatStore.activeSession.modelType = model
    updateSession(chatStore.activeSession.sessionId, chatStore.activeSession)
  }
}

onMounted(() => {
  chatStore.loadSessions()
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
  padding: 15px;
  border-bottom: 1px solid #e0e0e0;
  background-color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h2 {
  margin: 0;
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
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
