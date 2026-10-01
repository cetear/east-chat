import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getModelList } from '@/api/model'
import type { ModelOption } from '@/types/model'
import { identity } from '@/auth'

export const useConfigStore = defineStore('config', () => {
  const currentModel = ref('')
  const modelOptions = ref<ModelOption[]>([])
  const modelOptionsLoading = ref(false)
  const modelOptionsLoaded = ref(false)
  const modelOptionsError = ref('')
  const isStreamingMode = ref(true)
  const toolsEnabled = ref(false)
  const ragEnabled = ref(false)
  const dataset = ref('default')
  const knowledgeEnabled = import.meta.env.VITE_KNOWLEDGE_ENABLED === 'true'
  const mediaEnabled = import.meta.env.VITE_MEDIA_ENABLED === 'true'

  function setModel(model: string) {
    currentModel.value = model
  }

  async function loadModelOptions(): Promise<ModelOption[]> {
    const requestedIdentity = identity.value
    modelOptionsLoading.value = true
    modelOptionsError.value = ''
    try {
      const models = await getModelList()
      if (identity.value !== requestedIdentity) return []
      modelOptions.value = models
      modelOptionsLoaded.value = true

      const firstModel = models[0]
      if (!models.some(item => item.code === currentModel.value)) {
        currentModel.value = firstModel?.code ?? ''
      }

      return models
    } catch (error) {
      if (identity.value === requestedIdentity) {
        modelOptionsLoaded.value = false
        modelOptionsError.value = (error as Error).message || '模型目录加载失败'
        modelOptions.value = []
        currentModel.value = ''
      }
      throw error
    } finally {
      if (identity.value === requestedIdentity) modelOptionsLoading.value = false
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
    modelOptionsLoaded,
    modelOptionsError,
    isStreamingMode,
    toolsEnabled,
    ragEnabled,
    dataset,
    knowledgeEnabled,
    mediaEnabled,
    loadModelOptions,
    getModelLabel,
    setModel,
  }
})
