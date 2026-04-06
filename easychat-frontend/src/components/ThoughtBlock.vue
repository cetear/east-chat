<template>
  <div class="thought-block" :class="{ collapsed }">
    <div class="thought-header" @click="collapsed = !collapsed">
      <span class="thought-icon">&#x1F9E0;</span>
      <span class="thought-label">Thinking...</span>
      <el-icon class="collapse-icon" :class="{ rotated: !collapsed }"><ArrowRight /></el-icon>
    </div>
    <div v-show="!collapsed" class="thought-content">
      {{ content }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

defineProps<{ content: string }>()
const collapsed = ref(false)
</script>

<style scoped>
.thought-block {
  background: #fffbeb;
  border-left: 3px solid #f59e0b;
  border-radius: 6px;
  padding: 10px 14px;
  margin: 6px 0;
}

.thought-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #92400e;
  font-weight: 500;
}

.thought-icon {
  font-size: 16px;
}

.collapse-icon {
  margin-left: auto;
  transition: transform 0.2s;
}

.collapse-icon.rotated {
  transform: rotate(90deg);
}

.thought-content {
  margin-top: 8px;
  font-size: 13px;
  color: #78350f;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
