<template>
  <div class="chat-message" :class="{ 'user-message': message.role === 'user' }">
    <div class="message-content">
      <div class="message-role">{{ message.role === 'user' ? 'You' : 'AI' }}</div>

      <ThoughtBlock v-if="eventType === 'thought'" :content="message.content" />
      <ToolCard
        v-else-if="eventType === 'action'"
        :toolName="toolName"
        :toolInput="toolInput"
        :toolOutput="toolOutput"
      />
      <ObservationBlock v-else-if="eventType === 'observation'" :content="message.content" />

      <div v-else-if="message.role === 'user'" class="message-text plain-text">{{ message.content }}</div>
      <div v-else class="message-text" v-html="renderedContent"></div>
      <img v-for="(src, index) in images" :key="index" :src="src" alt="消息附件" class="attachment-image" referrerpolicy="no-referrer" />
      <p v-if="notice" class="message-status" role="status">{{ notice }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { messageNotice } from '@/utils/chatPresentation'
import { validImage } from '@/utils/validation'
import ThoughtBlock from './ThoughtBlock.vue'
import ToolCard from './ToolCard.vue'
import ObservationBlock from './ObservationBlock.vue'
import type { ChatMessage as PlainChatMessage, StreamEventMessage } from '@/types/message'

const props = defineProps<{
  message: PlainChatMessage | StreamEventMessage
}>()

const notice = computed(() => 'type' in props.message ? '' : messageNotice(props.message))

const eventType = computed(() => {
  return 'type' in props.message ? props.message.type : null
})

const toolName = computed(() => {
  return 'toolName' in props.message ? props.message.toolName || '' : ''
})

const toolInput = computed(() => {
  return 'toolInput' in props.message ? props.message.toolInput : undefined
})

const toolOutput = computed(() => {
  return 'toolOutput' in props.message ? props.message.toolOutput : undefined
})

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  if (props.message.role === 'user') return props.message.content
  try {
    return renderMarkdown(props.message.content)
  } catch {
    return ''
  }
})
const images = computed<string[]>(() => {
  if ('type' in props.message) return []
  let values = props.message.images
  if (!values && props.message.paramJson) {
    try { values = JSON.parse(props.message.paramJson).images } catch { return [] }
  }
  return Array.isArray(values) ? values.filter(value => typeof value === 'string' && validImage(value)) : []
})
</script>

<style scoped>
.plain-text { white-space: pre-wrap; }
.attachment-image { display: block; max-width: 280px; max-height: 240px; margin-top: 8px; }
.message-status { font-size: 12px; opacity: .8; margin-top: 8px; }
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
