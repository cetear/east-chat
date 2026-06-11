export interface ModelOption {
  code: string
  name: string
}

export interface ModelApiItem extends Record<string, unknown> {
  modelCode?: string
  modelName?: string
  modelType?: string
  code?: string
  name?: string
  displayName?: string
}

export interface ModelListPayload {
  data?: ModelApiItem[]
  list?: ModelApiItem[]
  rows?: ModelApiItem[]
  items?: ModelApiItem[]
  records?: ModelApiItem[]
}
