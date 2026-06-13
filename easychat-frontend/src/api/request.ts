const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL?.trim() || '').replace(/\/+$/, '')

function buildUrl(path: string): string {
  if (/^https?:\/\//.test(path)) {
    return path
  }

  if (!API_BASE_URL) {
    return path
  }

  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(buildUrl(path), init)

  if (!response.ok) {
    const body = await response.json().catch(() => null) as
      | { error?: string; message?: string }
      | null
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`)
  }

  return response.json() as Promise<T>
}

export async function requestVoid(path: string, init?: RequestInit): Promise<void> {
  const response = await fetch(buildUrl(path), init)

  if (!response.ok) {
    const body = await response.json().catch(() => null) as
      | { error?: string; message?: string }
      | null
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`)
  }
}

export async function requestStream(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(buildUrl(path), init)

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null) as
      | { error?: string; message?: string }
      | null
    throw new Error(body?.error || body?.message || `HTTP ${response.status}`)
  }

  return response
}
