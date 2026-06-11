import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getModelList } from '@/api/model'
import type { ModelOption } from '@/types/model'

export const useConfigStore = defineStore('config', () => {
  const currentModel = ref('')
  const modelOptions = ref<ModelOption[]>([])
  const modelOptionsLoading = ref(false)
  const isStreamingMode = ref(true)
  const toolsEnabled = ref(false)
  const ragEnabled = ref(false)

  function setModel(model: string) {
    currentModel.value = model
  }

  async function loadModelOptions(): Promise<ModelOption[]> {
    modelOptionsLoading.value = true
    try {
      const models = await getModelList()
      modelOptions.value = models

      const firstModel = models[0]
      if (!currentModel.value && firstModel) {
        currentModel.value = firstModel.code
      }

      return models
    } finally {
      modelOptionsLoading.value = false
    }
  }

  function getModelLabel(modelCode?: string | null): string {
    if (!modelCode) return ''
    return modelOptions.value.find(item => item.code === modelCode)?.name || modelCode
  }

  return {
    currentModel,
    modelOptions,
    modelOptionsLoading,
    isStreamingMode,
    toolsEnabled,
    ragEnabled,
    loadModelOptions,
    getModelLabel,
    setModel,
  }
})
