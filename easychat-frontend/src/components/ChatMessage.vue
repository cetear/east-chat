<template>
  <div class="chat-message" :class="{ 'user-message': message.role === 'user' }">
    <div class="message-content">
      <div class="message-role">{{ message.role === 'user' ? 'You' : 'AI' }}</div>

      <!-- Structured agent events -->
      <ThoughtBlock v-if="message.type === 'thought'" :content="message.content" />
      <ToolCard v-else-if="message.type === 'action'"
        :toolName="message.toolName || ''"
        :toolInput="message.toolInput"
        :toolOutput="message.toolOutput"
      />
      <ObservationBlock v-else-if="message.type === 'observation'" :content="message.content" />

      <!-- Normal text message with Markdown -->
      <div v-else class="message-text" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import ThoughtBlock from './ThoughtBlock.vue'
import ToolCard from './ToolCard.vue'
import ObservationBlock from './ObservationBlock.vue'

const props = defineProps<{
  message: {
    role: string
    content: string
    type?: string
    toolName?: string
    toolInput?: Record<string, unknown>
    toolOutput?: string
  }
}>()

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  if (props.message.role === 'user') return props.message.content
  try {
    return marked.parse(props.message.content, { breaks: true })
  } catch {
    return props.message.content
  }
})
</script>

<style scoped>
.chat-message {
  margin: 10px 0;
  display: flex;
  justify-content: flex-start;
}

.user-message {
  justify-content: flex-end;
}

.message-content {
  max-width: 70%;
  padding: 10px 15px;
  border-radius: 18px;
  background-color: #f0f0f0;
}

.user-message .message-content {
  background-color: #409eff;
  color: white;
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

.message-text :deep(p) {
  margin: 0 0 8px;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}
</style>
