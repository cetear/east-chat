import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useConfigStore = defineStore('config', () => {
  const currentModel = ref('openai')
  const isStreamingMode = ref(true)
  const toolsEnabled = ref(false)
  const ragEnabled = ref(false)
  const modelConfigVisible = ref(false)

  function setModel(model: string) {
    currentModel.value = model
  }

  return {
    currentModel, isStreamingMode, toolsEnabled, ragEnabled,
    modelConfigVisible, setModel,
  }
})
