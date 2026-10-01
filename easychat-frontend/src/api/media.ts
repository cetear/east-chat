import { requestJson } from './request'
import { validateFile } from '@/utils/validation'
export async function transcribe(file: File, signal?: AbortSignal): Promise<string> {
  validateFile(file)
  if (!/\.(wav|mp3|m4a|ogg|flac|mp4|webm|mov|mkv)$/i.test(file.name)) throw new Error('不支持的音视频格式')
  const form = new FormData()
  form.append('file', file)
  const response = await requestJson<{ text: string }>('/api/media/transcribe', { method: 'POST', body: form, signal })
  if (!response.text?.trim()) throw new Error('转写未返回文字')
  return response.text
}
