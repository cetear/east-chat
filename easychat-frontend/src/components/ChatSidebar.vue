<template>
  <div class="chat-sidebar">
    <div class="sidebar-header">
      <h3>会话列表</h3>
      <el-tooltip content="新建会话" placement="top">
        <el-button type="primary" size="small" class="new-chat-btn" @click="emit('new-chat')">
          <el-icon><Plus /></el-icon>
          <span>新建</span>
        </el-button>
      </el-tooltip>
    </div>

    <div class="session-list" @click="hideContextMenu">
      <div
        v-for="session in paginatedSessions"
        :key="session.sessionCode"
        class="session-item"
        :class="{ active: session.sessionCode === activeSessionId }"
        @click="handleSessionClick(session.sessionCode)"
        @contextmenu.prevent="showContextMenu($event, session)"
      >
        <div class="session-info">
          <div class="session-title">
            <el-icon class="session-icon"><ChatRound /></el-icon>
            <span class="title-text">{{ session.title || 'New Chat' }}</span>
          </div>
          <div class="session-meta">
            <span class="session-model">{{ getModelName(session.modelCode) }}</span>
            <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
          </div>
        </div>

        <div v-if="session.sessionCode === activeSessionId" class="session-actions">
          <el-tooltip content="更多操作" placement="top">
            <el-button
              type="text"
              size="small"
              class="more-btn"
              @click.stop="showContextMenu($event, session)"
            >
              <el-icon><MoreFilled /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <div v-if="sessions.length === 0" class="empty-sessions">
        <el-empty description="暂无会话，点击上方新建按钮开始聊天" />
      </div>
    </div>

    <div v-if="sessions.length > pageSize" class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        small
        layout="prev, pager, next"
        :total="sessions.length"
        :page-size="pageSize"
        @current-change="handleCurrentChange"
      />
    </div>

    <div
      v-if="menuVisible && selectedSession"
      class="context-menu"
      :style="{ left: `${menuX}px`, top: `${menuY}px` }"
    >
      <div class="menu-item" @click="renameSession(selectedSession)">
        <el-icon><Edit /></el-icon>
        <span>重命名</span>
      </div>
      <div class="menu-item delete" @click="removeSession(selectedSession)">
        <el-icon><Delete /></el-icon>
        <span>删除</span>
      </div>
    </div>

    <el-dialog
      v-model="renameDialogVisible"
      title="重命名会话"
      width="350px"
      :close-on-click-modal="false"
    >
      <el-form @submit.prevent="confirmRename">
        <el-form-item label="会话标题">
          <el-input
            ref="renameInput"
            v-model="renameTitle"
            placeholder="请输入新的会话标题"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="renameDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!renameTitle.trim()" @click="confirmRename">
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ChatRound, Delete, Edit, MoreFilled, Plus } from '@element-plus/icons-vue'
import type { SessionView } from '@/types/message'
import type { ModelOption } from '@/types/model'

const props = defineProps<{
  activeSessionId: string | null
  sessions: SessionView[]
  modelOptions: ModelOption[]
}>()

const emit = defineEmits<{
  'new-chat': []
  'session-click': [sessionId: string]
  'delete-session': [sessionId: string]
  'update-session': [session: SessionView]
}>()

const currentPage = ref(1)
const pageSize = ref(10)
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const selectedSession = ref<SessionView | null>(null)
const renameDialogVisible = ref(false)
const renameTitle = ref('')
const renameInput = ref<{ focus: () => void } | null>(null)

const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return props.sessions.slice(start, start + pageSize.value)
})

function getModelName(modelCode: string): string {
  return props.modelOptions.find(item => item.code === modelCode)?.name || modelCode
}

function handleSessionClick(sessionId: string): void {
  hideContextMenu()
  emit('session-click', sessionId)
}

function formatTime(timestamp: string): string {
  if (!timestamp) return ''

  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 604_800_000) return `${Math.floor(diff / 86_400_000)} 天前`

  return date.toLocaleDateString()
}

function showContextMenu(event: MouseEvent, session: SessionView): void {
  event.stopPropagation()
  selectedSession.value = session
  menuX.value = event.clientX
  menuY.value = event.clientY
  menuVisible.value = true
}

function hideContextMenu(): void {
  menuVisible.value = false
}

function renameSession(session: SessionView): void {
  selectedSession.value = session
  renameTitle.value = session.title || ''
  renameDialogVisible.value = true
  menuVisible.value = false

  nextTick(() => {
    renameInput.value?.focus()
  })
}

function confirmRename(): void {
  if (!selectedSession.value || !renameTitle.value.trim()) return

  emit('update-session', {
    ...selectedSession.value,
    title: renameTitle.value.trim(),
  })

  renameDialogVisible.value = false
}

function removeSession(session: SessionView): void {
  emit('delete-session', session.sessionCode)
  menuVisible.value = false
}

function handleCurrentChange(page: number): void {
  currentPage.value = page
  hideContextMenu()
}

function handleWindowClick(): void {
  hideContextMenu()
}

window.addEventListener('click', handleWindowClick)

onBeforeUnmount(() => {
  window.removeEventListener('click', handleWindowClick)
})
</script>

<style scoped>
.chat-sidebar {
  width: 280px;
  border-right: 1px solid #e0e0e0;
  background-color: #f5f7fa;
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: relative;
}

.sidebar-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.new-chat-btn {
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 4px;
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.session-item {
  padding: 12px 16px;
  margin-bottom: 8px;
  border-radius: 10px;
  background-color: #fff;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.session-item:hover {
  background-color: #f0f9ff;
  border-color: #c6e2ff;
  transform: translateX(3px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.session-item.active {
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
  border-left: 4px solid #409eff;
  border-color: #90caf9;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.session-icon {
  color: #409eff;
  font-size: 16px;
}

.title-text {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.session-model {
  background-color: #f0f9ff;
  color: #409eff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.session-actions {
  opacity: 0;
  transition: opacity 0.3s ease;
}

.session-item:hover .session-actions,
.session-item.active .session-actions {
  opacity: 1;
}

.more-btn {
  padding: 4px;
  color: #909399;
}

.more-btn:hover {
  color: #409eff;
  background-color: #f0f9ff;
}

.empty-sessions {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
  color: #909399;
}

.pagination {
  padding: 12px;
  border-top: 1px solid #e0e0e0;
  background-color: #fff;
  display: flex;
  justify-content: center;
}

.context-menu {
  position: fixed;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 6px 0;
  z-index: 9999;
  min-width: 140px;
  border: 1px solid #e4e7ed;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  color: #606266;
}

.menu-item:hover {
  background-color: #f5f7fa;
  color: #409eff;
}

.menu-item.delete {
  color: #f56c6c;
}

.menu-item.delete:hover {
  background-color: #fef0f0;
  color: #f56c6c;
}

.menu-item .el-icon {
  font-size: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.session-list::-webkit-scrollbar {
  width: 6px;
}

.session-list::-webkit-scrollbar-track {
  background: transparent;
}

.session-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

.session-list::-webkit-scrollbar-thumb:hover {
  background: #909399;
}
</style>
