import type { ChatMessage } from '../types/message'

export function messageNotice(message: ChatMessage): string {
  if (message.role !== 'assistant') return ''
  if (message.status === 0) return message.errorMsg ? `回答失败：${message.errorMsg}` : '回答失败，请稍后重试。'
  if (message.status === 3) return '回答已停止。'
  if (message.status === 2) return '回答仍在生成，请稍后刷新。'
  if (message.finishReason === 'length') return '回答达到长度限制，可能未完整生成。'
  return ''
}

// Completed history is authoritative even when the server normalizes the final text.
// A running placeholder must not displace the locally received partial answer.
export function persistedReply(messages: ChatMessage[], initialCount: number, content: string, success: boolean): ChatMessage | undefined {
  return messages.slice(initialCount).find(message => message.role === 'assistant' && message.status !== 2 &&
    (success && message.status === 1 || !!content && message.content === content))
}
