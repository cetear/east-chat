<template>
  <div class="model-selector">
    <el-select
      :model-value="modelValue"
      :disabled="disabled"
      :loading="loading"
      placeholder="选择模型"
      class="model-select"
      @visible-change="(visible: boolean) => emit('visibleChange', visible)"
      @update:model-value="(value: string) => emit('update:modelValue', value)"
    >
      <el-option
        v-for="model in options"
        :key="model.code"
        :label="model.name"
        :value="model.code"
      />
    </el-select>
  </div>
</template>

<script setup lang="ts">
import type { ModelOption } from '@/types/model'

defineProps<{
  modelValue: string
  options: ModelOption[]
  disabled?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  visibleChange: [visible: boolean]
}>()
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
