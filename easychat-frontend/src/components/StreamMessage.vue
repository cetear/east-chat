<template>
  <div class="stream-message">
    <!-- Agent reasoning events -->
    <template v-for="(event, index) in streamingEvents" :key="index">
      <ThoughtBlock v-if="event.type === 'thought'" :content="event.content" />
      <ToolCard v-else-if="event.type === 'action'"
        :toolName="event.toolName || ''"
        :toolInput="event.toolInput"
      />
      <ObservationBlock v-else-if="event.type === 'observation'" :content="event.content" />
    </template>

    <!-- Final answer streaming -->
    <div v-if="content" class="chat-message">
      <div class="message-content">
        <div class="message-role">AI</div>
        <div class="message-text" v-html="renderedContent"></div>
        <span v-if="loading" class="loading">
          <el-icon><Loading /></el-icon>
        </span>
      </div>
    </div>

    <!-- Loading indicator when no content yet -->
    <div v-if="loading && !content && streamingEvents.length === 0" class="chat-message">
      <div class="message-content">
        <div class="message-role">AI</div>
        <div class="message-text">
          <el-icon class="loading"><Loading /></el-icon>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import ThoughtBlock from './ThoughtBlock.vue'
import ToolCard from './ToolCard.vue'
import ObservationBlock from './ObservationBlock.vue'

const props = defineProps<{
  content: string
  loading: boolean
  streamingEvents?: Array<{
    type: string
    content: string
    toolName?: string
    toolInput?: Record<string, unknown>
  }>
}>()

const renderedContent = computed(() => {
  if (!props.content) return ''
  try {
    return marked.parse(props.content, { breaks: true })
  } catch {
    return props.content
  }
})
</script>

<style scoped>
.stream-message {
  margin: 10px 0;
}

.chat-message {
  display: flex;
  justify-content: flex-start;
  margin: 6px 0;
}

.message-content {
  max-width: 70%;
  padding: 10px 15px;
  border-radius: 18px;
  background-color: #f0f0f0;
}

.message-role {
  font-size: 12px;
  margin-bottom: 4px;
  opacity: 0.7;
}

.message-text {
  word-wrap: break-word;
  line-height: 1.5;
}

.message-text :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
}

.message-text :deep(code) {
  background: #e2e8f0;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 13px;
}

.message-text :deep(pre code) {
  background: none;
  padding: 0;
}

.loading {
  margin-left: 5px;
  color: #409eff;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
