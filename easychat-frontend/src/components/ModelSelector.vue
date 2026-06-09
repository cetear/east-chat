<template>
  <div class="model-selector">
    <el-select
      :model-value="modelValue"
      :disabled="disabled"
      placeholder="选择模型"
      class="model-select"
      @update:model-value="value => emit('update:modelValue', value)"
    >
      <el-option
        v-for="model in modelOptions"
        :key="model.code"
        :label="model.name"
        :value="model.code"
      />
    </el-select>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

type ModelOption = {
  code: string
  name: string
}

const modelOptions: ModelOption[] = [
  { code: 'deepseek-chat', name: 'DeepSeek Chat' },
  { code: 'openai', name: 'OpenAI' },
  { code: 'claude', name: 'Claude' },
  { code: 'gemini', name: 'Gemini' },
]
</script>

<style scoped>
.model-selector {
  display: flex;
  align-items: center;
}

.model-select {
  width: 180px;
}
</style>
