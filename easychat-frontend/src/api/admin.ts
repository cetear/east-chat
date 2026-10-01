import { identity, isAdmin } from '@/auth'
import { jsonBody, request, requestJson, requestResult } from './request'
export type AdminKind = 'model' | 'provider' | 'route'
export type AdminRecord = Record<string, string | number | null>
function authorize(): void { if (!isAdmin()) throw new Error('需要管理员权限') }
async function guarded<T>(operation: () => Promise<T>): Promise<T> {
  authorize()
  const requestedIdentity = identity.value
  const result = await operation()
  if (identity.value !== requestedIdentity) throw new Error('登录身份已变更，请重新加载')
  return result
}
const code = encodeURIComponent
export function listModels(): Promise<AdminRecord[]> { authorize(); return guarded(() => requestResult('/model/list')) }
export function listProviders(): Promise<AdminRecord[]> { authorize(); return guarded(() => requestResult('/model/provider/list')) }
export function listRoutes(provider: string): Promise<AdminRecord[]> { authorize(); return guarded(() => requestResult(`/model/provider/${code(provider)}/models`)) }
function itemPath(kind: AdminKind, item: AdminRecord): string {
  if (kind === 'provider') return `/model/provider/${code(String(item.providerCode))}`
  if (kind === 'model') return `/model/${code(String(item.modelCode))}`
  return `/model/provider/${code(String(item.providerCode))}/models/${code(String(item.modelCode))}`
}
export function saveRecord(kind: AdminKind, item: AdminRecord, editing: boolean): Promise<null> {
  authorize()
  const path = editing ? itemPath(kind, item) : { model: '/model/create', provider: '/model/provider/create', route: '/model/addModelToProvider' }[kind]
  return guarded(() => requestResult(path, jsonBody(editing ? 'PUT' : 'POST', item)))
}
export function removeRecord(kind: AdminKind, item: AdminRecord): Promise<null> {
  authorize(); return guarded(() => requestResult(itemPath(kind, item), { method: 'DELETE' }))
}
export function getOperations(): Promise<Record<string, unknown>> { authorize(); return guarded(() => requestJson('/api/ops/status')) }
export async function testEs(): Promise<string> { authorize(); return guarded(async () => (await request('/es/test')).text()) }
export function getEsDocuments(index: string, size: number): Promise<Record<string, unknown>[]> {
  authorize()
  return guarded(() => requestResult(`/es/getDocuments?index=${code(index)}&size=${size}`))
}
export function indexEsDocument(index: string, id: string, document: Record<string, unknown>): Promise<string> {
  authorize(); return guarded(() => requestResult('/es/indexDocument', jsonBody('POST', { index, ...(id ? { id } : {}), document })))
}
