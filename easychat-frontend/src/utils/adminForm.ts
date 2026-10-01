import type { AdminKind, AdminRecord } from '@/api/admin'
export interface Field { key: string; label: string; kind?: 'number' | 'flag' | 'secret' | 'json'; min?: number; max?: number; integer?: boolean }
export const fields: Record<AdminKind, Field[]> = {
  provider: [
    { key: 'providerCode', label: '渠道代码' }, { key: 'baseUrl', label: '服务地址' },
    { key: 'apiKey', label: 'API Key（编辑留空保留）', kind: 'secret' }, { key: 'enabled', label: '启用', kind: 'flag' },
  ],
  model: [
    { key: 'modelCode', label: '模型代码' }, { key: 'modelName', label: '模型名称' },
    { key: 'modelType', label: '模型类型' }, { key: 'modelFamily', label: '模型系列' },
    { key: 'contextWindow', label: '上下文窗口', kind: 'number', min: 1, integer: true },
    { key: 'maxOutputTokens', label: '最大输出 Token', kind: 'number', min: 1, integer: true },
    { key: 'defaultTemperature', label: 'Temperature', kind: 'number', min: 0 },
    { key: 'defaultTopP', label: 'Top P', kind: 'number', min: 0, max: 1 },
    { key: 'defaultConfig', label: '扩展配置 JSON（清空用 {}）', kind: 'json' },
    { key: 'supportVision', label: '支持图片', kind: 'flag' }, { key: 'enabled', label: '启用', kind: 'flag' },
  ],
  route: [
    { key: 'providerCode', label: '渠道代码' }, { key: 'modelCode', label: '模型代码' },
    { key: 'priority', label: '优先级（越小越优先）', kind: 'number', integer: true },
    { key: 'weight', label: '权重', kind: 'number', min: 1, integer: true },
    { key: 'timeoutMs', label: '超时毫秒', kind: 'number', min: 1, integer: true },
    { key: 'maxRetry', label: '额外重试次数', kind: 'number', min: 0, max: 3, integer: true },
    { key: 'enabled', label: '启用', kind: 'flag' },
  ],
}
export function recordPayload(kind: AdminKind, draft: Record<string, string>): AdminRecord {
  const result: AdminRecord = {}
  for (const field of fields[kind]) {
    const raw = draft[field.key] ?? ''
    const value = field.kind === 'secret' ? raw : raw.trim()
    if (!value.trim()) continue
    if (field.kind === 'number' || field.kind === 'flag') {
      const n = Number(value)
      if (!Number.isFinite(n) || ((field.integer || field.kind === 'flag') && !Number.isSafeInteger(n)) ||
        (field.min != null && n < field.min) || (field.max != null && n > field.max) ||
        (field.kind === 'flag' && n !== 0 && n !== 1)) throw new Error(`${field.label}数值不合法`)
      result[field.key] = n
    } else if (field.kind === 'json') {
      let parsed: unknown
      try { parsed = JSON.parse(value) } catch { throw new Error('扩展配置必须为有效 JSON 对象文本') }
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('扩展配置必须为 JSON 对象')
      result[field.key] = value
    } else result[field.key] = value
  }
  const required = kind === 'model' ? ['modelCode', 'modelName'] : kind === 'provider' ? ['providerCode', 'baseUrl'] : ['providerCode', 'modelCode']
  for (const key of required) if (!result[key]) throw new Error(`请填写 ${fields[kind].find(f => f.key === key)?.label}`)
  if (kind === 'provider') {
    try { if (!['http:', 'https:'].includes(new URL(String(result.baseUrl)).protocol)) throw new Error() }
    catch { throw new Error('服务地址必须为有效的 http(s) URL') }
  }
  return result
}
