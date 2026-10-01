import { requestResult } from './request'
import { normalizeDataset, validateFile } from '@/utils/validation'
export interface KnowledgeDoc {
  docCode: string; fileName: string; fileType: string; dataset: string; chunkCount: number
  status: 'PENDING' | 'INDEXING' | 'INDEXED' | 'FAILED' | 'DELETING' | 'DELETE_FAILED'
  errorMsg: string | null; createdAt: string
}
const path = (code: string) => `/api/knowledge/docs/${encodeURIComponent(code)}`
export function getDocuments(dataset?: string, signal?: AbortSignal): Promise<KnowledgeDoc[]> {
  const query = dataset?.trim() ? `?dataset=${encodeURIComponent(normalizeDataset(dataset))}` : ''
  return requestResult(`/api/knowledge/docs${query}`, { signal })
}
export function getDocumentStatus(code: string, signal?: AbortSignal): Promise<KnowledgeDoc> {
  return requestResult(`${path(code)}/status`, { signal })
}
export function uploadDocument(file: File, dataset: string, signal?: AbortSignal): Promise<string> {
  validateFile(file)
  const form = new FormData()
  form.append('file', file)
  form.append('dataset', normalizeDataset(dataset))
  return requestResult('/api/knowledge/upload', { method: 'POST', body: form, signal })
}
export function retryDocument(code: string, signal?: AbortSignal): Promise<null> {
  return requestResult(`${path(code)}/retry`, { method: 'POST', signal })
}
export function deleteDocument(code: string, signal?: AbortSignal): Promise<null> {
  return requestResult(path(code), { method: 'DELETE', signal })
}
