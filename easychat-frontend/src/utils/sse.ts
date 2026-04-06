export interface SSECallbacks {
  onMessage?: (token: string) => void
  onThought?: (data: { content: string }) => void
  onAction?: (data: { tool: string; input: Record<string, unknown> }) => void
  onObservation?: (data: { content: string }) => void
  onError?: (msg: string) => void
  onFinish?: () => void
}

export function handleStreamChat(response: Response, callbacks: SSECallbacks): void {
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'message'

  function read(): void {
    reader.read().then(({ done, value }) => {
      if (done) {
        if (buffer.trim()) {
          processLine(buffer.trimEnd())
        }
        return
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()!

      for (const line of lines) {
        processLine(line.trimEnd())
      }

      read()
    }).catch(error => {
      callbacks.onError?.(error.message)
    })
  }

  function processLine(line: string): void {
    if (line.startsWith('event: ')) {
      currentEvent = line.substring(7).trim()
    } else if (line.startsWith('data: ')) {
      const data = line.substring(6)
      dispatchEvent(currentEvent, data)
    } else if (line === '') {
      currentEvent = 'message'
    }
  }

  function dispatchEvent(event: string, data: string): void {
    switch (event) {
      case 'message':
        callbacks.onMessage?.(data)
        break
      case 'thought':
        try {
          callbacks.onThought?.(JSON.parse(data))
        } catch {
          callbacks.onThought?.({ content: data })
        }
        break
      case 'action':
        try {
          callbacks.onAction?.(JSON.parse(data))
        } catch {
          callbacks.onAction?.({ tool: 'unknown', input: { raw: data } })
        }
        break
      case 'observation':
        try {
          callbacks.onObservation?.(JSON.parse(data))
        } catch {
          callbacks.onObservation?.({ content: data })
        }
        break
      case 'error':
        callbacks.onError?.(data)
        break
      case 'finish':
        callbacks.onFinish?.()
        break
    }
  }

  read()
}
