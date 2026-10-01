import { requestResult } from './request'
import type { ModelEntry, ModelOption } from '@/types/model'
export async function getModelList(): Promise<ModelOption[]> {
  const models = await requestResult<ModelEntry[]>('/api/models')
  return models.map(item => ({ code: item.modelCode, name: item.modelName, supportVision: item.supportVision === 1 }))
}
