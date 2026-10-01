import type { ChatMeta, Source } from '../types/message'
export interface SSECallbacks {
  onMeta?: (data: ChatMeta) => void
  onMessage?: (text: string) => void
  onThought?: (data: { content: string }) => void
  onAction?: (data: { tool: string; input: Record<string, unknown> }) => void
  onObservation?: (data: { content: string }) => void
  onSources?: (data: Source[]) => void
  onWarning?: (text: string) => void
  onError?: (text: string) => void
  onFinish?: (reason: string) => void
}
export async function handleStreamChat(response: Response, callbacks: SSECallbacks): Promise<'finish' | 'error'> {
  if (!response.body) throw new Error('响应没有数据流')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal: 'finish' | 'error' | undefined
  function dispatch(block: string): void {
    let event = 'message'
    const data: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith(':')) continue
      const colon = line.indexOf(':')
      const field = colon < 0 ? line : line.slice(0, colon)
      let value = colon < 0 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') event = value
      if (field === 'data') data.push(value)
    }
    if (!data.length) return
    const raw = data.join('\n')
    const parsed = ['meta', 'action', 'observation', 'sources', 'thought'].includes(event) ? JSON.parse(raw) : undefined
    switch (event) {
      case 'meta': callbacks.onMeta?.(parsed); break
      case 'message': callbacks.onMessage?.(raw); break
      case 'thought': callbacks.onThought?.(parsed); break
      case 'action': callbacks.onAction?.(parsed); break
      case 'observation': callbacks.onObservation?.(parsed); break
      case 'sources': callbacks.onSources?.(parsed); break
      case 'warning': callbacks.onWarning?.(raw); break
      case 'error': terminal = 'error'; callbacks.onError?.(raw); break
      case 'finish': terminal = 'finish'; callbacks.onFinish?.(raw); break
    }
  }
  function drain(): void {
    let boundary: RegExpExecArray | null
    while (!terminal && (boundary = /\r?\n\r?\n/.exec(buffer))) {
      const block = buffer.slice(0, boundary.index)
      buffer = buffer.slice(boundary.index + boundary[0].length)
      dispatch(block)
    }
  }
  try {
    while (!terminal) {
      const { done, value } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      drain()
      if (done) break
    }
    if (!terminal) throw new Error('连接在终态事件之前断开，请刷新历史确认结果')
    return terminal
  } finally {
    try { await reader.cancel() } catch { /* disconnected */ }
    reader.releaseLock()
  }
}
