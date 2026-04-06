<template>
  <div class="rag-reference" v-if="references && references.length">
    <div class="rag-header" @click="expanded = !expanded">
      <span class="rag-icon">&#x1F4DA;</span>
      <span class="rag-label">References ({{ references.length }})</span>
      <el-icon class="expand-icon" :class="{ rotated: expanded }"><ArrowRight /></el-icon>
    </div>
    <div v-show="expanded" class="rag-list">
      <div v-for="(ref, idx) in references" :key="idx" class="rag-item">
        <div class="rag-title">[{{ idx + 1 }}] {{ ref.title }}</div>
        <div class="rag-score" v-if="ref.score">Score: {{ ref.score.toFixed(2) }}</div>
        <div class="rag-text">{{ ref.content }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

defineProps<{
  references: Array<{ title: string; content: string; score?: number }>
}>()

const expanded = ref(false)
</script>

<style scoped>
.rag-reference {
  background: #faf5ff;
  border: 1px solid #e9d5ff;
  border-radius: 6px;
  padding: 10px 14px;
  margin: 6px 0;
}

.rag-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #7c3aed;
}

.expand-icon {
  margin-left: auto;
  transition: transform 0.2s;
}

.expand-icon.rotated {
  transform: rotate(90deg);
}

.rag-list {
  margin-top: 8px;
}

.rag-item {
  padding: 8px;
  background: #f5f3ff;
  border-radius: 4px;
  margin-bottom: 6px;
}

.rag-title {
  font-weight: 600;
  font-size: 13px;
  color: #5b21b6;
}

.rag-score {
  font-size: 11px;
  color: #8b5cf6;
  margin-top: 2px;
}

.rag-text {
  font-size: 12px;
  color: #6b21a8;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
