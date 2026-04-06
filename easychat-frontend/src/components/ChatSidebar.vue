<template>
  <div class="chat-sidebar">
    <div class="sidebar-header">
      <h3>会话列表</h3>
      <el-tooltip content="新建会话" placement="top">
        <el-button type="primary" size="small" class="new-chat-btn" @click="handleNewChat">
          <el-icon><Plus /></el-icon>
          <span>新建</span>
        </el-button>
      </el-tooltip>
    </div>
    
    <div class="session-list" @click="hideContextMenu">
      <div
        v-for="session in paginatedSessions"
        :key="session.sessionId"
        class="session-item"
        :class="{ 'active': session.sessionId === activeSessionId }"
        @click="handleSessionClick(session.sessionId)"
        @contextmenu.prevent="showContextMenu($event, session)"
      >
        <div class="session-info">
          <div class="session-title">
            <el-icon class="session-icon"><ChatRound /></el-icon>
            <span class="title-text">{{ session.title }}</span>
          </div>
          <div class="session-meta">
            <span class="session-model">{{ getModelName(session.modelType) }}</span>
            <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
          </div>
        </div>
        <div class="session-actions" v-if="session.sessionId === activeSessionId">
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
        small
        layout="prev, pager, next"
        :total="sessions.length"
        :page-size="pageSize"
        v-model:current-page="currentPage"
        @current-change="handleCurrentChange"
      />
    </div>
    
    <!-- 右键菜单 -->
    <div 
      v-if="menuVisible" 
      class="context-menu" 
      :style="{ left: menuX + 'px', top: menuY + 'px' }"
    >
      <div class="menu-item" @click="renameSession(selectedSession)">
        <el-icon><Edit /></el-icon>
        <span>重命名</span>
      </div>
      <div class="menu-item delete" @click="deleteSession(selectedSession)">
        <el-icon><Delete /></el-icon>
        <span>删除</span>
      </div>
    </div>
    
    <!-- 重命名对话框 -->
    <el-dialog
      v-model="renameDialogVisible"
      title="重命名会话"
      width="350px"
      :close-on-click-modal="false"
    >
      <el-form @submit.prevent="confirmRename">
        <el-form-item label="会话标题">
          <el-input 
            v-model="renameTitle" 
            placeholder="请输入新的会话标题"
            maxlength="50"
            show-word-limit
            ref="renameInput"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="renameDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmRename" :disabled="!renameTitle.trim()">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import { Plus, Edit, Delete, ChatRound, MoreFilled } from '@element-plus/icons-vue';

const props = defineProps({
  activeSessionId: {
    type: String,
    default: ''
  },
  sessions: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['new-chat', 'session-click', 'delete-session', 'update-session']);

// 分页相关
const currentPage = ref(1);
const pageSize = ref(10);

// 右键菜单相关
const menuVisible = ref(false);
const menuX = ref(0);
const menuY = ref(0);
const selectedSession = ref(null);

// 重命名相关
const renameDialogVisible = ref(false);
const renameTitle = ref('');
const renameInput = ref(null);

// 模型名称映射
const modelNameMap = {
  'openai': 'OpenAI',
  'claude': 'Claude',
  'gemini': 'Gemini',
  'deepseek': 'DeepSeek'
};

// 获取模型显示名称
const getModelName = (modelType) => {
  return modelNameMap[modelType] || modelType;
};

// 计算分页后的会话列表
const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return props.sessions.slice(start, end);
});

const handleNewChat = () => {
  emit('new-chat');
};

const handleSessionClick = (sessionId) => {
  hideContextMenu();
  emit('session-click', sessionId);
};

const formatTime = (timestamp) => {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now - date;
  
  // 小于1分钟
  if (diff < 60000) {
    return '刚刚';
  }
  // 小于1小时
  if (diff < 3600000) {
    return Math.floor(diff / 60000) + '分钟前';
  }
  // 小于24小时
  if (diff < 86400000) {
    return Math.floor(diff / 3600000) + '小时前';
  }
  // 小于7天
  if (diff < 604800000) {
    return Math.floor(diff / 86400000) + '天前';
  }
  
  return date.toLocaleDateString();
};

// 显示右键菜单
const showContextMenu = (event, session) => {
  event.stopPropagation();
  selectedSession.value = session;
  menuX.value = event.clientX;
  menuY.value = event.clientY;
  menuVisible.value = true;
};

// 隐藏右键菜单
const hideContextMenu = () => {
  menuVisible.value = false;
};

// 重命名会话
const renameSession = (session) => {
  selectedSession.value = session;
  renameTitle.value = session.title;
  renameDialogVisible.value = true;
  menuVisible.value = false;
  
  // 自动聚焦输入框
  nextTick(() => {
    if (renameInput.value) {
      renameInput.value.focus();
    }
  });
};

// 确认重命名
const confirmRename = () => {
  if (selectedSession.value && renameTitle.value.trim()) {
    const updatedSession = { ...selectedSession.value, title: renameTitle.value.trim() };
    emit('update-session', updatedSession);
    renameDialogVisible.value = false;
  }
};

// 删除会话
const deleteSession = (session) => {
  emit('delete-session', session.sessionId);
  menuVisible.value = false;
};

// 分页相关事件
const handleCurrentChange = (current) => {
  currentPage.value = current;
  hideContextMenu();
};

// 点击其他地方隐藏菜单
window.addEventListener('click', hideContextMenu);
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

/* 右键菜单样式 */
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

/* 滚动条样式 */
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
