<template>
  <div class="tool-card">
    <div class="tool-header">
      <span class="tool-icon">&#x2699;&#xFE0F;</span>
      <span class="tool-label">Tool: {{ toolName }}</span>
    </div>
    <div class="tool-input" v-if="toolInput && Object.keys(toolInput).length">
      <div class="tool-section-label">Input:</div>
      <pre class="tool-code">{{ JSON.stringify(toolInput, null, 2) }}</pre>
    </div>
    <div class="tool-output" v-if="toolOutput">
      <div class="tool-section-label">Output:</div>
      <pre class="tool-code">{{ toolOutput }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  toolName: string
  toolInput?: Record<string, unknown>
  toolOutput?: string
}>()
</script>

<style scoped>
.tool-card {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
  padding: 10px 14px;
  margin: 6px 0;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #0369a1;
}

.tool-icon {
  font-size: 15px;
}

.tool-section-label {
  font-size: 12px;
  color: #64748b;
  margin-top: 8px;
  margin-bottom: 4px;
}

.tool-code {
  background: #f1f5f9;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  overflow-x: auto;
  margin: 0;
  color: #334155;
  line-height: 1.4;
}
</style>
