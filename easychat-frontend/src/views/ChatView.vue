<template>
  <div class="chat-view">
    <ChatSidebar :sessions="chatStore.sessions" :active-session-id="chatStore.activeSessionId"
      @new-chat="handleNewChat" @session-click="handleSessionClick"
      @delete-session="handleDeleteSession" @update-session="handleRename" />
    <div class="chat-main">
      <header class="chat-header">
        <div><h2>{{ chatStore.activeSession?.title || 'EasyChat' }}</h2><small>模型按每次请求选择 · 时间按后端部署时区显示</small></div>
        <div class="header-actions">
          <el-button @click="authDialog = true">{{ identity ? '登录凭证' : authRequired ? '接入登录' : '演示模式' }}</el-button>
          <el-button v-if="admin" @click="$router.push('/admin')">管理</el-button>
          <el-button v-if="configStore.knowledgeEnabled && authenticated" @click="knowledgeDialog = true">知识库</el-button>
          <el-button :disabled="busy || !chatStore.activeSession" @click="openSettings">会话设置</el-button>
          <el-button :disabled="busy || !chatStore.activeSessionId" @click="refreshHistory">刷新历史</el-button>
        </div>
      </header>
      <div class="chat-options">
        <ModelSelector :model-value="configStore.currentModel" :options="configStore.modelOptions"
          :loading="configStore.modelOptionsLoading" :disabled="busy || !authenticated"
          @visible-change="loadModelsOnOpen" @update:model-value="configStore.setModel" />
        <el-button :disabled="busy || !authenticated" :loading="configStore.modelOptionsLoading" @click="loadModelsOnOpen(true)">刷新模型</el-button>
        <el-switch v-model="configStore.isStreamingMode" :disabled="busy" active-text="流式" inactive-text="同步" aria-label="流式输出" />
        <el-switch v-model="configStore.toolsEnabled" :disabled="busy" active-text="工具" inactive-text="工具" aria-label="工具调用" />
        <el-switch v-if="configStore.knowledgeEnabled" v-model="configStore.ragEnabled" :disabled="busy" active-text="知识检索" inactive-text="知识检索" aria-label="知识检索" />
        <el-input v-if="configStore.knowledgeEnabled" v-model="configStore.dataset" :disabled="busy" placeholder="数据集" style="width: 150px" />
        <el-button v-if="chatStore.isStreaming" type="danger" plain @click="stop">停止生成</el-button>
      </div>
      <el-alert v-if="!authenticated" title="请通过现有登录系统提供 Bearer token。EasyChat 后端不提供登录接口。" type="info" :closable="false" />
      <el-alert v-else-if="configStore.modelOptionsError" title="模型目录加载失败，请检查服务后刷新模型。"
        :description="configStore.modelOptionsError" type="error" :closable="false" />
      <el-alert v-else-if="!configStore.modelOptionsLoading && configStore.modelOptionsLoaded && !configStore.modelOptions.length"
        title="当前没有可用模型，请管理员配置并启用模型、渠道及路由绑定后刷新模型。" type="warning" :closable="false" />
      <el-alert v-if="chatStore.historyError" title="会话历史加载失败，暂时不能发送。请在服务恢复后点击刷新历史。"
        :description="chatStore.historyError" type="error" :closable="false" />
      <el-alert v-if="chatStore.activeSession?.status === 0" title="此会话已关闭，可在会话设置中重新开启。" type="warning" :closable="false" />
      <div ref="messagesContainer" class="chat-messages">
        <ChatMessage v-for="(message, index) in chatStore.currentMessages" :key="index" :message="message" />
        <p v-if="!chatStore.isStreaming && retainedOutput && chatStore.streamContent">本地已接收内容（尚未在历史中完整确认）</p>
        <StreamMessage v-if="chatStore.isStreaming || retainedOutput" :content="chatStore.streamContent"
          :loading="chatStore.isStreaming" :streaming-events="chatStore.streamingEvents" />
        <section v-if="visibleRound" class="round-details">
          <p v-if="visibleRound.error" role="alert">{{ visibleRound.error }}</p>
          <p v-for="(warning, index) in visibleRound.warnings" :key="index">{{ warning }}</p>
          <RagReference :references="visibleRound.sources" />
          <details v-if="visibleRound.meta.providerCode || visibleRound.meta.usage" class="response-details">
            <summary>查看响应详情</summary>
          <small v-if="visibleRound.meta.finishReason">结束原因：{{ visibleRound.meta.finishReason }}{{ visibleRound.meta.finishReason === 'length' ? '（回答可能被截断）' : '' }}</small>
          <small v-if="visibleRound.meta.providerCode"> · 渠道：{{ visibleRound.meta.providerCode }}</small>
          <p v-if="visibleRound.meta.usage">
            Token：{{ visibleRound.meta.usage.totalTokens ?? '未完整上报' }}
            · 调用尝试 {{ visibleRound.meta.usage.attempts }} · 已上报 {{ visibleRound.meta.usage.reportedCalls }}
          </p>
          </details>
          <small v-if="visibleRound.sources.length || chatStore.streamingEvents.length">引用与工具记录仅保留在本次页面中，重新加载后无法从历史恢复。</small>
        </section>
        <div v-if="!chatStore.currentMessages.length && !chatStore.isStreaming && !chatStore.historyError" class="empty-state">
          <h3>{{ chatStore.activeSessionId ? '可以开始对话了' : '新建或选择一个会话' }}</h3>
          <p>模型与数据集按本次请求提交；需要隔离知识库上下文时请新建会话。</p>
        </div>
      </div>
      <ChatInput ref="chatInput" :key="chatStore.activeSessionId ?? 'empty'"
        :disabled="busy || !authenticated || !configStore.currentModel || !chatStore.historyReady || chatStore.activeSession?.status !== 1"
        :vision="supportsVision" :media-enabled="configStore.mediaEnabled" @send="send" />
    </div>
    <el-dialog v-model="authDialog" title="接入已有登录系统" width="480px">
      <p>可由宿主身份系统自动注入凭证；手动调试时可粘贴已有 token。凭证仅保存在当前页面内存。</p>
      <el-input v-model="tokenDraft" type="password" show-password placeholder="Bearer token（不含 Bearer 前缀）" />
      <a v-if="loginUrl" :href="loginUrl">前往登录系统</a>
      <template #footer>
        <el-button @click="setIdentity(null); authDialog = false">清除凭证</el-button>
        <el-button type="primary" :disabled="!tokenDraft.trim()" @click="applyToken">使用凭证</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="settingsDialog" title="会话设置" width="500px">
      <el-form label-width="100px">
        <el-form-item label="标题"><el-input v-model="settings.title" /></el-form-item>
        <el-form-item label="系统提示词"><el-input v-model="settings.systemPrompt" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="历史轮数"><el-input-number v-model="settings.maxRounds" :min="1" :max="100" :precision="0" /></el-form-item>
        <el-form-item label="开启会话"><el-switch v-model="settings.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" :disabled="busy" @click="saveSettings">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="knowledgeDialog" title="知识库" width="90%" destroy-on-close>
      <KnowledgePanel v-if="knowledgeDialog && authenticated" :dataset="configStore.dataset" />
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChatSidebar from '@/components/ChatSidebar.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import ChatInput from '@/components/ChatInput.vue'
import ModelSelector from '@/components/ModelSelector.vue'
import StreamMessage from '@/components/StreamMessage.vue'
import RagReference from '@/components/RagReference.vue'
import KnowledgePanel from '@/components/KnowledgePanel.vue'
import { chat, streamChat } from '@/api/chat'
import { useChatStore } from '@/stores/chatStore'
import { useConfigStore } from '@/stores/configStore'
import { authRequired, identity, isAdmin, setIdentity } from '@/auth'
import type { ChatMeta, ChatRequest, SessionView, Source } from '@/types/message'
import { persistedReply } from '@/utils/chatPresentation'
import { handleStreamChat } from '@/utils/sse'
import { normalizeDataset, validImage } from '@/utils/validation'
const chatStore = useChatStore()
const configStore = useConfigStore()
const authenticated = computed(() => !authRequired || !!identity.value?.token)
const admin = computed(isAdmin)
const supportsVision = computed(() => configStore.modelOptions.find(m => m.code === configStore.currentModel)?.supportVision ?? false)
const messagesContainer = ref<HTMLElement | null>(null)
const chatInput = ref<InstanceType<typeof ChatInput> | null>(null)
const busy = ref(false)
const retainedOutput = ref(false)
const authDialog = ref(false)
const tokenDraft = ref('')
const knowledgeDialog = ref(false)
const settingsDialog = ref(false)
const settings = ref({ title: '', systemPrompt: '', maxRounds: 10, status: 1 })
const loginUrl = (() => {
  try { const url = new URL(import.meta.env.VITE_LOGIN_URL || '', window.location.origin); return ['http:', 'https:'].includes(url.protocol) && import.meta.env.VITE_LOGIN_URL ? url.href : '' } catch { return '' }
})()
interface Round { sessionId: string; sources: Source[]; warnings: string[]; error: string; meta: ChatMeta }
const round = ref<Round | null>(null)
const visibleRound = computed(() => round.value?.sessionId === chatStore.activeSessionId ? round.value : null)
let controller: AbortController | null = null
let generation = 0
function scroll(): void { void nextTick(() => { const el = messagesContainer.value; if (el) el.scrollTop = el.scrollHeight }) }
function showError(error: unknown): void { ElMessage.error((error as Error).message || '请求失败') }
function clearTransient(): void {
  retainedOutput.value = false
  chatStore.streamContent = ''
  chatStore.streamingEvents = []
  round.value = null
}
async function send(content: string, images: string[]): Promise<void> {
  if (busy.value || !authenticated.value || !chatStore.historyReady || chatStore.activeSession?.status !== 1) return
  const model = configStore.currentModel
  if (!model) { ElMessage.warning('请先选择可用模型'); return }
  if (images.length > 1 || images.some(image => !validImage(image)) || (images.length && !supportsVision.value)) {
    ElMessage.warning('最多发送一张有效图片，并选择视觉模型'); return
  }
  let dataset: string
  try { dataset = normalizeDataset(configStore.dataset) } catch (error) { showError(error); return }
  const sessionId = chatStore.activeSessionId!
  const turnGeneration = generation
  const initialCount = chatStore.currentMessages.length
  busy.value = true
  clearTransient()
  const currentRound: Round = { sessionId, sources: [], warnings: [], error: '', meta: {} }
  round.value = currentRound
  const state = round.value
  const abort = new AbortController()
  controller = abort
  chatStore.isStreaming = true
  chatStore.addUserMessage(content, sessionId, images)
  chatInput.value?.clear()
  scroll()
  const request: ChatRequest = { sessionId, model, messages: [{ role: 'user', content, images }],
    toolsEnabled: configStore.toolsEnabled, ragEnabled: configStore.knowledgeEnabled && configStore.ragEnabled, dataset }
  let success = false
  try {
    if (configStore.isStreamingMode) {
      const response = await streamChat(request, abort.signal)
      const terminal = await handleStreamChat(response, {
        onMeta(meta) {
          if (!state.meta.sessionId && meta.sessionId) { state.sessionId = meta.sessionId; state.meta.sessionId = meta.sessionId }
          state.meta = { ...state.meta, ...meta, sessionId: state.meta.sessionId }
        },
        onMessage(text) { chatStore.streamContent += text; scroll() },
        onAction(data) {
          chatStore.streamingEvents.push({ id: crypto.randomUUID(), role: 'assistant', type: 'action', content: '', toolName: data.tool, toolInput: data.input, timestamp: Date.now() })
          scroll()
        },
        onObservation(data) {
          chatStore.streamingEvents.push({ id: crypto.randomUUID(), role: 'assistant', type: 'observation', content: data.content, timestamp: Date.now() })
          scroll()
        },
        onSources(sources) { state.sources = sources },
        onWarning(warning) { state.warnings.push(warning) },
        onError(error) { state.error = error },
        onFinish(reason) { state.meta.finishReason = reason },
      })
      success = terminal === 'finish'
    } else {
      const response = await chat(request, abort.signal)
      state.sessionId = response.sessionId || sessionId
      state.meta = response
      chatStore.streamContent = response.content
      if (response.sources) {
        try { state.sources = JSON.parse(response.sources) } catch { state.warnings.push('引用信息解析失败') }
      }
      if (response.warning) state.warnings.push(response.warning)
      success = true
    }
  } catch (error) {
    if (turnGeneration !== generation) return
    state.error = abort.signal.aborted ? '已停止接收，正在刷新历史确认后端状态；远端执行可能尚未结束。' : (error as Error).message
  } finally {
    if (turnGeneration === generation) {
      chatStore.isStreaming = false
      retainedOutput.value = !!chatStore.streamContent || !!chatStore.streamingEvents.length
      try {
        const messages = await chatStore.refreshSession(state.sessionId)
        // Replace optimistic messages. Keep partial output separately only if it has not reached persistence yet.
        const persistedAssistant = persistedReply(messages, initialCount, chatStore.streamContent, success)
        if (persistedAssistant) {
          retainedOutput.value = !!chatStore.streamingEvents.length
          chatStore.streamContent = ''
          // The persisted message already carries the failure notice.
          if (persistedAssistant.status === 0) state.error = ''
        }
        if (success && !persistedAssistant && chatStore.streamContent) state.warnings.push('回答已完成，但历史尚未返回该记录，请稍后刷新确认。')
        const session = chatStore.sessions.find(item => item.sessionCode === state.sessionId)
        if (success && turnGeneration === generation && session && (!session.title || session.title === 'New Chat')) {
          const title = content || '图片对话'
          try { await chatStore.updateSession(state.sessionId, { title: title.slice(0, 20) + (title.length > 20 ? '…' : '') }) }
          catch (error) { state.warnings.push(`会话标题更新失败：${(error as Error).message}`) }
        }
      } catch (error) { state.warnings.push(`历史刷新失败，请手动刷新；不要自动重发。${(error as Error).message}`) }
      if (turnGeneration === generation) {
        controller = null
        busy.value = false
        scroll()
      }
    }
  }
}
function stop(): void {
  controller?.abort()
  chatStore.isStreaming = false
  retainedOutput.value = !!chatStore.streamContent || !!chatStore.streamingEvents.length
}
async function run(action: () => Promise<unknown>): Promise<void> {
  if (busy.value || !authenticated.value) return
  busy.value = true
  try { await action(); scroll() } catch (error) { showError(error) } finally { busy.value = false }
}
async function handleNewChat(): Promise<void> { await run(async () => { await chatStore.createSession(); clearTransient() }) }
async function handleSessionClick(code: string): Promise<void> { await run(async () => { clearTransient(); await chatStore.switchSession(code) }) }
async function refreshHistory(): Promise<void> {
  if (!chatStore.activeSessionId) return
  await run(async () => { await chatStore.refreshSession(chatStore.activeSessionId!); clearTransient() })
}
async function handleDeleteSession(code: string): Promise<void> {
  if (busy.value) return
  try { await ElMessageBox.confirm('删除会话及全部消息？', '删除会话', { type: 'warning' }) } catch { return }
  await run(async () => { await chatStore.deleteSession(code); clearTransient() })
}
async function handleRename(session: SessionView): Promise<void> { await run(() => chatStore.updateSession(session.sessionCode, { title: session.title })) }
function openSettings(): void {
  const session = chatStore.activeSession
  if (!session) return
  settings.value = { title: session.title, systemPrompt: session.systemPrompt ?? '', maxRounds: session.maxRounds, status: session.status }
  settingsDialog.value = true
}
async function saveSettings(): Promise<void> {
  if (!Number.isInteger(settings.value.maxRounds) || settings.value.maxRounds < 1 || settings.value.maxRounds > 100) { ElMessage.warning('历史轮数须为 1–100'); return }
  await run(async () => { await chatStore.updateSession(chatStore.activeSessionId!, settings.value); settingsDialog.value = false })
}
async function loadModelsOnOpen(open: boolean): Promise<void> {
  if (!open || !authenticated.value) return
  try { await configStore.loadModelOptions() } catch (error) { showError(error) }
}
function applyToken(): void { setIdentity({ token: tokenDraft.value.trim().replace(/^Bearer\s+/i, ''), roles: [] }); tokenDraft.value = ''; authDialog.value = false }
watch(identity, async () => {
  const version = ++generation
  controller?.abort()
  controller = null
  busy.value = false
  chatStore.reset()
  clearTransient()
  knowledgeDialog.value = false
  settingsDialog.value = false
  configStore.modelOptions = []
  configStore.modelOptionsLoading = false
  configStore.modelOptionsLoaded = false
  configStore.modelOptionsError = ''
  configStore.currentModel = ''
  if (!authenticated.value) return
  busy.value = true
  const results = await Promise.allSettled([chatStore.loadSessions(), configStore.loadModelOptions()])
  if (version !== generation) return
  for (const result of results) if (result.status === 'rejected') showError(result.reason)
  busy.value = false
}, { immediate: true })
onBeforeUnmount(() => { ++generation; controller?.abort(); chatStore.isStreaming = false })
</script>
<style scoped>
.chat-view { display: flex; height: 100vh; width: 100%; }
.chat-main { flex: 1; min-width: 0; display: flex; flex-direction: column; background: #fafafa; }
.chat-header { padding: 16px 20px; border-bottom: 1px solid #ddd; background: white; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.chat-header h2 { font-size: 20px; margin-bottom: 4px; }
.header-actions, .chat-options { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.chat-options { padding: 12px 20px; background: white; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; }
.empty-state { margin: auto; text-align: center; color: #909399; }
.empty-state p { margin-top: 12px; }
.round-details { padding: 12px; color: #606266; font-size: 13px; }
.response-details summary { cursor: pointer; }
.round-details p { margin: 8px 0; }
small { color: #909399; }
</style>
