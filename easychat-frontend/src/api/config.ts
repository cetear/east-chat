const BASE = '/api'

const jsonHeaders = { 'Content-Type': 'application/json' }

export function getModels(): Promise<Record<string, unknown>[]> {
  return fetch(`${BASE}/config/models`).then(r => r.json())
}

export function getCurrentModel(): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/config/current`).then(r => r.json())
}

export function setCurrentModel(model: string): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/config/current`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ model }),
  }).then(r => r.json())
}

export function getModelConfig(modelCode: string): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/config/model/${modelCode}`).then(r => r.json())
}

export function saveModelConfig(config: Record<string, unknown>): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/config/model`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(config),
  }).then(r => r.json())
}

export function updateModelConfig(modelCode: string, config: Record<string, unknown>): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/config/model/${modelCode}`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify(config),
  }).then(r => r.json())
}

export function deleteModelConfig(modelCode: string): Promise<Record<string, unknown>> {
  return fetch(`${BASE}/config/model/${modelCode}`, {
    method: 'DELETE',
  }).then(r => r.json())
}
