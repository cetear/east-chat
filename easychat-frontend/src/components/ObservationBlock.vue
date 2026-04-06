<template>
  <div class="observation-block">
    <div class="obs-header">
      <span class="obs-icon">&#x1F4CB;</span>
      <span class="obs-label">Observation</span>
    </div>
    <pre class="obs-content">{{ content }}</pre>
  </div>
</template>

<script setup lang="ts">
defineProps<{ content: string }>()
</script>

<style scoped>
.observation-block {
  background: #f0fdf4;
  border-left: 3px solid #22c55e;
  border-radius: 6px;
  padding: 10px 14px;
  margin: 6px 0;
}

.obs-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #166534;
}

.obs-icon {
  font-size: 15px;
}

.obs-content {
  margin-top: 8px;
  font-size: 12px;
  color: #15803d;
  background: #dcfce7;
  border-radius: 4px;
  padding: 8px;
  overflow-x: auto;
  line-height: 1.4;
  white-space: pre-wrap;
}
</style>
