import { requestJson } from './request'
import type { ModelApiItem, ModelListPayload, ModelOption } from '@/types/model'

function pickFirstString(source: ModelApiItem, keys: string[]): string {
  for (const key of keys) {
    const value = source[key]
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  return ''
}

function normalizeModel(item: ModelApiItem): ModelOption | null {
  const code = pickFirstString(item, ['modelCode', 'code', 'modelType', 'name'])
  if (!code) {
    return null
  }

  const name = pickFirstString(item, ['modelName', 'displayName', 'name', 'modelCode', 'code'])
  return {
    code,
    name: name || code,
  }
}

function extractItems(payload: ModelApiItem[] | ModelListPayload): ModelApiItem[] {
  if (Array.isArray(payload)) {
    return payload
  }

  if (Array.isArray(payload.data)) return payload.data
  if (Array.isArray(payload.list)) return payload.list
  if (Array.isArray(payload.rows)) return payload.rows
  if (Array.isArray(payload.items)) return payload.items
  if (Array.isArray(payload.records)) return payload.records

  return []
}

export async function getModelList(): Promise<ModelOption[]> {
  const payload = await requestJson<ModelApiItem[] | ModelListPayload>('/model/list')
  const items = extractItems(payload)
  const seen = new Set<string>()

  return items.reduce<ModelOption[]>((result, item) => {
    const normalized = normalizeModel(item)
    if (!normalized || seen.has(normalized.code)) {
      return result
    }

    seen.add(normalized.code)
    result.push(normalized)
    return result
  }, [])
}
