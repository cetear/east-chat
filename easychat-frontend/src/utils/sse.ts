export interface SSECallbacks {
  onSession?: (sessionId: string) => void
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
  let currentData: string[] = []

  function read(): void {
    reader.read().then(({ done, value }) => {
      if (done) {
        if (buffer) {
          processChunk(buffer)
        }
        flushEvent()
        return
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() ?? ''

      for (const line of lines) {
        processLine(line)
      }

      read()
    }).catch(error => {
      callbacks.onError?.(error.message)
    })
  }

  function processChunk(chunk: string): void {
    const lines = chunk.split(/\r?\n/)
    for (const line of lines) {
      processLine(line)
    }
  }

  function processLine(line: string): void {
    const normalizedLine = line.trimEnd()
    if (normalizedLine === '') {
      flushEvent()
      return
    }

    if (normalizedLine.startsWith(':')) {
      return
    }

    const separatorIndex = normalizedLine.indexOf(':')
    const field = separatorIndex >= 0 ? normalizedLine.slice(0, separatorIndex) : normalizedLine
    let value = separatorIndex >= 0 ? normalizedLine.slice(separatorIndex + 1) : ''
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }

    if (field === 'event') {
      currentEvent = value || 'message'
      return
    }

    if (field === 'data') {
      currentData.push(value)
    }
  }

  function flushEvent(): void {
    if (!currentData.length) {
      currentEvent = 'message'
      return
    }

    dispatchEvent(currentEvent, currentData.join('\n'))
    currentEvent = 'message'
    currentData = []
  }

  function dispatchEvent(event: string, data: string): void {
    switch (event) {
      case 'session':
        callbacks.onSession?.(data.trim())
        break
      case 'message':
        if (data.startsWith('__session_id__:')) {
          callbacks.onSession?.(data.replace('__session_id__:', '').trim())
          return
        }
        try {
          const parsed = JSON.parse(data) as { sessionId?: string; content?: string }
          if (parsed.sessionId) {
            callbacks.onSession?.(parsed.sessionId)
            if (parsed.content) {
              callbacks.onMessage?.(parsed.content)
            }
            return
          }
        } catch {
          // ignore JSON parse errors for plain text chunks
        }
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
